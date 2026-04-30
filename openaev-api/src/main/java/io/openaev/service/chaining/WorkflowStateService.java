package io.openaev.service.chaining;

import static io.openaev.utils.JsonUtils.gson;

import com.google.gson.*;
import io.openaev.database.model.*;
import io.openaev.database.repository.WorkflowStateRepository;
import io.openaev.utils.ConditionUtils;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class WorkflowStateService {

  private final ConditionUtils conditionUtils;

  private final WorkflowStateRepository workflowStateRepository;

  /**
   * Syncs structured output data into the global workflow state entries.
   *
   * @param dataToSync JSON element containing output data to merge
   * @param fieldTypeMap mapping from field name to its contract output type
   * @param workflowRun the running workflow whose global state is updated
   */
  public void syncState(
      JsonElement dataToSync, Map<String, ContractOutputType> fieldTypeMap, Workflow workflowRun) {
    WorkflowState globalState = loadOrBuildGlobalState(workflowRun);

    WorkflowStateEntries entries =
        gson.fromJson(globalState.getEntries(), WorkflowStateEntries.class);

    // Process traces
    if (dataToSync.isJsonObject()) {
      saveToEntries(entries, dataToSync.getAsJsonObject(), fieldTypeMap);
    }

    // Save JSON back to DB
    globalState.setEntries(gson.toJson(entries));
    save(globalState);
  }

  /**
   * Parses structured output fields and adds their values to the state entries.
   *
   * @param entries state entries to populate
   * @param structuredOutput JSON object with field arrays
   * @param fieldTypeMap mapping from field name to contract output type
   * @return map of field names to extracted values (for diagnostics)
   */
  private Map<String, List<String>> saveToEntries(
      WorkflowStateEntries entries,
      JsonObject structuredOutput,
      Map<String, ContractOutputType> fieldTypeMap) {

    Map<String, List<String>> currentTraceParsed = new HashMap<>();

    structuredOutput
        .entrySet()
        .forEach(
            entry -> {
              String nodeName = entry.getKey();
              JsonElement jsonValue = entry.getValue();

              ContractOutputType type = fieldTypeMap.get(nodeName);
              if (type == null || jsonValue.isJsonNull() || !jsonValue.isJsonArray()) {
                return;
              }

              JsonArray array = jsonValue.getAsJsonArray();

              for (JsonElement element : array) {
                if (element.isJsonPrimitive()) {
                  String val = element.getAsString();
                  entries.getInputByKey(type.name()).getValues().add(val);
                } else if (element.isJsonObject()) {
                  // e.g. "portscan": [{"port": 22, "host": "1.1.1.1"}]
                  saveCorrelatedObject(entries, element.getAsJsonObject());
                }
              }
            });
    return currentTraceParsed;
  }

  /**
   * Saves a correlated object (multi-field entry like host+port) into state entries.
   *
   * @param entries state entries to update
   * @param obj JSON object whose fields form a correlated pair set
   */
  private void saveCorrelatedObject(WorkflowStateEntries entries, JsonObject obj) {
    Set<WorkflowStateEntries.Pair> pairSet = new HashSet<>();

    obj.entrySet()
        .forEach(
            entry -> {
              String key = entry.getKey();
              JsonElement value = entry.getValue();

              if (value == null || value.isJsonNull()) {
                return;
              }

              // Extract clean string values for the Pair
              String valStr = value.isJsonPrimitive() ? value.getAsString() : value.toString();
              pairSet.add(new WorkflowStateEntries.Pair(key, valStr));
            });

    if (pairSet.size() > 1) {
      entries.getCorrelated().add(new WorkflowStateEntries.Correlated(pairSet));
    }
  }

  /** Persists a workflow state entity. */
  public WorkflowState save(WorkflowState state) {
    return workflowStateRepository.save(state);
  }

  /**
   * Returns or creates the global state for a workflow execution.
   *
   * @param workflow the running workflow
   * @return existing global state, or a new (unsaved) one with empty entries
   */
  private WorkflowState loadOrBuildGlobalState(Workflow workflow) {
    WorkflowState state = getGlobalStateByWorkflowId(workflow.getId());
    if (state == null) {
      state =
          WorkflowState.builder()
              .workflowExecution(workflow)
              .entries(gson.toJson(createInitialEntries()))
              .build();
    }
    return state;
  }

  private static WorkflowStateEntries createInitialEntries() {
    return new WorkflowStateEntries(
        new ArrayList<>(), new ArrayList<>(), new HashSet<>(), new HashSet<>());
  }

  /**
   * Returns the global {@link WorkflowState} for a workflow execution (null step-template).
   *
   * @param workflowId the workflow execution ID
   * @return the global state, or {@code null} if not yet created
   */
  public WorkflowState getGlobalStateByWorkflowId(String workflowId) {
    return workflowStateRepository.findByStepTemplateIsNullAndWorkflowExecutionId(workflowId);
  }

  /**
   * Returns the local {@link WorkflowState} for a step template within a workflow execution.
   *
   * @param targetTemplate the step template ID
   * @param workflowExecution the workflow execution ID
   * @return the local state, or {@code null} if not yet initialized
   */
  public WorkflowState loadOrBuildLocalState(Step targetTemplate, Workflow workflowExecution) {
    WorkflowState localState =
        workflowStateRepository.findByStepTemplate_IdAndWorkflowExecution_Id(
            targetTemplate.getId(), workflowExecution.getId());

    if (localState == null) {
      localState =
          initializeLocalState(
              targetTemplate, workflowExecution, gson.toJson(createInitialEntries()));
    }

    return localState;
  }

  /**
   * Creates a new local state entity for a step template (not yet persisted).
   *
   * @param target the step template
   * @param workflowExecution the workflow execution
   * @param entriesJson initial entries as JSON
   * @return a new WorkflowState bound to the step and workflow
   */
  private WorkflowState initializeLocalState(
      Step target, Workflow workflowExecution, String entriesJson) {
    return WorkflowState.builder()
        .stepTemplate(target)
        .workflowExecution(workflowExecution)
        .entries(entriesJson)
        .build();
  }

  /**
   * path - key are use during the config of mapped value (see Condition Mapper) path is different
   * depending on step parent but key is the same for the next step
   *
   * @param workflowStateEntries "state entries of current step"
   * @param output "new output"
   * @param path "outputs.message.stdout" or "outputs.message.port+outputs.message.ip"
   * @param key "stout" or "ip+port"
   */
  public void newOutput(
      WorkflowStateEntries workflowStateEntries, String output, String path, String key) {
    if (workflowStateEntries.isPathCorrelated(path)) {
      // Get Mapped condition liked to this step(child) and with idStepFrom(parent)
      // todo It give you the key <-> path
      Map<String, String> pathToKey = new HashMap<>();
      // todo remove
      pathToKey.put("outputs.message.ip", "ip");
      pathToKey.put("outputs.message.port", "port");
      List<String> paths = workflowStateEntries.pathCorrelated(path);

      Set<WorkflowStateEntries.Pair> values = new HashSet<>();
      for (String pathUnit : paths) {
        // TODO check if we can have other than Primitive
        String value = getValues(output, pathUnit).stream().findFirst().orElse("");
        values.add(new WorkflowStateEntries.Pair(pathToKey.get(pathUnit), value));
      }

      Map<Set<WorkflowStateEntries.Pair>, WorkflowStateEntries.Correlated> index =
          workflowStateEntries.getIndexCorrelatedInput();
      if (!index.containsKey(values)) {
        WorkflowStateEntries.Correlated newCorrelated = new WorkflowStateEntries.Correlated(values);
        workflowStateEntries.getCorrelated().add(newCorrelated);
        // todo test all combination  and launch the ones not executed
        // Todo save this StepInputBuffer
        workflowStateEntries.testAndSaveCombinationsForCorrelated(newCorrelated);
      }
    } else {
      Set<String> values = getValues(output, path);

      List<String> newValues = new ArrayList<>();

      WorkflowStateEntries.Input input = workflowStateEntries.getInputByKey(key);
      for (String value : values) {
        if (!input.getValues().contains(value)) {
          newValues.add(value);
          input.getValues().add(value);
          // todo test all combination and launch the ones not executed
          // Todo save this StepInputBuffer

          if (!newValues.isEmpty()) {
            workflowStateEntries.testAndSaveCombinationsForInput(input, newValues);
          }
        }
      }
    }
  }

  /**
   * Extracts values from an output string based on the given path.
   *
   * @param output the output string to extract values from
   * @param path the path specifying which fields to extract
   * @return a set of extracted string values
   */
  private Set<String> getValues(String output, String path) {
    Map<String, Object> fields = StepService.getFields(output, path);

    return fields.values().stream()
        .map(
            value -> {
              if (value instanceof JsonNull) {
                return null;
              } else if (value instanceof JsonPrimitive jsonPrimitive) {
                return jsonPrimitive.getAsString();
              } else {
                return value.toString();
              }
            })
        .collect(Collectors.toSet());
  }
}
