package io.openaev.service.chaining;

import static io.openaev.database.model.ScopeRuleValueType.getAllContractOutputTypes;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import io.openaev.database.model.*;
import io.openaev.database.repository.WorkflowStateRepository;
import io.openaev.rest.inject.service.StructuredOutputUtils;
import io.openaev.rest.injector_contract.InjectorContractContentUtils;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class WorkflowStateService {

  private final ConditionService conditionService;

  private final WorkflowStateRepository workflowStateRepository;

  private final InjectorContractContentUtils injectorContractContentUtils;
  private final StructuredOutputUtils structuredOutputUtils;

  private final Gson gson = new Gson();

  /**
   * Syncs a completed inject's structured output traces into the workflow's global state entries.
   * Exits early if the inject has no status, no traces, or no matching global state.
   *
   * @param inject the completed inject whose output traces are synchronized
   * @param stepRun the RUN step used to locate the workflow's global state
   */
  @Transactional
  public void syncInjectOutputToGlobalState(Inject inject, Step stepRun) {
    Optional<InjectStatus> injectStatus = inject.getStatus();

    if (injectStatus.isPresent() && injectStatus.get().getTraces() != null) {
      Map<String, ContractOutputType> fieldTypeMap = new HashMap<>();

      if (inject.getInjectorContract().isPresent()) {
        injectorContractContentUtils
            .getAllContractOutputs(inject.getInjectorContract().get())
            .forEach(out -> fieldTypeMap.put(out.getField(), out.getType()));
      } else {
        Set<OutputParser> outputParsers = structuredOutputUtils.extractOutputParsers(inject);
        injectorContractContentUtils
            .getAllContractOutputs(outputParsers)
            .forEach(out -> fieldTypeMap.put(out.getKey(), out.getType()));
      }

      // Load Global State
      WorkflowState globalState = getGlobalStateByWorkflowId(stepRun.getWorkflow().getId());

      if (globalState == null) {
        return; // Guard against missing state
      }

      // Deserialize entries
      WorkflowStateEntries entries =
          gson.fromJson(globalState.getEntries(), WorkflowStateEntries.class);

      // Process traces
      injectStatus
          .get()
          .getTraces()
          .forEach(
              trace -> {
                ObjectNode rawOutput = trace.getStructuredOutput();
                if (rawOutput == null || rawOutput.isEmpty()) {
                  return;
                }

                try {
                  JsonElement element = JsonParser.parseString(rawOutput.toString());
                  if (element.isJsonObject()) {
                    saveToEntries(entries, element.getAsJsonObject(), fieldTypeMap);
                  }
                } catch (Exception e) {
                  log.error("Failed to sync trace {} to global state", trace.getId(), e);
                }
              });

      // Save JSON back to DB
      globalState.setEntries(gson.toJson(entries));
      save(globalState);
    }
  }

  private void saveToEntries(
      WorkflowStateEntries entries,
      JsonObject structuredOutput,
      Map<String, ContractOutputType> fieldTypeMap) {

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
                  // e.g. "ports": [22, 80]
                  entries.getInputByKey(type.name()).getValues().add(element.getAsString());
                } else if (element.isJsonObject()) {
                  // e.g. "portscan": [{"port": 22, "host": "1.1.1.1"}]
                  saveCorrelatedObject(entries, element.getAsJsonObject());
                }
              }
            });
  }

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

  /**
   * Syncs LOCAL mapper values from a step output into the local workflow state partition. Filters
   * candidate values through {@code filterCondition} and persists only when something new is added.
   * Non-LOCAL mappers ({@code GLOBAL}, {@code DEFAULT}) are skipped.
   *
   * @param targetTemplate step template whose local state receives the new values
   * @param workflowExecution running workflow used to locate the state partition
   * @param currentOutput JSON payload produced by the RUN step
   * @param filterCondition root filter applied to candidate values before storing
   * @param mappers mapper conditions defined on the target step template
   * @return {@code true} if at least one new value was added, {@code false} otherwise
   */
  @Transactional
  public boolean syncMappersToLocalPartition(
      Step targetTemplate,
      Workflow workflowExecution,
      String currentOutput,
      Condition filterCondition,
      List<Condition> mappers) {

    // Fetch states and initialize LocalState if missing
    WorkflowState localState =
        getLocalStateByWorkflowIdAndStepId(targetTemplate.getId(), workflowExecution.getId());

    if (localState == null) {
      localState =
          initializeLocalState(
              targetTemplate, workflowExecution, gson.toJson(createInitialEntries()));
    }

    WorkflowStateEntries entries =
        gson.fromJson(localState.getEntries(), WorkflowStateEntries.class);
    boolean changed = false;

    // Process Mappers
    for (Condition mapper : mappers) {
      // We only save to Local Partition if Mapping is LOCAL
      if (mapper.getMappingType() != MappingType.LOCAL) {
        continue;
      }

      if (currentOutput == null || currentOutput.isBlank()) {
        continue;
      }

      // Extract values for this specific KeyType from the output
      Set<String> values = getInputValues(currentOutput, mapper.getKeyType().name());

      List<String> validValues =
          values.stream()
              .filter(v -> conditionService.isFilterConditionValid(v, filterCondition))
              .toList();

      // Use WorkflowStateEntries internal logic to save
      if (!validValues.isEmpty()) {
        WorkflowStateEntries.Input input = entries.getInputByKey(mapper.getKeyType().name());
        for (String v : validValues) {
          // Set.add returns true only if the element was not already present
          if (input.getValues().add(v)) {
            changed = true;
          }
        }
      }
    }

    // Persist if data was added
    if (changed) {
      localState.setEntries(gson.toJson(entries));
      save(localState);
    }

    return changed;
  }

  /**
   * Creates and persists the global {@link WorkflowState} for a new workflow run. Initializes a
   * scope bucket (from the workflow's whitelist rules) and an empty discovery bucket.
   *
   * @param workflowRun the running workflow
   * @return the persisted global {@link WorkflowState}
   */
  public WorkflowState createGlobalState(Workflow workflowRun) {
    WorkflowStateEntries scopeEntries = createInitialScopeEntries(workflowRun);
    WorkflowStateEntries discoveryEntries = createInitialEntries();

    WorkflowState globalState = initializeGlobalState(workflowRun, scopeEntries, discoveryEntries);

    save(globalState);
    log.debug("Initialized Global WorkflowState for workflow execution {}", workflowRun.getId());

    return globalState;
  }

  public void save(WorkflowState state) {
    workflowStateRepository.save(state);
  }

  private static WorkflowStateEntries createInitialScopeEntries(Workflow workflowRun) {
    // 1. Collect all possible keys from the Enum to initialize the buckets
    Set<String> scopeKeys = getAllContractOutputTypes();

    // Initialize the WorkflowStateEntries for the Scope column
    WorkflowStateEntries scopeEntries =
        new WorkflowStateEntries(
            new ArrayList<>(), // inputs
            new ArrayList<>(), // correlated
            new HashSet<>(), // hashExecution
            scopeKeys // keys (IP, DOMAIN, etc.)
            );

    // Process the whitelist rules
    if (workflowRun.getWhitelist() != null) {
      for (WorkflowScopeRule rule : workflowRun.getWhitelist()) {
        // Use the Enum name directly as the key (e.g., "IP", "ASSET_ID")
        String key = rule.getValueType().getContractOutputType();

        // getInputByKey ensures the Input object exists and adds the value
        scopeEntries.getInputByKey(key).getValues().add(rule.getRuleValue());
      }
    }
    return scopeEntries;
  }

  private WorkflowState initializeGlobalState(
      Workflow workflowRun,
      WorkflowStateEntries scopeEntries,
      WorkflowStateEntries discoveryEntries) {
    return WorkflowState.builder()
        .workflowExecution(workflowRun)
        .stepTemplate(null) // NULL indicates the Global State bucket
        .scope(gson.toJson(scopeEntries))
        .entries(gson.toJson(discoveryEntries))
        .build();
  }

  /**
   * Creates a new step state for a workflow execution.
   *
   * @param executionKeys the set of execution keys for the state
   * @param stepTemplate the step template this state is associated with
   * @param workflowExecution the workflow execution this state belongs to
   */
  public void createLocalState(
      Set<String> executionKeys, Step stepTemplate, Workflow workflowExecution) {
    if (executionKeys == null || executionKeys.isEmpty()) {
      throw new IllegalArgumentException("executionKeys is null");
    }
    WorkflowStateEntries workflowStateEntries =
        new WorkflowStateEntries(
            new ArrayList<>(), new ArrayList<>(), new HashSet<>(), executionKeys);
    String workflowStateEntriesAsJson =
        gson.toJson(workflowStateEntries, WorkflowStateEntries.class);
    WorkflowState workflowState =
        initializeLocalState(stepTemplate, workflowExecution, workflowStateEntriesAsJson);
    save(workflowState);
  }

  /**
   * Retrieves the state entries for a step template within a workflow execution.
   *
   * @param stepTemplateId the ID of the step template
   * @param workflowExecutionId the ID of the workflow execution
   * @return the step state entries
   */
  public WorkflowStateEntries getState(String stepTemplateId, String workflowExecutionId) {
    WorkflowState persistedWorkflowState =
        getLocalStateByWorkflowIdAndStepId(stepTemplateId, workflowExecutionId);
    return gson.fromJson(persistedWorkflowState.getEntries(), WorkflowStateEntries.class);
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

  private Set<String> getInputValues(String currentOutput, String keyToFind) {
    if (currentOutput == null || currentOutput.isBlank()) {
      return Collections.emptySet();
    }

    try {
      // Parse as a List of Input objects (using the inner class from WorkflowStateEntries)
      List<WorkflowStateEntries.Input> allInputs =
          gson.fromJson(
              currentOutput, new TypeToken<List<WorkflowStateEntries.Input>>() {}.getType());

      // Find the input that matches the key and return its values
      return allInputs.stream()
          .filter(input -> keyToFind.equals(input.getKey()))
          .findFirst()
          .map(WorkflowStateEntries.Input::getValues)
          .orElse(Collections.emptySet());

    } catch (Exception e) {
      return Collections.emptySet();
    }
  }

  private static WorkflowStateEntries createInitialEntries() {
    return new WorkflowStateEntries(
        new ArrayList<>(), new ArrayList<>(), new HashSet<>(), new HashSet<>());
  }

  private WorkflowState initializeLocalState(
      Step target, Workflow workflowExecution, String entriesJson) {
    return WorkflowState.builder()
        .stepTemplate(target)
        .workflowExecution(workflowExecution)
        .entries(entriesJson)
        .build();
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
   * @param targetId the step template ID
   * @param workflowExecutionId the workflow execution ID
   * @return the local state, or {@code null} if not yet initialized
   */
  public WorkflowState getLocalStateByWorkflowIdAndStepId(
      String targetId, String workflowExecutionId) {
    return workflowStateRepository.findByStepTemplate_IdAndWorkflowExecution_Id(
        targetId, workflowExecutionId);
  }
}
