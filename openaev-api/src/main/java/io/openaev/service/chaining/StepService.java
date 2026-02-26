package io.openaev.service.chaining;

import com.google.gson.*;
import io.openaev.api.chaining.ActionStep;
import io.openaev.api.chaining.InjectExecutionStep;
import io.openaev.api.chaining.dto.ConditionCreateInput;
import io.openaev.api.chaining.dto.StepsCreateInput;
import io.openaev.database.model.*;
import io.openaev.database.repository.StepRepository;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exception.ChainingException;
import io.openaev.rest.exception.ElementNotFoundException;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class StepService implements StepEventHandler, ExternalUpdateEventHandler {
  private final InjectExecutionStep injectExecutionStep;

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
  @Transactional(rollbackOn = Exception.class)
  public void createStepTemplates(String workflowId, List<StepsCreateInput.StepCreateInput> steps)
      throws ChainingException {
    Workflow workflow =
        workflowService.getWorkflowByIdAndStatus(workflowId, WorkflowStatus.TEMPLATE);

    for (StepsCreateInput.StepCreateInput stepInput : steps) {
      ActionStep actionStep = factoryAction(stepInput.getStepAction(), null);

      Step step =
          actionStep
              .create(stepInput, workflow)
              .orElseThrow(() -> new ChainingException("Failed to create step (TEMPLATE)"));

      step = saveStep(step);
      stepConditionTemplate(stepInput, step);
    }
  }

  /**
   * Start workflow for given simulation
   *
   * @param simulationId id of the simulation to start
   */
  @Transactional(rollbackOn = Exception.class)
  public void startWorkflow(String simulationId) throws ChainingException {
    Workflow workflowTemplate =
        workflowService
            .findWorkflowTemplateBySimulationId(simulationId)
            .orElseThrow(
                () ->
                    new ElementNotFoundException(
                        "Workflow (TEMPLATE) not found. Simulation ID: " + simulationId));
    // Get all step template
    List<Step> stepsTemplate = this.findAllStepTemplateByWorkflow(workflowTemplate.getId());
    // todo Check edition content
    // If edited increase version workflow template
    // Create new workflow RUN save
    Workflow workflowRun = workflowService.launchWorkflow(workflowTemplate);

    if (stepsTemplate.isEmpty()) {
      log.info(
          "No step template for workflow template {}. End running {}",
          workflowTemplate.getId(),
          workflowRun.getId());
      workflowRun.setStatus(WorkflowStatus.END);
      workflowService.saveWorkflowRun(workflowRun);
      return;
    }

    // Find step template with condition valid
    List<Step> stepWithValidCondition = new ArrayList<>();

    for (Step step : stepsTemplate) {
      Optional<Step> stepReadyOpt = ready(step, workflowRun, null);
      stepReadyOpt.ifPresent(stepWithValidCondition::add);
    }

    // IF NONE STEP TEMPLATE WITH CONDITION VALID update WORKFLOW with status END
    // todo manage steptemplate with time condition in queue delay : can be done after new
    // implémentation of the queue delay (in db)
    /*if (stepWithValidCondition.isEmpty()) {
        workflowRun.setStatus(WorkflowStatus.END);
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
  public Optional<Step> ready(Step nextStepTemplateToExecute, Workflow workflowRun, String input)
      throws ChainingException {

    ActionStep actionStep =
        factoryAction(nextStepTemplateToExecute.getStepAction(), nextStepTemplateToExecute.getId());

    Step nextStepTemplateToExecutePersisted =
        findByIdAndStatus(nextStepTemplateToExecute.getId(), StepStatus.TEMPLATE);

    // CHECK CONDITIONS
    List<Condition> conditionExecution =
        conditionService.checkCondition(
            nextStepTemplateToExecutePersisted, input, workflowRun, this);

    // List<Condition> conditionExecution:
    // 1. null  : push in delay queue or exced limit execution or condition invalid  -> no execution
    // 2. empty  : no condition                                                  -> direct execution
    // 3. not empty : all conditions are valid (will be saved as condition execution)   -> execution
    if (conditionExecution != null) {
      Step stepReady;
      stepReady =
          actionStep
              .ready(nextStepTemplateToExecutePersisted, input, workflowRun)
              .orElseThrow(
                  () ->
                      new ChainingException(
                          "Error creating step (READY) from step (TEMPLATE). Step ID: "
                              + nextStepTemplateToExecute.getId()));
      stepReady = saveStep(stepReady);
      Step finalStepReady = stepReady;

      // For each step template, IF condition is valid, create condition execution
      conditionExecution.forEach(condition -> condition.setStep(finalStepReady));
      conditionService.saveAllConditions(conditionExecution);

      try {
        queueChainingService.readyStep(finalStepReady, workflowRun);
        return Optional.of(stepReady);
      } catch (IOException e) {
        stepReady.setStatus(StepStatus.END);
        saveStep(stepReady);
        throw new ChainingException(
            "Failed to push step (READY) into ready queue. Step moved to (END) state. Step ID: "
                + stepReady.getId(),
            e);
      }
    }

    return Optional.empty();
  }

  /**
   * Run step that is ready
   *
   * @param stepReady step ready to run
   */
  public void run(Step stepReady) {
    Step stepRun;
    try {
      ActionStep actionStep = factoryAction(stepReady.getStepAction(), stepReady.getId());
      stepRun =
          actionStep
              .run(stepReady)
              .orElseThrow(() -> new ChainingException("Step (READY) execution failed"));
    } catch (ChainingException e) {
      // todo system notif queue fail + system log for step + status FAIL
      log.error(
          "Ready consume : Step (READY) execution failed. Step moved to (END) state. Step ID: {} {}",
          stepReady.getId(),
          e.getMessage(),
          e);
      stepReady.setStatus(StepStatus.END);
      saveStep(stepReady);
      return;
      // todo Check all executed steps, if all ended, end workflow run
      /* int runningStep = stepRepository.countRunningStep(stepReady.getWorkflow().getId());
      if (runningStep == 0) {
        // TODO manage steptemplate with time delay
        Workflow run = stepReady.getWorkflow();
        run.setStatus(WorkflowStatus.END);
        workflowService.saveWorkflowRun(run);
      }*/
    }

    stepRun.setStatus(StepStatus.RUN);
    saveStep(stepRun);
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
  public ActionStep factoryAction(StepActionClass actionClass, String stepId)
      throws ChainingException {
    if (actionClass == null) {
      String stepInfo =
          (stepId != null)
              ? "Action step is null. Step ID:" + stepId
              : "Action step of new step (TEMPLATE) is null";
      throw new ChainingException(stepInfo, new BadRequestException(stepInfo));
    }
    return switch (actionClass) {
      case StepActionClass.INJECT_EXECUTION -> injectExecutionStep;
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
  void stepConditionTemplate(StepsCreateInput.StepCreateInput stepInput, Step step) {
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
                  throw new IllegalArgumentException(
                      "New step (TEMPLATE): Only 1 condition can be first parent");
                })
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "New step (TEMPLATE): Only 1 condition can be first parent"));

    Step stepFrom = getStepFromCondition(firstCondition.getStepFrom());

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
        Step stepFromCondition = getStepFromCondition(condition.getStepFrom());

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
    return this.stepRepository
        .findByStepTemplateIdIsNullAndIdAndStatus(idStep, StepStatus.TEMPLATE)
        .orElseThrow(() -> new ElementNotFoundException("Step template not find, id: " + idStep));
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
        idStep, StepStatus.READY);
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
   * @param status status of the step not null
   * @return optional step
   */
  public Step findByIdAndStatus(String stepId, @NotNull StepStatus status) {
    return stepRepository
        .findByIdAndStatus(stepId, status)
        .orElseThrow(
            () ->
                new ElementNotFoundException(
                    "Step " + status.name() + " not found. Step ID: " + stepId));
  }

  /**
   * Find step by id
   *
   * @param stepId id of the step
   * @return optional step
   */
  public Step findById(String stepId) {
    return stepRepository
        .findById(stepId)
        .orElseThrow(() -> new ElementNotFoundException("Step not found. Step ID: " + stepId));
  }

  /**
   * Find step id by inject id
   *
   * @param injectId inject id to find step id
   * @return optional step id
   */
  public String findStepIdByInjectId(final String injectId) {
    return stepRepository
        .findStepIdByInjectId(injectId)
        .orElseThrow(
            () -> new ElementNotFoundException("Step id not found for inject id : " + injectId));
  }

  /**
   * Find step ids by expectation ids
   *
   * @param expectationIds expectation ids to find associated step ids
   * @return Corresponding step IDs
   */
  public Set<String> findStepIdsByExpectationIds(final Set<String> expectationIds) {
    return stepRepository.findStepIdsByExpectationIds(expectationIds);
  }

  /**
   * Find a json field from a path
   *
   * @param jsonString json to read
   * @param path path to check
   * @return path value
   */
  public static String getField(String jsonString, String path) {
    Map<String, Object> fieldsAndValue = getFields(jsonString, path);
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
   * Retrieves a {@link Step} associated with a condition from its identifier.
   *
   * <p>If the provided {@code stepFromId} is not {@code null}, this method attempts to load the
   * corresponding {@link Step} from the repository.
   *
   * <ul>
   *   <li>If the step is found, it is returned.
   *   <li>If the step is not found, a {@link ChainingException} is thrown, wrapping an {@link
   *       ElementNotFoundException}.
   *   <li>If {@code stepFromId} is {@code null}, this method returns {@code null}.
   * </ul>
   *
   * @param stepFromId the identifier of the step referenced by a condition; may be {@code null}
   * @return the persisted {@link Step} corresponding to the given identifier, or {@code null} if
   *     the identifier is {@code null} {@link Step} is found
   */
  private Step getStepFromCondition(String stepFromId) {
    if (stepFromId != null) {
      return stepRepository
          .findById(stepFromId)
          .orElseThrow(
              () ->
                  new ElementNotFoundException(
                      "Condition references a non-existing step (field: stepFrom). Step ID: "
                          + stepFromId));
    }
    return null;
  }

  /**
   * Consume ready event from queue
   *
   * @param events list of events
   * @return consumed list of events
   */
  // Do not help to have a consistence data;
  // need a re push event system and/or log system to retry new output
  // @Transactional(rollbackOn = Exception.class)
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
  // todo remove
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
  // Do not help to have a consistence data;
  // need a re push event system and/or log system to retry new output
  // @Transactional(rollbackOn = Exception.class)
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
    stepRepository
        .findById(stepEvent.getStepId())
        .ifPresentOrElse(
            this::run,
            () ->
                log.error(
                    "Ready consume: Step not found for StepEvent ID: {}", stepEvent.getStepId()));
  }

  /**
   * Handle delay event and pause the corresponding step
   *
   * @param stepEvent event to handle
   */
  // TODO remove
  @Override
  public void handleDelayStepEvent(StepEvent stepEvent) {
    stepRepository
        .findByIdAndStatus(stepEvent.getStepId(), StepStatus.TEMPLATE)
        .ifPresentOrElse(
            step -> {
              Workflow workflowRun =
                  workflowService.getWorkflowByIdAndStatus(
                      stepEvent.getWorkflowId(), WorkflowStatus.RUN);
              try {
                ready(step, workflowRun, null);
              } catch (ChainingException e) {
                log.error("Delay consume failed : {}", e.getMessage(), e);
              }
            },
            () ->
                log.error(
                    "Delay consume: Step not found for StepEvent. Step ID: {}",
                    stepEvent.getStepId()));
  }

  /**
   * Handle external update event and create next ready step
   *
   * @param stepEvent event to handle
   */
  @Override
  public void handleExternalUpdateEvent(ExternalUpdateEvent stepEvent) {
    Step stepRun;
    try {
      stepRun = findByIdAndStatus(stepEvent.getStepId(), StepStatus.RUN);
    } catch (ElementNotFoundException e) {
      // Todo: system notif queue fail + system log for step + status FAIL
      log.error(
          "Update consume: Step (RUN) not found. Step ID: {} {}",
          stepEvent.getStepId(),
          e.getMessage(),
          e);
      return;
    }
    Optional<Step> stepUpdatedOpt;

    try {
      ActionStep actionStep = factoryAction(stepRun.getStepAction(), stepRun.getId());
      stepUpdatedOpt = actionStep.update(stepRun);
    } catch (ChainingException e) {
      // Todo: system notif queue fail + system log for step + status FAIL
      log.error(
          "Update consume : Step (RUN) update failed. Step moved to (END) state. Step ID: {} {}",
          stepRun.getId(),
          e.getMessage(),
          e);
      stepRun.setStatus(StepStatus.END);
      saveStep(stepRun);
      return;
    }

    if (stepUpdatedOpt.isPresent()) {
      Step stepUpdated = stepUpdatedOpt.get();
      this.saveStep(stepUpdated);
      // GET STEPS TEMPLATE
      Step stepTemplateCurrent = this.findStepTemplateById(stepRun.getStepTemplate().getId());
      Workflow workflowTemplate = stepTemplateCurrent.getWorkflow();

      // Todo: system notif queue fail + system log for step + status FAIL
      if (workflowTemplate == null) {
        log.error(
            "Workflow (TEMPLATE) not found for step (TEMPLATE). Step ID: {}",
            stepRun.getStepTemplate().getId());
        return;
      }

      List<Step> stepsTemplate = this.findAllStepTemplateByWorkflow(workflowTemplate.getId());

      // FIND OTHER STEP WHO NEED INPUT FROM THIS STEP
      List<Step> nextStepToExecute = new ArrayList<>();
      for (Step stepTemplate : stepsTemplate) {
        List<Condition> conditions =
            this.conditionService.findAllConditionsByStepId(stepTemplate.getId());
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
        // Todo: system notif queue fail + system log for step + status FAIL
        try {
          ready(stepTemplate, stepRun.getWorkflow(), null);
        } catch (ChainingException e) {
          log.error(
              "Failed to execute step (TEMPLATE). Step ID: {} {}",
              stepTemplate.getId(),
              e.getMessage(),
              e);
        }
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
