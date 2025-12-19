package io.openaev.service.chaining;

import com.google.gson.*;
import io.openaev.api.chaining.ActionStep;
import io.openaev.api.chaining.InjectExecutionStep;
import io.openaev.api.chaining.dto.ConditionCreateInput;
import io.openaev.api.chaining.dto.StepsCreateInput;
import io.openaev.database.model.*;
import io.openaev.database.repository.StepRepository;
import io.openaev.rest.exception.BadRequestException;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class StepService {
  private final WorkflowService workflowService;
  private final StepRepository stepRepository;
  private final InjectExecutionStep injectExecutionStep;

  private final ConditionService conditionService;

  public void createStepsTemplate(String workflowId, List<StepsCreateInput.StepCreateInput> steps) {
    Workflow workflow = workflowService.getWorkflowById(workflowId);
    for (StepsCreateInput.StepCreateInput stepInput : steps) {
      ActionStep actionStep = this.factoryAction(stepInput.getStepAction());
      if (actionStep == null) throw new BadRequestException("action step is null");
      Step step = actionStep.create(stepInput, workflow);
      step = this.saveStep(step);
      this.stepCondition(stepInput, step, workflow);

      this.saveStep(step);
    }
  }

  public void startWorkflow(String workflowId) {
    // Get all step template
    // Check edition content
    // If edited increase version workflow template
    // Create new workflow RUN save

    // Find step template with condition valid
    // For each step template IF condition valid create condition execution

    // Call wait(Step stepTemplate, String conditionExecutionId or Condition
    // conditionExecutionWithoutParent)

    // IF NONE STEP TEMPLATE WITH CONDITION VALID update WORKFLOW with status END
  }

  public void wait(Step stepTemplate) {
    ActionStep actionStep = this.factoryAction(stepTemplate.getStepAction());
    if (actionStep == null) throw new BadRequestException("action step is null");

    Step stepWait = actionStep.wait(stepTemplate, null);

    stepWait = this.saveStep(stepWait);

    stepTemplate.getStepsExecuted().add(stepWait); // TODO Check
    this.saveStep(stepTemplate);
  }

  public void run(Step stepWait) {
    ActionStep actionStep = this.factoryAction(stepWait.getStepAction());
    if (actionStep == null) throw new BadRequestException("action step is null");

    Step stepRun = actionStep.run(stepWait, null);
    if (stepRun == null) {
      stepWait.setStatus(STEP_STATUS.END);
      this.saveStep(stepWait);

    } else {
      stepRun.setStatus(STEP_STATUS.RUN);
      this.saveStep(stepRun);
    }
  }

  private ActionStep factoryAction(STEP_ACTION_CLASS actionClass) {
    return switch (actionClass) {
      case STEP_ACTION_CLASS.INJECT_EXECUTION -> injectExecutionStep;
      default -> null;
    };
  }

  public void saveStep(List<Step> steps) {
    this.stepRepository.saveAll(steps);
  }

  private Condition stepCondition(
      StepsCreateInput.StepCreateInput stepInput, Step step, Workflow workflow) {
    ConditionCreateInput firstCondition =
        stepInput.conditions.stream()
            .filter(
                conditionCreateInput ->
                    conditionCreateInput.getTemporaryIdConditionParent() == null)
            .reduce(
                (a, b) -> {
                  throw new IllegalArgumentException("Only 1 condition can be first parent");
                })
            .orElseThrow(
                () -> new IllegalArgumentException("Only 1 condition can be first parent"));

    Condition first =
        Condition.builder()
            .step(step)
            .type(firstCondition.getType())
            .key(firstCondition.getKey())
            .value(firstCondition.getValue())
            .build();
    first = conditionService.saveCondition(first);

    Map<String, Condition> temporaryIdAndSaveId = new HashMap<>();
    temporaryIdAndSaveId.put(firstCondition.getTemporaryId(), first);

    Map<String, List<ConditionCreateInput>> temporaryConditions = new HashMap<>();
    temporaryConditions =
        stepInput.getConditions().stream()
            .filter(
                conditionCreateInput ->
                    conditionCreateInput.getTemporaryIdConditionParent() != null)
            .collect(Collectors.groupingBy(ConditionCreateInput::getTemporaryIdConditionParent));

    Queue<String> currentId = new LinkedList<>();
    currentId.add(firstCondition.getTemporaryId());

    while (!currentId.isEmpty()) {
      String currentTemporaryId = currentId.poll();

      List<ConditionCreateInput> conditions =
          temporaryConditions.getOrDefault(currentTemporaryId, new ArrayList<>());

      for (ConditionCreateInput condition : conditions) {
        Condition current =
            Condition.builder()
                .type(condition.getType())
                .key(condition.getKey())
                .value(condition.getValue())
                .conditionParent(
                    temporaryIdAndSaveId.get(condition.getTemporaryIdConditionParent()))
                .step(step)
                .build();

        current = conditionService.saveCondition(current);

        temporaryIdAndSaveId.put(condition.getTemporaryId(), current);

        currentId.add(condition.getTemporaryId());
      }
    }
    return first;
  }

  public Step saveStep(Step step) {
    return this.stepRepository.save(step);
  }

  public Step findStepTemplateById(String idStep) {
    return this.stepRepository.findByStepTemplateIdIsNullAndIdAndStatus(
        idStep, STEP_STATUS.TEMPLATE);
  }

  public Step findStepWaitById(String idStep) {
    return this.stepRepository.findByStepTemplateIdIsNotNullAndIdAndStatus(
        idStep, STEP_STATUS.WAIT);
  }

  public Step findById(String stepId) {
    return stepRepository.findById(stepId).orElseThrow(); // todo exc
  }

  public static JsonPrimitive getField(String jsonString, String path) {
    Map<String, Object> fieldsAndValue = new HashMap<>();
    fieldsAndValue.put(path, null);
    useJson(jsonString, fieldsAndValue, ACTION_JSON.GET);
    return (JsonPrimitive) fieldsAndValue.get(path);
  }

  public static String setField(String jsonString, String path, Object newValue) {
    Map<String, Object> fieldsAndValue = new HashMap<>();
    fieldsAndValue.put(path, newValue);
    JsonObject jsonUpdated = useJson(jsonString, fieldsAndValue, ACTION_JSON.REPLACE);
    return jsonUpdated.toString();
  }

  /**
   * @param jsonString the root JSON object to use
   * @param fieldsAndValue a map where keys are dot-separated JSON paths and values are the new
   *     values to apply(ACTION_JSON.REPLACE) or will be value to get(ACTION_JSON.GET)
   */
  public static JsonObject useJson(
      String jsonString, Map<String, Object> fieldsAndValue, ACTION_JSON actionJson) {
    final Gson gson = new Gson();
    JsonObject jsonObject = gson.fromJson(jsonString, JsonObject.class);
    for (String field : fieldsAndValue.keySet()) {
      List<String> treeToUpdate = Arrays.asList(field.split("\\."));
      int indexFieldPath = 0;

      JsonElement o = jsonObject.get(treeToUpdate.get(indexFieldPath));
      if (o != null) {
        if (indexFieldPath == treeToUpdate.size() - 1) {
          actionJson(
              fieldsAndValue,
              field,
              treeToUpdate,
              jsonObject,
              null,
              null,
              indexFieldPath,
              actionJson,
              TYPE_JSON.DEFAULT);
        } else if (o.isJsonArray()) {
          iterateJsonArray(
              o.getAsJsonArray(), indexFieldPath, treeToUpdate, fieldsAndValue, field, actionJson);
        } else if (o.isJsonObject()) {
          iterateJsonObject(
              o.getAsJsonObject(), indexFieldPath, treeToUpdate, fieldsAndValue, field, actionJson);
        }
      }
    }
    return jsonObject;
  }

  private static void iterateJsonArray(
      JsonArray jsonArray,
      int index,
      List<String> treeToUpdate,
      Map<String, Object> fieldsAndValue,
      String field,
      ACTION_JSON actionJson) {

    Integer tabIndex = null;
    if (NumberUtils.isParsable(treeToUpdate.get(index + 1))) {
      tabIndex = Integer.parseInt(treeToUpdate.get(index + 1));
    }
    int indexArray = 0;
    for (JsonElement element : jsonArray) {
      if (tabIndex == null || tabIndex == indexArray) {
        if (tabIndex != null) index++;
        if (index == treeToUpdate.size() - 1 && tabIndex != null) {
          actionJson(
              fieldsAndValue,
              field,
              treeToUpdate,
              element,
              jsonArray,
              indexArray,
              index,
              actionJson,
              TYPE_JSON.ARRAY);
        } else if (element.isJsonObject()) {
          iterateJsonObject(
              element.getAsJsonObject(), index, treeToUpdate, fieldsAndValue, field, actionJson);
        } else if (element.isJsonArray()) {
          iterateJsonArray(
              element.getAsJsonArray(), index, treeToUpdate, fieldsAndValue, field, actionJson);
        }
      }
      indexArray++;
    }
  }

  private static void iterateJsonObject(
      JsonObject jsonObject,
      int index,
      List<String> treeToUpdate,
      Map<String, Object> fieldsAndValue,
      String field,
      ACTION_JSON actionJson) {
    index++;
    if (index == treeToUpdate.size() - 1) {
      actionJson(
          fieldsAndValue,
          field,
          treeToUpdate,
          jsonObject,
          null,
          null,
          index,
          actionJson,
          TYPE_JSON.OBJECT);
    } else if (jsonObject.get(treeToUpdate.get(index)).isJsonArray()) {
      iterateJsonArray(
          (JsonArray) jsonObject.get(treeToUpdate.get(index)),
          index,
          treeToUpdate,
          fieldsAndValue,
          field,
          actionJson);
    }
  }

  private static void actionJson(
      Map<String, Object> fieldsAndValue,
      String field,
      List<String> tree,
      JsonElement jsonElement,
      JsonArray jsonArray,
      Integer tabIndexJsonArray,
      int index,
      @NotNull ACTION_JSON actionJson,
      @NotNull TYPE_JSON typeJson) {
    switch (actionJson) {
      case REPLACE -> {
        JsonPrimitive newValue = toJsonPrimitive(fieldsAndValue.get(field));
        switch (typeJson) {
          case OBJECT -> {
            // OBJET
            JsonObject object = jsonElement.getAsJsonObject();
            if (object.get(tree.get(index)).isJsonArray()) {
              object.remove(tree.get(index));
              JsonArray newJsonArray = new JsonArray();
              newJsonArray.add(newValue);
              object.add(tree.get(index), newJsonArray);
            } else {
              object.remove(tree.get(index));
              object.add(tree.get(index), newValue);
            }
          }
          case ARRAY -> {
            // JSONARRAY
            if (jsonElement.isJsonPrimitive()) {
              jsonArray.set(tabIndexJsonArray, newValue);
            } else {
              jsonElement.getAsJsonObject().remove(tree.get(index));
            }
          }
          case DEFAULT -> {
            JsonObject jsonObject = jsonElement.getAsJsonObject();
            // DEFAULT
            jsonObject.remove(tree.get(index));
            jsonObject.add(tree.get(index), newValue);
          }
        }
      }
      case GET -> {
        switch (typeJson) {
          case OBJECT, DEFAULT -> {
            JsonObject object = jsonElement.getAsJsonObject();
            fieldsAndValue.put(field, object.get(tree.get(index)));
          }
          case ARRAY -> {
            if (jsonElement.isJsonPrimitive()) {
              fieldsAndValue.put(field, jsonArray.get(tabIndexJsonArray));
            } else {
              fieldsAndValue.put(field, jsonElement.getAsJsonObject());
            }
          }
        }
      }
    }
  }

  private static JsonPrimitive toJsonPrimitive(Object o) {
    if (o instanceof String) {
      return new JsonPrimitive((String) o);
    }
    if (o instanceof Boolean) {
      return new JsonPrimitive((Boolean) o);
    }
    if (o instanceof Number) {
      return new JsonPrimitive((Number) o);
    }
    return new JsonPrimitive(o.toString());
  }

  public enum ACTION_JSON {
    REPLACE,
    GET
  }

  public enum TYPE_JSON {
    OBJECT,
    ARRAY,
    DEFAULT
  }
}
