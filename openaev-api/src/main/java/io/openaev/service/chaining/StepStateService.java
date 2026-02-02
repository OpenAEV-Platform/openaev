package io.openaev.service.chaining;

import com.google.gson.Gson;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import io.openaev.database.model.Step;
import io.openaev.database.model.StepState;
import io.openaev.database.model.StepStateEntries;
import io.openaev.database.model.Workflow;
import io.openaev.database.repository.StepStateRepository;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class StepStateService {

  private final StepStateRepository stepStateRepository;
  private final Gson gson = new Gson();

  public void createState(
      Set<String> executionKeys, Step stepTemplate, Workflow workflowExecution) {
    StepStateEntries stateEntries =
        new StepStateEntries(new ArrayList<>(), new ArrayList<>(), new HashSet<>(), executionKeys);
    String stateAsJson = gson.toJson(stateEntries, StepStateEntries.class);
    StepState stepState =
        StepState.builder()
            .stepTemplate(stepTemplate)
            .workflowExecution(workflowExecution)
            .entries(stateAsJson)
            .build();
    stepStateRepository.save(stepState);
  }

  public StepStateEntries getState(String stepTemplateId, String workflowExecutionId) {
    StepState stepState =
        stepStateRepository.findByStepTemplate_IdAndWorkflowExecution_Id(
            stepTemplateId, workflowExecutionId);
    return gson.fromJson(gson.toJson(stepState.getEntries()), StepStateEntries.class);
  }

  /**
   * path - key are use during the config of mapped value (see Condition Mapper) path is different
   * depending on step parent but key is the same for the next step
   *
   * @param stateEntries "state entries of current step"
   * @param output "new output"
   * @param path "outputs.message.stdout" or "outputs.message.port+outputs.message.ip"
   * @param key "stout" or "ip+port"
   */
  public void newOutput(StepStateEntries stateEntries, String output, String path, String key) {
    if (stateEntries.isPathCorrelated(path)) {
      // Get Mapped condition liked to this step(child) and with idStepFrom(parent)
      // todo It give you the key <-> path
      Map<String, String> pathKey = new HashMap<>();
      // todo remove
      pathKey.put("outputs.message.ip", "ip");
      pathKey.put("outputs.message.port", "port");
      List<String> paths = stateEntries.pathCorrelated(path);

      Set<StepStateEntries.Pair> values = new HashSet<>();
      for (String pathUnit : paths) {
        // TODO check if we can have other than Primitive
        String value = getValues(output, pathUnit).stream().findFirst().orElse("");
        values.add(new StepStateEntries.Pair(pathKey.get(pathUnit), value));
      }

      Map<Set<StepStateEntries.Pair>, StepStateEntries.Correlated> index =
          stateEntries.getIndexCorrelatedInput();
      if (!index.containsKey(values)) {
        StepStateEntries.Correlated newCorrelated = new StepStateEntries.Correlated(values);
        stateEntries.getCorrelated().add(newCorrelated);
        // todo test all combination  and launch the ones not executed
        // Todo save this StepInputBuffer
        stateEntries.testAndSaveCombinationsForCorrelated(newCorrelated);
      }
    } else {
      Set<String> values = getValues(output, path);

      List<String> newValues = new ArrayList<>();

      StepStateEntries.Input input = stateEntries.getInputByKey(key);
      for (String value : values) {
        if (!input.getValues().contains(value)) {
          newValues.add(value);
          input.getValues().add(value);
          // todo test all combination and launch the ones not executed
          // Todo save this StepInputBuffer

          if (!newValues.isEmpty()) {
            stateEntries.testAndSaveCombinationsForInput(input, newValues);
          }
        }
      }
    }
  }

  private Set<String> getValues(String output, String path) {
    Map<String, Object> fields = StepService.getFields(output, path);

    return fields.values().stream()
        .map(
            value -> {
              if (value instanceof JsonNull) {
                return null;
              } else if (value instanceof JsonPrimitive) {
                return ((JsonPrimitive) value).getAsString();
              } else {
                return value.toString();
              }
            })
        .collect(Collectors.toSet());
  }
}
