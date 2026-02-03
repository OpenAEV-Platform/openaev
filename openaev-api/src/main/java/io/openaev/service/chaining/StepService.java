package io.openaev.service.chaining;

import com.google.gson.*;
import io.openaev.api.chaining.ActionStep;
import io.openaev.api.chaining.InjectExecutionStep;
import io.openaev.api.chaining.dto.ConditionCreateInput;
import io.openaev.api.chaining.dto.StepsCreateInput;
import io.openaev.database.model.*;
import io.openaev.database.repository.StepRepository;
import io.openaev.rest.exception.BadRequestException;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class StepService implements StepEventHandler, ExternalUpdateEventHandler {

  private final ApplicationContext applicationContext;

  private final WorkflowService workflowService;
  private final StepRepository stepRepository;

  public final ConditionService conditionService;
  private final QueueChainingService queueChainingService;

  /**
   * Create step templates
   *
   * @param workflowId id of the workflow linked to the step templates
   * @param steps list of input to create step templates
   */
  public void createStepTemplates(String workflowId, List<StepsCreateInput.StepCreateInput> steps) {
    Workflow workflow = workflowService.getWorkflowById(workflowId);

    for (StepsCreateInput.StepCreateInput stepInput : steps) {
      if (stepInput.getStepAction() == null) {
        stepInput.setStepAction(STEP_ACTION_CLASS.UNSUPPORTED);
      }
      ActionStep actionStep = this.factoryAction(stepInput.getStepAction());
      if (actionStep == null) throw new BadRequestException("action step is null");

      Step step = actionStep.create(stepInput, workflow);
      step = this.saveStep(step);
      this.stepCondition(stepInput, step);
    }
  }

  /**
   * Start workflow for given simulation
   *
   * @param simulationId id of the simulation to start
   */
  public void startWorkflow(String simulationId) {
    Workflow workflowTemplate = workflowService.findWorkflowTemplateByIdExercise(simulationId);
    // Get all step template
    List<Step> stepsTemplate = this.findAllStepTemplateByWorkflow(workflowTemplate.getId());
    // todo Check edition content
    // If edited increase version workflow template
    // Create new workflow RUN save
    Workflow workflowRun = workflowService.launchWorkflow(simulationId);

    // Find step template with condition valid
    List<Step> stepWithValidCondition = new ArrayList<>();

    for (Step step : stepsTemplate) {
      Step stepWait = ready(step, workflowRun, null);
      if (stepWait != null) {
        stepWithValidCondition.add(stepWait);
      }
    }

    // IF NONE STEP TEMPLATE WITH CONDITION VALID update WORKFLOW with status END
    // todo manage steptemplate with time condition in queue
    /*if (stepWithValidCondition.isEmpty()) {
        workflowRun.setStatus(WORKFLOW_STATUS.END);
    }*/
  }

  /**
   * Create an execution step in the ready state for given template
   *
   * @param nextStepTemplateToExecute step template to ready
   * @param workflowRun the running workflow
   * @param input json input for the execution step
   * @return ready step
   */
  public Step ready(Step nextStepTemplateToExecute, Workflow workflowRun, String input) {
    ActionStep actionStep = this.factoryAction(nextStepTemplateToExecute.getStepAction());
    if (actionStep == null) throw new BadRequestException("action step is null");
    Step nextStepTemplateToExecutePersisted = this.findById(nextStepTemplateToExecute.getId());
    // CHECK CONDITIONS
    List<Condition> conditionExecution =
        conditionService.checkCondition(
            nextStepTemplateToExecutePersisted,
            input,
            nextStepTemplateToExecutePersisted.getData(),
            workflowRun,
            this);

    if (conditionExecution != null) {
      Step stepWait = actionStep.wait(nextStepTemplateToExecutePersisted, input, workflowRun);
      stepWait.setWorkflow(workflowRun);
      stepWait = this.saveStep(stepWait);

      Step finalStepWait = stepWait;

      // For each step template, IF condition is valid, create condition execution
      conditionExecution.forEach(
          condition -> {
            condition.setStep(finalStepWait);
          });
      conditionService.saveAllConditions(conditionExecution);
      try {
        queueChainingService.waitStep(finalStepWait, workflowRun);
      } catch (IOException e) {
        // TODO exception management
        throw new RuntimeException(e);
      }
      return stepWait;
    }

    return null;
  }

  /**
   * Run step that is ready
   *
   * @param stepReady step ready to run
   */
  public void run(Step stepReady) {
    ActionStep actionStep = this.factoryAction(stepReady.getStepAction());
    if (actionStep == null) throw new BadRequestException("action step is null");

    Step stepRun = actionStep.run(stepReady);
    if (stepRun == null) {
      stepReady.setStatus(STEP_STATUS.END);
      this.saveStep(stepReady);
      // Check all executed steps, if all ended, end workflow run
      int runningStep = stepRepository.countRunningStep(stepReady.getWorkflow().getId());
      if (runningStep == 0) {
        // TODO manage steptemplate with time delay
        Workflow run = stepReady.getWorkflow();
        run.setStatus(WORKFLOW_STATUS.END);
        workflowService.saveWorkflowRun(run);
      }
    } else {
      stepRun.setStatus(STEP_STATUS.RUN);
      this.saveStep(stepRun);
    }
  }

  /**
   * Count executed step
   *
   * @param workflowRunId id of the executed workflow
   * @param stepTemplateId step id for which to count the number of execution
   * @return integer
   */
  public int countExecutedStep(String workflowRunId, String stepTemplateId) {
    return stepRepository.countStepExecutedByStepTemplateIdAndWorkflowRunId(
        workflowRunId, stepTemplateId);
  }

  /**
   * Get an action class
   *
   * @param actionClass name of the action class
   * @return the corresponding action step class
   */
  public ActionStep factoryAction(STEP_ACTION_CLASS actionClass) {
    return switch (actionClass) {
      case STEP_ACTION_CLASS.INJECT_EXECUTION ->
          applicationContext.getBean(InjectExecutionStep.class);
      default -> null;
    };
  }

  /**
   * Save all the steps
   *
   * @param steps steps to save
   */
  public void saveSteps(List<Step> steps) {
    this.stepRepository.saveAll(steps);
  }

  /**
   * Check that the condition for a step are fulfilled
   *
   * @param stepInput input that are going to be used for the step
   * @param step step to check
   */
  private void stepCondition(StepsCreateInput.StepCreateInput stepInput, Step step) {
    if (stepInput.getConditions() == null || stepInput.getConditions().isEmpty()) {
      return;
    }
    ConditionCreateInput firstCondition =
        stepInput.getConditions().stream()
            .filter(
                conditionCreateInput ->
                    conditionCreateInput.getTemporaryIdConditionParent() == null)
            .reduce(
                (a, b) -> {
                  throw new IllegalArgumentException("Only 1 condition can be first parent");
                })
            .orElseThrow(
                () -> new IllegalArgumentException("Only 1 condition can be first parent"));

    Step stepFrom =
        firstCondition.getStepFrom() != null
            ? stepRepository.findById(firstCondition.getStepFrom()).orElse(null)
            : null;

    Condition first =
        Condition.builder()
            .step(step)
            .type(firstCondition.getType())
            .key(firstCondition.getKey())
            .value(firstCondition.getValue())
            .stepFrom(stepFrom)
            .build();

    first = conditionService.saveCondition(first);

    Map<String, Condition> temporaryIdAndSaveId = new HashMap<>();
    temporaryIdAndSaveId.put(firstCondition.getTemporaryId(), first);

    Map<String, List<ConditionCreateInput>> temporaryConditions =
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
        Step stepFromCondition =
            condition.getStepFrom() != null
                ? stepRepository.findById(condition.getStepFrom()).orElse(null)
                : null;

        Condition current =
            Condition.builder()
                .type(condition.getType())
                .key(condition.getKey())
                .value(condition.getValue())
                .conditionParent(
                    temporaryIdAndSaveId.get(condition.getTemporaryIdConditionParent()))
                .step(step)
                .stepFrom(stepFromCondition)
                .build();

        current = conditionService.saveCondition(current);

        temporaryIdAndSaveId.put(condition.getTemporaryId(), current);

        currentId.add(condition.getTemporaryId());
      }
    }
  }

  /**
   * Save step
   *
   * @param step step to save
   * @return saved step
   */
  public Step saveStep(Step step) {
    return this.stepRepository.save(step);
  }

  /**
   * Find step template by id
   *
   * @param idStep step id to find step template
   * @return found step
   */
  public Step findStepTemplateById(String idStep) {
    return this.stepRepository.findByStepTemplateIdIsNullAndIdAndStatus(
        idStep, STEP_STATUS.TEMPLATE);
  }

  /**
   * Find all step template by workflow
   *
   * @param idWorkflow workflow id to find all step templates
   * @return list of step
   */
  public List<Step> findAllStepTemplateByWorkflow(String idWorkflow) {
    return this.stepRepository.findAllByStepTemplateIdIsNullAndWorkflowId(idWorkflow);
  }

  /**
   * Find step ready by id
   *
   * @param idStep step id to find step ready
   * @return found step
   */
  public Step findStepReadyById(String idStep) {
    return this.stepRepository.findByStepTemplateIdIsNotNullAndIdAndStatus(
        idStep, STEP_STATUS.WAIT);
  }

  /**
   * Find all running steps
   *
   * @return list of step
   */
  public List<Step> findAllStepRun() {
    return this.stepRepository.findAllByStatus(STEP_STATUS.RUN);
  }

  /**
   * Returns all EXECUTED steps for a given Workflow Run and Step template.
   *
   * @param idStepTemplate the Step template identifier
   * @param idWorkflowRun the Workflow Run id
   * @return all matching RUN steps
   */
  public List<Step> findAllStepExecutedByStepTemplateIdAndWorkflowRunId(
      String idStepTemplate, String idWorkflowRun) {
    return this.stepRepository.findAllStepExecutedByStepTemplateIdAndWorkflowRunId(
        idStepTemplate, idWorkflowRun);
  }

  /**
   * Find step by id
   *
   * @param stepId id of the step
   * @return step
   */
  public Step findById(String stepId) {
    // TODO : exception management
    return stepRepository.findById(stepId).orElseThrow();
  }

  /**
   * Find step id by inject id
   *
   * @param injectId inject id to find step id
   * @return optional step id
   */
  public Optional<String> findStepIdByInjectId(final String injectId) {
    return stepRepository.findStepIdByInjectId(injectId);
  }

  /**
   * Find a json field from a path
   *
   * @param jsonString json to read
   * @param path path to check
   * @return path value
   */
  public static String getField(String jsonString, String path) {
    Map<String, Object> fieldsAndValue = new HashMap<>();
    fieldsAndValue.put(path, null);
    useJson(jsonString, fieldsAndValue, ACTION_JSON.GET);
    Object value = fieldsAndValue.get(path);
    if (value instanceof JsonNull) {
      return null;
    } else if (value instanceof JsonPrimitive) {
      return ((JsonPrimitive) value).getAsString();
    } else {
      return value.toString();
    }
  }

  /**
   * Find a json field from a path
   *
   * @param jsonString json to read
   * @param path path to check
   * @return json object
   */
  public static Map<String, Object> getFields(String jsonString, String path) {
    Map<String, Object> fieldsAndValue = new HashMap<>();
    fieldsAndValue.put(path, null);
    useJson(jsonString, fieldsAndValue, ACTION_JSON.GET);
    return fieldsAndValue;
  }

  /**
   * Update a json field from a path
   *
   * @param jsonString json to update
   * @param path path to update
   * @param newValue new value to update
   * @return updated json
   */
  public static String setField(String jsonString, String path, Object newValue) {
    Map<String, Object> fieldsAndValue = new HashMap<>();
    fieldsAndValue.put(path, newValue);
    JsonObject jsonUpdated = useJson(jsonString, fieldsAndValue, ACTION_JSON.REPLACE);
    return jsonUpdated.toString();
  }

  /**
   * Perform an action on a json path
   *
   * @param jsonString the root JSON object to use
   * @param fieldsAndValue a map where keys are dot-separated JSON paths and values are the new
   *     values to apply(ACTION_JSON.REPLACE) or will be value to get(ACTION_JSON.GET)
   * @param actionJson the action to perform
   * @return updated json
   */
  public static JsonObject useJson(
      String jsonString, Map<String, Object> fieldsAndValue, ACTION_JSON actionJson) {
    final Gson gson = new Gson();
    JsonObject jsonObject = gson.fromJson(jsonString, JsonObject.class);
    StringBuilder path = new StringBuilder();

    Map<String, Object> fieldsAndValueCopy = new HashMap<>(fieldsAndValue);
    for (String field : fieldsAndValueCopy.keySet()) {
      List<String> treeToUpdate = Arrays.asList(field.split("\\."));
      int indexFieldPath = 0;

      JsonElement o = jsonObject.get(treeToUpdate.get(indexFieldPath));
      path.delete(0, path.length());
      path.append(treeToUpdate.get(indexFieldPath)).append(".");
      if (o != null) {
        if (indexFieldPath == treeToUpdate.size() - 1) {
          path.deleteCharAt(path.length() - 1);
          actionJson(
              fieldsAndValue,
              field,
              treeToUpdate,
              jsonObject,
              null,
              null,
              indexFieldPath,
              actionJson,
              TYPE_JSON.DEFAULT,
              path);
        } else if (o.isJsonArray()) {
          iterateJsonArray(
              o.getAsJsonArray(),
              indexFieldPath,
              treeToUpdate,
              fieldsAndValue,
              field,
              actionJson,
              path);
        } else if (o.isJsonObject()) {
          iterateJsonObject(
              o.getAsJsonObject(),
              indexFieldPath,
              treeToUpdate,
              fieldsAndValue,
              field,
              actionJson,
              path);
        }
      }
    }
    return jsonObject;
  }

  /**
   * Perform an action in a json array
   *
   * @param jsonArray json array to use
   * @param index starting index
   * @param treeToUpdate list of json path to update
   * @param fieldsAndValue a map where keys are dot-separated JSON paths and values are the new
   *     values to apply(ACTION_JSON.REPLACE) or will be value to get(ACTION_JSON.GET)
   * @param field field from fieldsAndValue to manipulate
   * @param actionJson action to perform
   * @param path json path
   */
  private static void iterateJsonArray(
      JsonArray jsonArray,
      int index,
      List<String> treeToUpdate,
      Map<String, Object> fieldsAndValue,
      String field,
      ACTION_JSON actionJson,
      StringBuilder path) {

    Integer tabIndex = null;
    if (NumberUtils.isParsable(treeToUpdate.get(index + 1))) {
      tabIndex = Integer.parseInt(treeToUpdate.get(index + 1));
    }
    int indexArray = 0;
    for (JsonElement element : jsonArray) {
      StringBuilder copyPath = new StringBuilder(path.toString());
      copyPath.append(indexArray).append(".");
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
              TYPE_JSON.ARRAY,
              copyPath);
        } else if (element.isJsonObject()) {
          iterateJsonObject(
              element.getAsJsonObject(),
              index,
              treeToUpdate,
              fieldsAndValue,
              field,
              actionJson,
              copyPath);
        } else if (element.isJsonArray()) {
          iterateJsonArray(
              element.getAsJsonArray(),
              index,
              treeToUpdate,
              fieldsAndValue,
              field,
              actionJson,
              copyPath);
        }
      }
      indexArray++;
    }
  }

  /**
   * Perform an action in a json object
   *
   * @param jsonObject json object to use
   * @param index starting index
   * @param treeToUpdate list of json path to update
   * @param fieldsAndValue a map where keys are dot-separated JSON paths and values are the new
   *     values to apply(ACTION_JSON.REPLACE) or will be value to get(ACTION_JSON.GET)
   * @param field field from fieldsAndValue to manipulate
   * @param actionJson action to perform
   * @param path json path
   */
  private static void iterateJsonObject(
      JsonObject jsonObject,
      int index,
      List<String> treeToUpdate,
      Map<String, Object> fieldsAndValue,
      String field,
      ACTION_JSON actionJson,
      StringBuilder path) {
    index++;
    path.append(treeToUpdate.get(index)).append(".");
    if (index == treeToUpdate.size() - 1) {
      path.deleteCharAt(path.length() - 1);
      actionJson(
          fieldsAndValue,
          field,
          treeToUpdate,
          jsonObject,
          null,
          null,
          index,
          actionJson,
          TYPE_JSON.OBJECT,
          path);
    } else if (jsonObject.get(treeToUpdate.get(index)).isJsonArray()) {
      iterateJsonArray(
          (JsonArray) jsonObject.get(treeToUpdate.get(index)),
          index,
          treeToUpdate,
          fieldsAndValue,
          field,
          actionJson,
          path);
    } else if (jsonObject.get(treeToUpdate.get(index)).isJsonObject()) {
      iterateJsonObject(
          (JsonObject) jsonObject.get(treeToUpdate.get(index)),
          index,
          treeToUpdate,
          fieldsAndValue,
          field,
          actionJson,
          path);
    }
  }

  /**
   * Perform an action in a json array or object
   *
   * @param fieldsAndValue a map where keys are dot-separated JSON paths and values are the new
   *     values to apply(ACTION_JSON.REPLACE) or will be value to get(ACTION_JSON.GET)
   * @param field field from fieldsAndValue to manipulate
   * @param tree list of json path to update
   * @param jsonElement json object to use
   * @param jsonArray json array to use
   * @param tabIndexJsonArray index to update in json array
   * @param index starting index
   * @param actionJson action to perform
   * @param typeJson type of the json object
   * @param path json path
   */
  private static void actionJson(
      Map<String, Object> fieldsAndValue,
      String field,
      List<String> tree,
      JsonElement jsonElement,
      JsonArray jsonArray,
      Integer tabIndexJsonArray,
      int index,
      @NotNull ACTION_JSON actionJson,
      @NotNull TYPE_JSON typeJson,
      StringBuilder path) {
    switch (actionJson) {
      case REPLACE -> {
        JsonPrimitive newValue = toJsonPrimitive(fieldsAndValue.get(field));
        switch (typeJson) {
          case OBJECT -> {
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
            if (jsonElement.isJsonPrimitive()) {
              jsonArray.set(tabIndexJsonArray, newValue);
            } else {
              jsonElement.getAsJsonObject().remove(tree.get(index));
            }
          }
          case DEFAULT -> {
            JsonObject jsonObject = jsonElement.getAsJsonObject();
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
            fieldsAndValue.put(path.toString(), object.get(tree.get(index)));
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

  /**
   * Convert java primitive to json primitive
   *
   * @param primitiveObject primitive object to convert
   * @return converted json primitive
   */
  private static JsonPrimitive toJsonPrimitive(Object primitiveObject) {
    if (primitiveObject instanceof String) {
      return new JsonPrimitive((String) primitiveObject);
    }
    if (primitiveObject instanceof Boolean) {
      return new JsonPrimitive((Boolean) primitiveObject);
    }
    if (primitiveObject instanceof Number) {
      return new JsonPrimitive((Number) primitiveObject);
    }
    return new JsonPrimitive(primitiveObject.toString());
  }

  /**
   * Consume ready event from queue
   *
   * @param events list of events
   * @return consumed list of events
   */
  @Transactional
  public List<StepEvent> handleReadyEvent(List<StepEvent> events) {
    events.forEach(this::handleReadyStepEvent);
    return events;
  }

  /**
   * Consume delay event from queue
   *
   * @param events list of events
   * @return consumed list of events
   */
  @Transactional
  public List<StepEvent> handleDelayEvent(List<StepEvent> events) {
    events.forEach(this::handleDelayStepEvent);
    return events;
  }

  /**
   * Consume update event from queue
   *
   * @param events list of events
   * @return consumed list of events
   */
  @Transactional
  public List<ExternalUpdateEvent> handleExternalUpdateEvent(List<ExternalUpdateEvent> events) {
    events.forEach(this::handleExternalUpdateEvent);
    return events;
  }

  /**
   * Handle ready event and run the corresponding step
   *
   * @param stepEvent event to handle
   */
  @Override
  public void handleReadyStepEvent(StepEvent stepEvent) {
    stepRepository.findById(stepEvent.getStepId()).ifPresent(this::run);
  }

  /**
   * Handle delay event and pause the corresponding step
   *
   * @param stepEvent event to handle
   */
  @Override
  public void handleDelayStepEvent(StepEvent stepEvent) {
    stepRepository
        .findById(stepEvent.getStepId())
        .ifPresent(
            step -> {
              Workflow workflowRun = workflowService.getWorkflowById(stepEvent.getWorkflowId());
              // TODO: replace null value by actual output from previous step run ?
              ready(step, workflowRun, null);
            });
  }

  /**
   * Handle external update event and create next ready step
   *
   * @param stepEvent event to handle
   */
  @Override
  public void handleExternalUpdateEvent(ExternalUpdateEvent stepEvent) {
    Step stepRun = this.findById(stepEvent.getStepId());

    ActionStep actionStep = this.factoryAction(stepRun.getStepAction());
    if (actionStep == null) throw new BadRequestException("action step is null");

    Step stepUpdated = actionStep.update(stepRun);
    if (stepUpdated != null) {
      this.saveStep(stepUpdated);
      // GET STEP TEMPLATE
      Step stepTemplateCurrent = this.findStepTemplateById(stepRun.getStepTemplate().getId());
      Workflow workflowTemplate = stepTemplateCurrent.getWorkflow();
      List<Step> stepsTemplate = this.findAllStepTemplateByWorkflow(workflowTemplate.getId());

      // FIND OTHER STEP WHO NEED INPUT FROM THIS STEP
      List<Step> nextStepToExecute = new ArrayList<>();
      for (Step stepTemplate : stepsTemplate) {
        List<Condition> conditions = this.conditionService.findAllByStepId(stepTemplate.getId());
        for (Condition conditionTemplate : conditions) {
          if (conditionTemplate.getStepFrom() != null
              && conditionTemplate
                  .getStepFrom()
                  .getId()
                  .equals(stepRun.getStepTemplate().getId())) {
            nextStepToExecute.add(stepTemplate);
          }
        }
      }

      for (Step stepTemplate : nextStepToExecute) {
        this.ready(stepTemplate, stepRun.getWorkflow(), null);
      }
    }
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
