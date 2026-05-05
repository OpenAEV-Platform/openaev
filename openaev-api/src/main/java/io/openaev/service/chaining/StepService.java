package io.openaev.service.chaining;

import com.google.gson.*;
import io.openaev.api.chaining.ActionStep;
import io.openaev.api.chaining.ConditionMapper;
import io.openaev.api.chaining.InjectExecutionStep;
import io.openaev.api.chaining.dto.ConditionCreateInput;
import io.openaev.api.chaining.dto.StepInput;
import io.openaev.api.chaining.dto.StepsCreateInput;
import io.openaev.database.model.*;
import io.openaev.database.repository.StepDelayQueueRepository;
import io.openaev.database.repository.StepRepository;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exception.ChainingException;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.inject.service.InjectService;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class StepService {

  private final InjectExecutionStep injectExecutionStep;

  private final InjectService injectService;
  private final ConditionService conditionService;
  private final QueueChainingService queueChainingService;

  private final StepRepository stepRepository;
  private final StepDelayQueueRepository stepDelayQueueRepository;

  /**
   * Create a single step template.
   *
   * @param workflow workflow linked to the step template
   * @param stepInput input to create the step template
   * @return created step template
   */
  @Transactional(rollbackFor = Exception.class)
  public Step createStepTemplate(Workflow workflow, StepsCreateInput.StepInput stepInput)
      throws ChainingException {
    ActionStep actionStep = factoryAction(stepInput.getStepAction(), null);
    Step step =
        actionStep
            .create(stepInput, workflow)
            .orElseThrow(() -> new ChainingException("Failed to create step (TEMPLATE)"));

    step = saveStep(step);
    stepConditionTemplate(stepInput.getConditions(), workflow.getId(), step);
    conditionService.linkExistingConditionsToStep(step, stepInput.getConditionIds());
    return step;
  }

  /**
   * Create step templates.
   *
   * @param workflow workflow linked to the step templates
   * @param steps list of input to create step templates
   */
  @Transactional(rollbackFor = Exception.class)
  public void createStepTemplates(Workflow workflow, List<StepsCreateInput.StepInput> steps)
      throws ChainingException {
    for (StepsCreateInput.StepInput stepInput : steps) {
      createStepTemplate(workflow, stepInput);
    }
  }

  /**
   * Copies all step templates (and their conditions) from one workflow to another.
   *
   * @param workflowTemplateFrom source workflow
   * @param workflowTemplateTo target workflow
   */
  @Transactional(rollbackFor = Exception.class)
  public void copyStepTemplate(Workflow workflowTemplateFrom, Workflow workflowTemplateTo) {
    List<Step> stepsTemplate = findAllStepTemplateByWorkflow(workflowTemplateFrom.getId());

    // Copy steps template & Conditions
    // Todo add condition not linked to a step
    List<Step> stepsTemplateCopy = copyStepsTemplate(stepsTemplate, workflowTemplateTo);
    saveSteps(stepsTemplateCopy);
  }

  /**
   * Evaluates workflow progress by checking all step templates for valid conditions and creating
   * READY steps. Sets workflow to END if no steps are ready and no delayed steps remain.
   *
   * @param workflowRun the running workflow to evaluate
   * @return the updated workflow (may have status END)
   */
  @Transactional(rollbackFor = Exception.class)
  public Workflow evaluateWorkflowProgress(Workflow workflowRun) throws ChainingException {
    String workflowTemplateId = workflowRun.getWorkflowTemplate().getId();

    // Get all step template
    List<Step> stepsTemplate = findAllStepTemplateByWorkflow(workflowTemplateId);

    if (stepsTemplate.isEmpty()) {
      log.info(
          "No step template for workflow template {}. End running {}",
          workflowTemplateId,
          workflowRun.getId());
      workflowRun.setStatus(WorkflowStatus.END);
      return workflowRun;
    }

    // At least one template generated one or more ready execution steps.
    boolean hasReadySteps = false;

    for (Step step : stepsTemplate) {
      List<Step> stepReadys = createReadySteps(step, workflowRun, null);
      if (!stepReadys.isEmpty()) {
        hasReadySteps = true;
        enqueueReadySteps(stepReadys, workflowRun);
      }
    }

    // If none step TEMPLATE with valid conditions && no step template delayed update workflow with
    // status END
    if (!hasReadySteps && stepDelayQueueRepository.findAllByWorkflowRun(workflowRun).isEmpty()) {
      workflowRun.setStatus(WorkflowStatus.END);
    }

    return workflowRun;
  }

  /**
   * Evaluates conditions for a step template and creates READY execution steps for each valid
   * batch. Returns an empty list when conditions defer execution.
   *
   * @param nextStepTemplateToExecute step template to ready
   * @param workflowRun the running workflow
   * @param input json input for the execution step
   * @return created ready execution steps
   */
  public List<Step> createReadySteps(
      Step nextStepTemplateToExecute, Workflow workflowRun, String input) throws ChainingException {

    // If no condition mapper and step already executed, we skip the step to avoid to execute it
    // again
    if (!conditionService.hasConditionMapper(nextStepTemplateToExecute)
        && isStepAlreadyExecutedOnce(nextStepTemplateToExecute.getId(), workflowRun.getId())) {
      return List.of();
    }

    ActionStep actionStep =
        factoryAction(nextStepTemplateToExecute.getStepAction(), nextStepTemplateToExecute.getId());

    Step persistedTemplate =
        findByIdAndStatus(nextStepTemplateToExecute.getId(), StepStatus.TEMPLATE);

    List<ConditionService.ExecutionBatch> executionBatches =
        conditionService.checkCondition(persistedTemplate, workflowRun, input);

    if (executionBatches == null) {
      return List.of();
    }

    List<Step> stepReadys = new ArrayList<>();
    for (ConditionService.ExecutionBatch batch : executionBatches) {
      stepReadys.add(createReadyStepFromBatch(actionStep, persistedTemplate, workflowRun, batch));
    }
    return stepReadys;
  }

  /**
   * Creates a single READY step from an {@link ConditionService.ExecutionBatch}, persists it, and
   * links the batch's conditions to the new step.
   *
   * @param actionStep resolved action implementation
   * @param template persisted step template
   * @param workflowRun running workflow
   * @param batch execution batch carrying the resolved input and mapper conditions
   * @return persisted READY step
   */
  private Step createReadyStepFromBatch(
      ActionStep actionStep,
      Step template,
      Workflow workflowRun,
      ConditionService.ExecutionBatch batch)
      throws ChainingException {

    Step stepReady =
        actionStep
            .ready(template, batch.inputString(), workflowRun)
            .orElseThrow(
                () ->
                    new ChainingException(
                        "Error creating step (READY) from step (TEMPLATE). Step ID: "
                            + template.getId()));
    stepReady = saveStep(stepReady);
    linkBatchConditions(batch, stepReady);
    return stepReady;
  }

  /**
   * Links all mapper conditions from a batch to the given READY step and persists them.
   *
   * @param batch execution batch whose conditions to link
   * @param stepReady target step
   */
  private void linkBatchConditions(ConditionService.ExecutionBatch batch, Step stepReady) {
    List<Condition> conditionsToSave = new ArrayList<>();
    for (Condition mapper : batch.usedMappers()) {
      conditionService.linkToStep(mapper, stepReady, true);
      conditionsToSave.add(mapper);
    }
    conditionService.saveAllConditions(conditionsToSave);
  }

  /**
   * Pushes already-created READY steps to the queue.
   *
   * @param stepReadys steps to queue
   * @param workflowRun workflow run owning these steps
   */
  public void enqueueReadySteps(List<Step> stepReadys, Workflow workflowRun)
      throws ChainingException {
    for (Step stepReady : stepReadys) {
      try {
        queueChainingService.readyStep(stepReady, workflowRun);
      } catch (IOException e) {
        stepReady.setStatus(StepStatus.END);
        saveStep(stepReady);
        throw new ChainingException(
            "Failed to push step (READY) into ready queue. Step moved to (END) state. Step ID: "
                + stepReady.getId(),
            e);
      }
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
   * Returns {@code true} if at least one executed step references the given step template, meaning
   * this template has already been executed at least once in the given workflow run.
   *
   * @param stepTemplateId the ID of the step template to check
   * @param workflowRunId the ID of the workflow run to scope the check
   * @return {@code true} if the step template has been executed at least once in that run
   */
  public boolean isStepAlreadyExecutedOnce(String stepTemplateId, String workflowRunId) {
    return stepRepository.existsByStepTemplateIdAndWorkflowId(stepTemplateId, workflowRunId);
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
   * Creates the condition tree for a step template from the given input.
   *
   * <p>Conditions are linked to the target step via the {@code conditions_steps} join table. The
   * {@code stepFrom} FK on the {@link Condition} entity is <strong>not</strong> set here — it is
   * only used at runtime for time-based chaining (DEPEND_ON conditions).
   *
   * @param conditionInputs list of conditions to create
   * @param workflowId workflow id to associate with conditions
   * @param step step to check
   */
  void stepConditionTemplate(
      List<ConditionCreateInput> conditionInputs, String workflowId, Step step) {

    if (conditionInputs == null || conditionInputs.isEmpty()) {
      return;
    }

    conditionService.createConditionTree(
        conditionInputs,
        rootInput -> {
          Condition c = ConditionMapper.toCondition(rootInput);
          c.setWorkflowId(workflowId);
          return c;
        },
        (childInput, parent) -> {
          Condition c = ConditionMapper.toCondition(childInput, parent);
          c.setWorkflowId(workflowId);
          return c;
        },
        (condition, isRoot) -> conditionService.linkToStep(condition, step, isRoot),
        null);
  }

  /**
   * Copies a list of step templates (with data and conditions) to a target workflow.
   *
   * @param stepsFrom source step templates
   * @param workflowTo target workflow
   * @return list of copied step templates
   */
  @Transactional(rollbackFor = Exception.class)
  List<Step> copyStepsTemplate(List<Step> stepsFrom, Workflow workflowTo) {
    List<Step> stepsCopied = new ArrayList<>();
    for (Step step : stepsFrom) {
      String data = step.getData();
      if (workflowTo.getSimulation() != null) {
        data = StepService.setField(data, "inject_exercise", workflowTo.getSimulation().getId());
      }

      Step copy =
          Step.builder()
              .stepAction(step.getStepAction())
              .output(step.getOutput())
              .outputParser(step.getOutputParser())
              .input(step.getInput())
              .data(data)
              .limitExecution(step.getLimitExecution())
              .status(StepStatus.TEMPLATE)
              .workflow(workflowTo)
              .build();

      copy = saveStep(copy);
      copyStepConditionTemplate(step, copy);
      stepsCopied.add(copy);
    }
    return stepsCopied;
  }

  /**
   * Copies the condition tree from a source step to a target step, preserving parent hierarchy.
   *
   * @param step source step with conditions
   * @param stepCopied target step to attach copied conditions to
   */
  @Transactional(rollbackFor = Exception.class)
  void copyStepConditionTemplate(Step step, Step stepCopied) {
    List<Condition> conditions = conditionService.findAllConditionsByStepId(step.getId());
    if (conditions == null || conditions.isEmpty()) {
      return;
    }
    List<Condition> rootConditions =
        conditions.stream().filter(condition -> condition.getConditionParent() == null).toList();

    if (rootConditions.isEmpty()) {
      throw new IllegalArgumentException(
          "New step (TEMPLATE): At least 1 condition must be a root (no parent)");
    }

    // Multiple roots are only allowed when all roots are MAPPER conditions
    if (rootConditions.size() > 1) {
      boolean allMapper =
          rootConditions.stream().allMatch(c -> c.getType() == ConditionType.MAPPER);
      if (!allMapper) {
        throw new IllegalArgumentException(
            "New step (TEMPLATE): Only 1 condition can be first parent");
      }
    }

    Map<String, Condition> temporaryIdAndSaveId = new HashMap<>();

    for (Condition firstCondition : rootConditions) {
      Step stepFrom =
          firstCondition.getStepFrom() == null
              ? null
              : findStepFromCondition(firstCondition.getStepFrom().getId());

      Condition first =
          Condition.builder()
              .type(firstCondition.getType())
              .key(firstCondition.getKey())
              .value(firstCondition.getValue())
              .stepFrom(stepFrom)
              .build();

      conditionService.linkToStep(first, stepCopied, true);
      first = conditionService.saveCondition(first);

      temporaryIdAndSaveId.put(firstCondition.getId(), first);
    }

    Map<String, List<Condition>> temporaryConditions =
        conditions.stream()
            .filter(condition -> condition.getConditionParent() != null)
            .collect(Collectors.groupingBy(condition -> condition.getConditionParent().getId()));

    Queue<String> currentId = new LinkedList<>();
    rootConditions.forEach(rc -> currentId.add(rc.getId()));

    while (!currentId.isEmpty()) {
      String currentTemporaryId = currentId.poll();

      List<Condition> conditionsTemplate =
          temporaryConditions.getOrDefault(currentTemporaryId, new ArrayList<>());

      for (Condition condition : conditionsTemplate) {
        Step stepFromCondition =
            condition.getStepFrom() == null
                ? null
                : findStepFromCondition(condition.getStepFrom().getId());

        Condition current =
            Condition.builder()
                .type(condition.getType())
                .key(condition.getKey())
                .value(condition.getValue())
                .conditionParent(temporaryIdAndSaveId.get(condition.getConditionParent().getId()))
                .stepFrom(stepFromCondition)
                .build();

        conditionService.linkToStep(current, stepCopied, false);
        current = conditionService.saveCondition(current);

        temporaryIdAndSaveId.put(condition.getId(), current);

        currentId.add(condition.getId());
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
   * Find all step templates.
   *
   * @return list of all step templates
   */
  public List<Step> findAllStepTemplates() {
    return this.stepRepository.findAll().stream()
        .filter(step -> step.getStepTemplate() == null)
        .toList();
  }

  /**
   * Update an existing step template.
   *
   * @param stepId step template id
   * @param stepInput updated step payload
   * @return updated step template
   */
  @Transactional(rollbackFor = Exception.class)
  public Step updateStepTemplate(String stepId, StepInput stepInput) throws ChainingException {
    // Retrieve the existing step template from a database
    Step existing = findStepTemplateById(stepId);

    // Resolve the correct ActionStep implementation based on input action type
    ActionStep actionStep = factoryAction(stepInput.getStepAction(), stepId);

    // Convert StepInput to StepsCreateInput.StepInput for actionStep.create()
    StepsCreateInput.StepInput createInput = toCreateStepInput(stepInput);

    // Rebuild a "candidate" Step using the same logic as creation
    // This ensures validation and mapping rules are reused
    Step updatedCandidate =
        actionStep
            .create(createInput, existing.getWorkflow())
            .orElseThrow(() -> new ChainingException("Failed to update step (TEMPLATE)"));

    // Apply updated fields from the candidate to the existing persistent entity
    existing.setStepAction(updatedCandidate.getStepAction());
    existing.setLimitExecution(updatedCandidate.getLimitExecution());
    existing.setData(updatedCandidate.getData());
    existing.setInput(updatedCandidate.getInput());
    existing.setOutputParser(updatedCandidate.getOutputParser());
    Step updated = saveStep(existing);

    // Remove all existing conditions (full replace strategy),
    // but preserve conditions referenced by conditionIds so they can be re-linked
    conditionService.deleteAllConditionsByStepId(stepId, stepInput.getConditionIds());

    // Recreate conditions from input (same logic as create)
    stepConditionTemplate(stepInput.getConditions(), stepInput.getWorkflowId(), updated);
    conditionService.linkExistingConditionsToStep(updated, stepInput.getConditionIds());
    return updated;
  }

  /**
   * Converts a CRUD {@link StepInput} into a {@link StepsCreateInput.StepInput} for reuse in {@link
   * ActionStep#create}.
   */
  private static StepsCreateInput.StepInput toCreateStepInput(StepInput stepInput) {
    return StepsCreateInput.StepInput.builder()
        .stepAction(stepInput.getStepAction())
        .conditions(stepInput.getConditions())
        .conditionIds(stepInput.getConditionIds())
        .dataStep(stepInput.getDataStep())
        .build();
  }

  /**
   * Delete a step template and its conditions.
   *
   * @param stepId step template id
   */
  @Transactional(rollbackFor = Exception.class)
  public void deleteStepTemplate(String stepId) {
    Step step = findStepTemplateById(stepId);
    conditionService.deleteAllConditionsByStepId(stepId);
    stepRepository.delete(step);
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

  public Set<String> findStepIdsByInjectIds(final Set<String> injectIds) {
    if (injectIds == null || injectIds.isEmpty()) {
      return Set.of();
    }
    return stepRepository.findStepIdsByInjectIds(injectIds);
  }

  /**
   * Returns all RUN and READY steps for a given workflow execution.
   *
   * @param id workflow run ID
   * @return list of steps currently executing or ready
   */
  public List<Step> findAllStepExecutedByWorkflowRunId(String id) {
    return stepRepository.findAllStepByWorkflow_IdAndStatusIn(
        id, List.of(StepStatus.RUN, StepStatus.READY));
  }

  private Step findStepFromCondition(String stepFromId) {
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
   * Find a json field from a path
   *
   * @param jsonString json to read
   * @param path path to check
   * @return path value
   */
  public static String getField(String jsonString, String path) {
    Map<String, Object> fieldsAndValue = getFields(jsonString, path);
    Object value = fieldsAndValue.get(path);
    if (value == null || value instanceof JsonNull) {
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
        if (tabIndex != null) {
          index++;
        }
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
   * Retrieves an inject by its ID (delegates to InjectService).
   *
   * @param injectId the inject ID
   * @return the found inject
   */
  public Inject getInject(String injectId) {
    return injectService.inject(injectId);
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
