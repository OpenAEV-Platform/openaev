package io.openaev.service.chaining;

import com.google.gson.*;
import io.openaev.api.chaining.ActionStep;
import io.openaev.api.chaining.ConditionMapper;
import io.openaev.api.chaining.InjectExecutionStep;
import io.openaev.api.chaining.dto.ConditionCreateInput;
import io.openaev.api.chaining.dto.StepInput;
import io.openaev.api.chaining.dto.StepsCreateInput;
import io.openaev.database.model.*;
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
  private final StepAutoLinkService stepAutoLinkService;
  private final QueueChainingService queueChainingService;
  private final SimulationRateLimitService simulationRateLimitService;

  private final StepRepository stepRepository;

  static final List<StepStatus> ACTIVE_STEP_STATUS = List.of(StepStatus.READY, StepStatus.RUN);

  /**
   * Create a single step template.
   *
   * <p>When no condition list is provided at all, the contract auto-links are applied. An explicit
   * list — even empty — means the caller already picked its links and is kept untouched.
   *
   * @param workflow workflow linked to the step template
   * @param stepInput input to create the step template
   * @return created step template
   */
  @Transactional(rollbackFor = Exception.class)
  public Step createStepTemplate(Workflow workflow, StepsCreateInput.StepInput stepInput)
      throws ChainingException {
    WorkflowEditability.assertLogicMapEditable(workflow);
    if (stepInput.getConditions() == null) {
      stepInput.setConditions(stepAutoLinkService.buildAutoLinkConditions(stepInput.getDataStep()));
    }
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
   * Creates an INJECT_EXECUTION step template idempotently, reusing an existing twin instead of
   * minting a duplicate.
   *
   * <p>Autonomous (AI-driven) runs author their attack path by calling this repeatedly. The
   * orchestrator - or a retried/replayed tool call, or a re-run decision cycle - can legitimately
   * request the <b>same</b> inject step many times. Left unguarded, every call minted a new
   * template and the next workflow evaluation turned each into its own inject, producing the
   * observed duplicate storm (hundreds of identical injects materialising in seconds). The chaining
   * engine's per-target dedup cannot catch this: each template carries its own committed-hash local
   * state, so N templates for the same inject are N distinct, individually-valid executions.
   *
   * <p>Idempotency key = the baked inject {@code data} (identical for an identical {@link
   * io.openaev.rest.inject.form.InjectInput} within a run) plus the kill-chain parent (the {@code
   * DEPEND_ON} step template id, {@code null} for a root). A match is reused ONLY while it is still
   * <b>pending</b> - it has not yet spawned a run step - because that is the signature of the storm:
   * a burst of identical author calls before the workflow first evaluates them. Once a twin has
   * executed, a fresh author of the same inject is a deliberate <b>re-run</b> (e.g. the agent tried
   * a step, saw no finding, edited the payload/injector contract in place - leaving the inject data
   * byte-identical - and wants to fire it again) and MUST mint a new template so it actually runs
   * again. This is the boundary that lets the guard kill the duplicate storm without ever blocking
   * the normal try -> tweak -> re-fire loop. On a miss a new template is created exactly as {@link
   * #createStepTemplate}; the candidate is built once via the action step's {@code create} (needed
   * to compute {@code data}) and only persisted on a miss, so a hit performs no writes.
   *
   * @param workflow the workflow template to author on
   * @param stepInput the inject step input (its {@code conditions} carry the DEPEND_ON on a miss)
   * @param dependOnParentTemplateId the kill-chain parent step template id, or {@code null} for
   *     root
   * @return the existing pending twin on a hit, or the newly created template on a miss
   */
  @Transactional(rollbackFor = Exception.class)
  public Step createInjectStepTemplateIdempotent(
      Workflow workflow, StepsCreateInput.StepInput stepInput, String dependOnParentTemplateId)
      throws ChainingException {
    ActionStep actionStep = factoryAction(stepInput.getStepAction(), null);
    Step candidate =
        actionStep
            .create(stepInput, workflow)
            .orElseThrow(() -> new ChainingException("Failed to create step (TEMPLATE)"));

    String candidateData = candidate.getData();
    String normalizedParent = normalizeDependOnParent(dependOnParentTemplateId);
    Optional<Step> existing =
        findAllStepTemplateByWorkflow(workflow.getId()).stream()
            .filter(s -> StepActionClass.INJECT_EXECUTION.equals(s.getStepAction()))
            .filter(s -> Objects.equals(s.getData(), candidateData))
            .filter(
                s -> Objects.equals(normalizeDependOnParent(dependOnParentOf(s)), normalizedParent))
            // Collapse a duplicate ONLY while the twin is still pending (no run step yet). A twin
            // that already executed means this author is a deliberate re-run and must create a new
            // template - never block the try -> tweak -> re-fire loop.
            .filter(s -> !stepRepository.existsByStepTemplateId(s.getId()))
            .findFirst();
    if (existing.isPresent()) {
      log.info(
          "[Chaining] Idempotent author: reusing pending inject step template {} on workflow {} "
              + "instead of creating a duplicate (storm guard).",
          existing.get().getId(),
          workflow.getId());
      return existing.get();
    }

    Step step = saveStep(candidate);
    stepConditionTemplate(stepInput.getConditions(), workflow.getId(), step);
    conditionService.linkExistingConditionsToStep(step, stepInput.getConditionIds());
    return step;
  }

  private static String normalizeDependOnParent(String parentTemplateId) {
    return (parentTemplateId != null && !parentTemplateId.isBlank()) ? parentTemplateId : null;
  }

  private String dependOnParentOf(Step template) {
    return conditionService.findAllConditionsByStepId(template.getId()).stream()
        .filter(c -> c.getType() == ConditionType.DEPEND_ON)
        .map(Condition::getValue)
        .filter(v -> v != null && !v.isBlank())
        .findFirst()
        .orElse(null);
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
   * Evaluates conditions for a step template and creates READY execution steps for each valid
   * batch. Batches that exceed the configured rate limit are pushed into the delay queue instead of
   * being executed immediately. Only hashes of actually created steps are committed.
   *
   * @param nextStepTemplateToExecute step template to ready
   * @param workflowRun the running workflow
   * @param input json input for the execution step
   * @param pendingCount number of steps already scheduled in the current evaluation cycle but not
   *     yet reflected in the database
   * @return created ready execution steps (does not include delayed batches)
   */
  @Transactional(rollbackFor = Exception.class)
  public List<Step> createReadySteps(
      Step nextStepTemplateToExecute, Workflow workflowRun, String input, int pendingCount)
      throws ChainingException {

    // Re-load the step template within this transaction so lazy collections can be initialized.
    // The parameter may be a detached entity loaded outside any session (e.g. from a Quartz job).
    Step persistedTemplate =
        findByIdAndStatus(nextStepTemplateToExecute.getId(), StepStatus.TEMPLATE);

    ActionStep actionStep =
        factoryAction(persistedTemplate.getStepAction(), persistedTemplate.getId());

    List<ConditionService.ExecutionBatch> executionBatches =
        conditionService.checkCondition(persistedTemplate, workflowRun, input);

    if (executionBatches == null || executionBatches.isEmpty()) {
      return List.of();
    }

    // Expand each condition batch into one batch per scope target so that each READY step handles
    // exactly one inject → one execution unit. The injector-vs-payload targeting policy (external
    // injectors also expand per manual IP target) is owned by expandTargetBatches, so StepService
    // stays agnostic here.
    if (StepActionClass.INJECT_EXECUTION.equals(persistedTemplate.getStepAction())) {
      executionBatches =
          injectExecutionStep.expandTargetBatches(executionBatches, workflowRun, persistedTemplate);
      if (executionBatches.isEmpty()) {
        return List.of();
      }

      // Per-target deduplication: expanded batches carry a per-target hash (combo + target).
      // The combo-level dedup in prepareInputsForStepExecution runs BEFORE expansion and only
      // knows the combo hash, so it cannot skip individual targets already executed. Load the
      // committed hashes once and drop batches whose target was already turned into a READY step,
      // preventing the same inject from being re-executed on every scheduling cycle.
      Set<String> committedTargetHashes =
          conditionService.getCommittedHashes(persistedTemplate, workflowRun);
      if (!committedTargetHashes.isEmpty()) {
        executionBatches =
            executionBatches.stream()
                .filter(batch -> !committedTargetHashes.contains(batch.hash()))
                .toList();
        if (executionBatches.isEmpty()) {
          return List.of();
        }
      }
    }
    List<Step> stepReadys = new ArrayList<>();
    Set<String> committedHashes = new HashSet<>();
    int localPending = pendingCount;

    for (ConditionService.ExecutionBatch batch : executionBatches) {
      // Guard: rate limit — if the rate limit is reached, delay this batch with its
      // resolved input. The hash is NOT committed so the batch can be retried later.
      if (simulationRateLimitService.delayIfRateLimitReached(
          persistedTemplate, batch.inputString(), workflowRun, localPending)) {
        continue;
      }

      stepReadys.add(createReadyStepFromBatch(actionStep, persistedTemplate, workflowRun, batch));

      if (batch.hash() != null) {
        committedHashes.add(batch.hash());
      }
      localPending++;
    }

    // Commit only the hashes of batches that were actually turned into READY steps.
    conditionService.commitHashes(persistedTemplate, workflowRun, committedHashes);

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
   * Count active step by status
   *
   * @param workflowRunId id of the executed workflow
   * @return long
   */
  public long countActiveSteps(String workflowRunId) {
    return stepRepository.countActiveSteps(workflowRunId, ACTIVE_STEP_STATUS);
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
    // Shared across every step copied in this call so that a single condition/event linked to
    // several source steps (e.g. one "event" root shared by 3 actions) is copied exactly once
    // and reused (re-linked) for the other steps, instead of being duplicated per step.
    Map<String, Condition> copiedConditionsByOriginalId = new HashMap<>();
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
      copyStepConditionTemplate(step, copy, copiedConditionsByOriginalId);
      stepsCopied.add(copy);
    }
    return stepsCopied;
  }

  /**
   * Copies the condition tree from a source step to a target step, preserving parent hierarchy.
   *
   * <p>Root conditions already copied for a previous step in the same {@link #copyStepsTemplate}
   * call (tracked via {@code copiedConditionsByOriginalId}) are reused instead of duplicated, so
   * that a condition/event shared across multiple source steps stays shared in the copy too.
   *
   * @param step source step with conditions
   * @param stepCopied target step to attach copied conditions to
   * @param copiedConditionsByOriginalId map of original condition id -> already-copied condition,
   *     shared across all steps copied in the same {@link #copyStepsTemplate} call
   */
  @Transactional(rollbackFor = Exception.class)
  void copyStepConditionTemplate(
      Step step, Step stepCopied, Map<String, Condition> copiedConditionsByOriginalId) {
    // Roots linked to this step (source of truth for which trees to copy)
    List<Condition> linkedConditions = conditionService.findAllConditionsByStepId(step.getId());
    if (linkedConditions == null || linkedConditions.isEmpty()) {
      return;
    }
    List<Condition> rootConditions =
        linkedConditions.stream()
            .filter(condition -> condition.getConditionParent() == null)
            .toList();

    if (rootConditions.isEmpty()) {
      throw new IllegalArgumentException(
          "New step (TEMPLATE): At least 1 condition must be a root (no parent)");
    }

    // Allow one event/filter root plus any number of mapper roots.
    // This happens when a step has one linked event root and one or more action mappings.
    if (rootConditions.size() > 1) {
      long nonMapperRootCount =
          rootConditions.stream().filter(c -> c.getType() != ConditionType.MAPPER).count();
      if (nonMapperRootCount > 1) {
        throw new IllegalArgumentException(
            "New step (TEMPLATE): Only 1 condition can be first parent");
      }
    }

    // Full set of the source workflow's non-MAPPER conditions, to resolve children by parent id
    // independent of conditions_steps linkage (Event-API links only the root to the step).
    List<Condition> allSourceConditions =
        conditionService.findAllNonMapperConditionsByWorkflowId(step.getWorkflow().getId());

    Map<String, List<Condition>> temporaryConditions =
        allSourceConditions.stream()
            .filter(condition -> condition.getConditionParent() != null)
            .collect(Collectors.groupingBy(condition -> condition.getConditionParent().getId()));

    // Local view of the shared map, plus the subset of roots that are newly copied during this
    // call (as opposed to reused from a previous step) — only newly copied roots need their
    // children traversed/copied below.
    Map<String, Condition> temporaryIdAndSaveId = copiedConditionsByOriginalId;
    List<Condition> newlyCopiedRootConditions = new ArrayList<>();

    for (Condition firstCondition : rootConditions) {
      Condition alreadyCopied = temporaryIdAndSaveId.get(firstCondition.getId());
      if (alreadyCopied != null) {
        // This condition (and its subtree) was already copied for another step sharing it —
        // reuse it instead of duplicating, so the sharing is preserved in the copy.
        conditionService.linkToStep(alreadyCopied, stepCopied, true);
        continue;
      }

      Step stepFrom =
          firstCondition.getStepFrom() == null
              ? null
              : findStepFromCondition(firstCondition.getStepFrom().getId());

      Condition first =
          Condition.builder()
              .type(firstCondition.getType())
              .key(firstCondition.getKey())
              .keyTypes(firstCondition.getKeyTypes())
              .value(firstCondition.getValue())
              .caseSensitive(firstCondition.isCaseSensitive())
              .mappingType(firstCondition.getMappingType())
              .name(firstCondition.getName())
              .description(firstCondition.getDescription())
              .workflowId(stepCopied.getWorkflow().getId())
              .stepFrom(stepFrom)
              .build();

      conditionService.linkToStep(first, stepCopied, true);
      first = conditionService.saveCondition(first);

      temporaryIdAndSaveId.put(firstCondition.getId(), first);
      newlyCopiedRootConditions.add(firstCondition);
    }

    Queue<String> currentId = new LinkedList<>();
    newlyCopiedRootConditions.forEach(rc -> currentId.add(rc.getId()));

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
                .keyTypes(condition.getKeyTypes())
                .value(condition.getValue())
                .caseSensitive(condition.isCaseSensitive())
                .mappingType(condition.getMappingType())
                .name(condition.getName())
                .workflowId(stepCopied.getWorkflow().getId())
                .conditionParent(temporaryIdAndSaveId.get(condition.getConditionParent().getId()))
                .stepFrom(stepFromCondition)
                .build();

        conditionService.linkToStep(current, stepCopied, false);
        current = conditionService.saveCondition(current);

        // Keep the in-memory graph consistent for API mapping (mirrors persistConditionTree)
        Condition parent = current.getConditionParent();
        if (parent.getConditionChildren() == null) {
          parent.setConditionChildren(new ArrayList<>());
        }
        parent.getConditionChildren().add(current);

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
  @Transactional(readOnly = true)
  public List<Step> findAllStepTemplates() {
    return this.stepRepository.findAllByStepTemplateIdIsNull();
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

    WorkflowEditability.assertLogicMapEditable(existing.getWorkflow());

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

    // Remove all existing conditions (full replace strategy),
    // but preserve conditions referenced by conditionIds so they can be re-linked
    conditionService.deleteAllConditionsByStepId(
        stepId, stepInput.getConditionIds() != null ? stepInput.getConditionIds() : List.of());

    // Clear the step-side collection to stay consistent with the condition-side unlinking above.
    // linkExistingConditionsToStep below will recreate the preserved links.
    if (existing.getConditionSteps() != null) {
      existing.getConditionSteps().clear();
    }

    // Recreate conditions from input (same logic as create)
    stepConditionTemplate(stepInput.getConditions(), stepInput.getWorkflowId(), existing);
    conditionService.linkExistingConditionsToStep(existing, stepInput.getConditionIds());
    return saveStep(existing);
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
    WorkflowEditability.assertLogicMapEditable(step.getWorkflow());
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
  public Optional<String> findStepIdByInjectId(final String injectId) {
    return stepRepository.findStepIdByInjectId(injectId);
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
   * Returns all active steps for a given workflow execution.
   *
   * @param id workflow run ID
   * @return list of steps active
   */
  public List<Step> findAllStepActiveByWorkflowRunId(String id) {
    return stepRepository.findAllStepByWorkflow_IdAndStatusIn(id, ACTIVE_STEP_STATUS);
  }

  /**
   * Ends all active steps for the given workflow run.
   *
   * @param workflowId the workflow run ID
   * @return number of steps terminated
   */
  public int endActiveStepsByWorkflowId(String workflowId) {
    List<Step> activeSteps =
        stepRepository.findAllStepByWorkflow_IdAndStatusIn(workflowId, ACTIVE_STEP_STATUS);
    for (Step step : activeSteps) {
      step.setStatus(StepStatus.END);
    }
    stepRepository.saveAll(activeSteps);
    return activeSteps.size();
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
