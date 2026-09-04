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
  private final StepTargetingService stepTargetingService;

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
   * <b>pending</b> - it has not yet spawned a run step - because that is the signature of the
   * storm: a burst of identical author calls before the workflow first evaluates them. Once a twin
   * has executed, a fresh author of the same inject is a deliberate <b>re-run</b> (e.g. the agent
   * tried a step, saw no finding, edited the payload/injector contract in place - leaving the
   * inject data byte-identical - and wants to fire it again) and MUST mint a new template so it
   * actually runs again. This is the boundary that lets the guard kill the duplicate storm without
   * ever blocking the normal try -> tweak -> re-fire loop. On a miss a new template is created
   * exactly as {@link #createStepTemplate}; the candidate is built once via the action step's
   * {@code create} (needed to compute {@code data}) and only persisted on a miss, so a hit performs
   * no writes.
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
    // The EVENT linkage is part of the idempotency identity. When this author REUSES an existing
    // event by id (stepInput.conditionIds), only a pending twin ALREADY linked to exactly that
    // event may collapse this call: otherwise a same-inject / same-parent step authored under a
    // DIFFERENT event (or none) would swallow the call, silently drop the requested event_id, and
    // mislead the scenario mirror into twinning the wrong event. A fresh-event / no-event author
    // (empty conditionIds) keeps the historical data + parent identity, so the duplicate-storm
    // guard for replayed identical authors is unchanged.
    Set<String> requestedEventIds =
        stepInput.getConditionIds() == null
            ? Set.of()
            : stepInput.getConditionIds().stream()
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());
    Optional<Step> existing =
        findAllStepTemplateByWorkflow(workflow.getId()).stream()
            .filter(s -> StepActionClass.INJECT_EXECUTION.equals(s.getStepAction()))
            .filter(s -> Objects.equals(s.getData(), candidateData))
            .filter(
                s -> Objects.equals(normalizeDependOnParent(dependOnParentOf(s)), normalizedParent))
            // Same inject + same parent but a DIFFERENT reused event is NOT the same step.
            .filter(
                s -> requestedEventIds.isEmpty() || linkedEventRootIds(s).equals(requestedEventIds))
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
    return dependOnParentTemplateId(template.getId());
  }

  /**
   * Returns the DEPEND_ON parent step template id of a step template (the step it runs AFTER), or
   * {@code null} for a root step. Exposed so the autonomous read path can surface the kill-chain
   * graph to the orchestrator by step template id.
   *
   * @param stepTemplateId the step template to inspect
   * @return the parent step template id, or {@code null} for a root step
   */
  public String dependOnParentTemplateId(String stepTemplateId) {
    return conditionService.findAllConditionsByStepId(stepTemplateId).stream()
        .filter(c -> c.getType() == ConditionType.DEPEND_ON)
        .map(Condition::getValue)
        .filter(v -> v != null && !v.isBlank())
        .findFirst()
        .orElse(null);
  }

  /**
   * The set of AND/OR finding-event ROOT condition ids linked to a step template. Part of the
   * idempotency identity in {@link #createInjectStepTemplateIdempotent}: reusing a DIFFERENT event
   * by id must never collapse onto a same-inject / same-parent twin authored under another event.
   * Filters on root-ness (no parent) so a linked event leaf or a nested AND/OR group is never
   * mistaken for the event root.
   *
   * @param step the step template to inspect
   * @return the linked event root ids (empty when the step has no finding event)
   */
  private Set<String> linkedEventRootIds(Step step) {
    return conditionService.findAllConditionsByStepId(step.getId()).stream()
        .filter(c -> c.getConditionParent() == null)
        .filter(c -> c.getType() == ConditionType.AND || c.getType() == ConditionType.OR)
        .map(Condition::getId)
        .collect(Collectors.toSet());
  }

  /**
   * Updates an existing INJECT_EXECUTION step template's baked inject data IN PLACE, preserving its
   * id and its conditions (its DEPEND_ON kill-chain parent). This is how the AI orchestrator edits
   * a step it already authored - change the payload / target / injector contract / title of the
   * SAME step - instead of authoring a duplicate. The new inject definition is recomputed exactly
   * as {@link #createStepTemplate} would (so targeting, tags, documents and defaults resolve the
   * same way), then only the {@code data} column is swapped on the existing template. Conditions
   * are intentionally left untouched so the attack-path edges stay intact.
   *
   * @param stepTemplateId the id of the step template to update
   * @param stepInput the new inject step input
   * @return the updated step template
   */
  @Transactional(rollbackFor = Exception.class)
  public Step updateInjectStepTemplateData(
      String stepTemplateId, StepsCreateInput.StepInput stepInput) throws ChainingException {
    Step existing =
        stepRepository
            .findById(stepTemplateId)
            .orElseThrow(
                () ->
                    new ElementNotFoundException(
                        "Step template not found. Step template ID: " + stepTemplateId));
    if (!StepActionClass.INJECT_EXECUTION.equals(existing.getStepAction())) {
      throw new ChainingException(
          "Step template " + stepTemplateId + " is not an inject-execution step");
    }
    ActionStep actionStep = factoryAction(stepInput.getStepAction(), null);
    Step candidate =
        actionStep
            .create(stepInput, existing.getWorkflow())
            .orElseThrow(() -> new ChainingException("Failed to rebuild step data (TEMPLATE)"));
    existing.setData(candidate.getData());
    return saveStep(existing);
  }

  /**
   * Trigger-aware sibling of {@link #updateInjectStepTemplateData(String,
   * StepsCreateInput.StepInput)}: updates the baked inject data AND replaces the step's
   * finding-trigger conditions (event root + leaf filters + MAPPER bindings), while PRESERVING any
   * DEPEND_ON ordering parent so the kill-chain edge stays intact. This is how the AI orchestrator
   * CORRECTS a mis-wired finding-driven step (change what it fires on / consumes) in place, instead
   * of re-authoring a duplicate or moving it in the chain.
   *
   * <p>Deliberately does NOT assert {@link WorkflowEditability}: the orchestrator is the author of
   * an autonomous run's workflow, exactly like {@link #createInjectStepTemplateIdempotent}. The
   * condition swap mirrors the manual full-replace strategy in {@code updateStep} - delete the
   * non-preserved conditions, clear the step-side links, recreate from input, re-link the preserved
   * DEPEND_ON root.
   *
   * <p>When {@code stepInput.getConditionIds()} names EXISTING event roots to reuse (the
   * event-sharing path: a step corrected to fire on an event that already exists), those roots and
   * their whole subtree are PRESERVED across the delete (never unlinked, so a shared event other
   * steps depend on is never dropped) and then linked to this step, exactly the {@code
   * condition_ids} channel {@link #createInjectStepTemplateIdempotent} uses. The link is
   * idempotent, so re-passing an already-linked event is a no-op.
   *
   * @param stepTemplateId the id of the step template to update
   * @param stepInput the new inject step input (data); its {@code conditionIds} name existing event
   *     roots to reuse
   * @param triggerConditions the finding-trigger + mapper conditions to install (an empty list
   *     clears the trigger while keeping DEPEND_ON and any reused event links)
   * @return the updated step template
   */
  @Transactional(rollbackFor = Exception.class)
  public Step updateInjectStepTemplateDataAndTrigger(
      String stepTemplateId,
      StepsCreateInput.StepInput stepInput,
      List<ConditionCreateInput> triggerConditions)
      throws ChainingException {
    Step existing =
        stepRepository
            .findById(stepTemplateId)
            .orElseThrow(
                () ->
                    new ElementNotFoundException(
                        "Step template not found. Step template ID: " + stepTemplateId));
    if (!StepActionClass.INJECT_EXECUTION.equals(existing.getStepAction())) {
      throw new ChainingException(
          "Step template " + stepTemplateId + " is not an inject-execution step");
    }
    ActionStep actionStep = factoryAction(stepInput.getStepAction(), null);
    Step candidate =
        actionStep
            .create(stepInput, existing.getWorkflow())
            .orElseThrow(() -> new ChainingException("Failed to rebuild step data (TEMPLATE)"));
    existing.setData(candidate.getData());

    List<String> preservedDependOnIds =
        conditionService.findAllConditionsByStepId(stepTemplateId).stream()
            .filter(condition -> condition.getType() == ConditionType.DEPEND_ON)
            .map(Condition::getId)
            .toList();
    // Existing event roots the caller asked to reuse are preserved across the delete alongside the
    // DEPEND_ON parent, so rebuilding this step's trigger never unlinks - let alone deletes - a
    // shared event that other steps still fire on. deleteAllConditionsByStepId preserves the whole
    // subtree of every excluded root, so the event's leaves survive too.
    List<String> reusedEventIds =
        stepInput.getConditionIds() == null
            ? List.of()
            : stepInput.getConditionIds().stream()
                .filter(id -> id != null && !id.isBlank())
                .toList();
    List<String> preserved = new ArrayList<>(preservedDependOnIds);
    preserved.addAll(reusedEventIds);
    conditionService.deleteAllConditionsByStepId(stepTemplateId, preserved);
    if (existing.getConditionSteps() != null) {
      existing.getConditionSteps().clear();
    }
    stepConditionTemplate(triggerConditions, existing.getWorkflow().getId(), existing);
    conditionService.linkExistingConditionsToStep(existing, preservedDependOnIds);
    // Link the reused event roots (idempotent: a re-passed, still-linked event is a no-op; a newly
    // chosen event is linked fresh). This is how an updated step attaches to a SHARED event.
    conditionService.linkExistingConditionsToStep(existing, reusedEventIds);
    return saveStep(existing);
  }

  /**
   * Deletes an INJECT_EXECUTION step template and its conditions on behalf of the AI orchestrator,
   * WITHOUT the manual {@link WorkflowEditability} guard (the orchestrator is the author of an
   * autonomous run's workflow, exactly like {@link #createInjectStepTemplateIdempotent}). Used to
   * PRUNE a mis-authored finding-driven step. Executed run steps stay as history (their {@code
   * stepTemplate} reference is nulled by the on-delete policy), so pruning a template never
   * rewrites what already ran.
   *
   * @param stepTemplateId the id of the step template to delete
   */
  @Transactional(rollbackFor = Exception.class)
  public void deleteInjectStepTemplate(String stepTemplateId) throws ChainingException {
    Step existing =
        stepRepository
            .findById(stepTemplateId)
            .orElseThrow(
                () ->
                    new ElementNotFoundException(
                        "Step template not found. Step template ID: " + stepTemplateId));
    if (!StepActionClass.INJECT_EXECUTION.equals(existing.getStepAction())) {
      throw new ChainingException(
          "Step template " + stepTemplateId + " is not an inject-execution step");
    }
    conditionService.deleteAllConditionsByStepId(stepTemplateId);
    stepRepository.delete(existing);
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
   * <p>The clone runs in three phases so that every reference the graph carries can be re-pointed
   * at the <b>destination</b> workflow:
   *
   * <ol>
   *   <li><b>steps</b> — every step template is copied first, building the {@code oldStepId ->
   *       newStep} map. This must complete before any condition is copied: a {@code DEPEND_ON}
   *       condition stores a step template id in {@code condition_value} and may reference a step
   *       declared later in the list.
   *   <li><b>conditions</b> — the condition trees linked to each step, consuming the step map to
   *       rewrite {@code DEPEND_ON} values and {@code step_from} references.
   *   <li><b>standalone conditions</b> — conditions authored on the source workflow but not linked
   *       to any step (e.g. an event created before it was attached to an action). They belong to
   *       the authored content and would otherwise be silently lost by the copy.
   * </ol>
   *
   * @param workflowTemplateFrom source workflow
   * @param workflowTemplateTo target workflow
   */
  @Transactional(rollbackFor = Exception.class)
  public void copyStepTemplate(Workflow workflowTemplateFrom, Workflow workflowTemplateTo) {
    List<Step> stepsTemplate = findAllStepTemplateByWorkflow(workflowTemplateFrom.getId());

    Map<String, Step> copiedStepsByOriginalId = new LinkedHashMap<>();
    for (Step step : stepsTemplate) {
      copiedStepsByOriginalId.put(step.getId(), copyStepTemplate(step, workflowTemplateTo));
    }

    // Shared across every step copied in this call so that a single condition/event linked to
    // several source steps (e.g. one "event" root shared by 3 actions) is copied exactly once
    // and reused (re-linked) for the other steps, instead of being duplicated per step.
    Map<String, Condition> copiedConditionsByOriginalId = new HashMap<>();
    for (Step step : stepsTemplate) {
      copyStepConditionTemplate(
          step,
          copiedStepsByOriginalId.get(step.getId()),
          copiedConditionsByOriginalId,
          copiedStepsByOriginalId);
    }

    copyStandaloneConditions(
        workflowTemplateFrom,
        workflowTemplateTo,
        copiedConditionsByOriginalId,
        copiedStepsByOriginalId);

    saveSteps(new ArrayList<>(copiedStepsByOriginalId.values()));
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

      // Every INJECT_EXECUTION batch MUST carry a stable, non-null dedup hash so it is committed
      // below and skipped on the next scheduling cycle. Two paths produce a null hash: a step with
      // no mapper (a DEPEND_ON-only step, i.e. any step the orchestrator chains via
      // parent_step_template_id) and a step whose scope resolves to no asset (expandTargetBatches
      // then returns the original batch untouched, e.g. a team-targeted human step or an inject
      // that bakes its own asset). With a null hash the batch is never committed and the step
      // re-readies -> re-executes on EVERY evaluation cycle, spawning a storm of duplicate injects.
      // Fall back to a deterministic hash derived from the step template and the resolved input so
      // the step readies exactly once per (template, run), just like an asset-expanded batch does
      // via its per-target hash. Mapper batches already carry a non-null combo hash and are
      // untouched, so legitimate per-upstream-value re-execution still works.
      executionBatches =
          executionBatches.stream()
              .map(batch -> ensureNonNullBatchHash(batch, persistedTemplate))
              .toList();

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
   * Guarantees a batch has a non-null deduplication hash. Returns the batch unchanged when it
   * already carries one (mapper combos, per-target expanded batches); otherwise returns a copy with
   * a deterministic hash built from the step template id and the resolved input. This is what stops
   * a no-mapper / no-scope-asset INJECT_EXECUTION step (e.g. a DEPEND_ON step the orchestrator
   * chained, or a team-targeted / asset-baked inject) from re-readying and re-executing on every
   * evaluation cycle: the fallback hash is committed once, so the step readies exactly once per
   * (template, run). String.hashCode is spec-defined and deterministic, so the key is stable across
   * cycles and JVMs.
   */
  private ConditionService.ExecutionBatch ensureNonNullBatchHash(
      ConditionService.ExecutionBatch batch, Step template) {
    if (batch.hash() != null) {
      return batch;
    }
    String input = batch.inputString() != null ? batch.inputString() : "";
    String fallbackHash =
        "direct:" + template.getId() + ":" + Integer.toHexString(input.hashCode());
    return new ConditionService.ExecutionBatch(
        batch.inputString(), batch.usedMappers(), fallbackHash);
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
   * @deprecated use {@link #copyStepTemplate(Workflow, Workflow)}, which copies the whole graph in
   *     the phases required to re-point {@code DEPEND_ON} references. Kept for callers that already
   *     hold an explicit step list.
   */
  @Deprecated
  @Transactional(rollbackFor = Exception.class)
  List<Step> copyStepsTemplate(List<Step> stepsFrom, Workflow workflowTo) {
    Map<String, Step> copiedStepsByOriginalId = new LinkedHashMap<>();
    for (Step step : stepsFrom) {
      copiedStepsByOriginalId.put(step.getId(), copyStepTemplate(step, workflowTo));
    }
    Map<String, Condition> copiedConditionsByOriginalId = new HashMap<>();
    for (Step step : stepsFrom) {
      copyStepConditionTemplate(
          step,
          copiedStepsByOriginalId.get(step.getId()),
          copiedConditionsByOriginalId,
          copiedStepsByOriginalId);
    }
    return new ArrayList<>(copiedStepsByOriginalId.values());
  }

  /**
   * Copies a single step template into {@code workflowTo} and persists it, re-pointing every
   * owner-scoped id frozen in {@code step_data} and clearing every execution artefact it may carry.
   *
   * <p>{@code step_data} is a serialized {@link Inject}. Its id-bearing fields fall into three
   * classes:
   *
   * <ul>
   *   <li><b>re-point</b> — {@code inject_exercise} / {@code inject_scenario} name the owning
   *       object, so they must follow the destination workflow (and the other one must be cleared,
   *       or the copy claims to belong to both).
   *   <li><b>clear</b> — {@code inject_id} and the other execution artefacts baked in at runtime
   *       ({@code inject_status}, {@code inject_expectations}, {@code _chaining_target}, …). A
   *       TEMPLATE must never carry them, and inheriting them is execution bleed.
   *   <li><b>keep</b> — same-tenant shared references ({@code inject_injector_contract}, assets,
   *       asset groups, teams, documents, tags, …). The copy stays in the same tenant, so they stay
   *       valid; unlike the export path, nothing is re-resolved or lost.
   * </ul>
   *
   * <p>{@code step_input} carries only condition-mapper descriptors (key / path / mapping type) and
   * no identifier, so it is copied verbatim.
   *
   * @param step source step template
   * @param workflowTo destination workflow
   * @return the persisted copy
   */
  private Step copyStepTemplate(Step step, Workflow workflowTo) {
    Step copy =
        Step.builder()
            .stepAction(step.getStepAction())
            .output(step.getOutput())
            .outputParser(step.getOutputParser())
            .input(step.getInput())
            .data(repointStepData(step.getData(), workflowTo))
            .limitExecution(step.getLimitExecution())
            .status(StepStatus.TEMPLATE)
            .workflow(workflowTo)
            .build();
    return saveStep(copy);
  }

  /**
   * {@code step_data} fields that are execution artefacts: baked in while the step ran, never part
   * of the authored template. See {@link #copyStepTemplate(Step, Workflow)}.
   */
  private static final List<String> RUNTIME_STEP_DATA_FIELDS =
      List.of(
          "inject_id",
          "inject_status",
          "inject_collect_status",
          "inject_expectations",
          "inject_communications",
          "inject_sent_at",
          "inject_created_at",
          "inject_updated_at",
          "inject_trigger_now_date",
          "inject_expectations_drift_dismissed",
          "inject_depends_on",
          "_chaining_target");

  /**
   * Rewrites the owning object of a {@code step_data} blob and strips its execution artefacts.
   *
   * @param data the source {@code step_data} JSON, may be null or blank
   * @param workflowTo the destination workflow, whose scenario or simulation becomes the new owner
   * @return the re-pointed JSON, or the input unchanged when it is not a JSON object
   */
  private static String repointStepData(String data, Workflow workflowTo) {
    if (data == null || data.isBlank()) {
      return data;
    }
    JsonObject json;
    try {
      json = JsonParser.parseString(data).getAsJsonObject();
    } catch (RuntimeException e) {
      log.warn("Step data is not a JSON object, copied verbatim", e);
      return data;
    }

    // Exactly one owner: a step template belongs either to a scenario workflow or to a simulation
    // one. Setting the new owner without clearing the other would leave the copy pointing at the
    // source object.
    json.remove("inject_exercise");
    json.remove("inject_scenario");
    if (workflowTo.getSimulation() != null) {
      json.addProperty("inject_exercise", workflowTo.getSimulation().getId());
    } else if (workflowTo.getScenario() != null) {
      json.addProperty("inject_scenario", workflowTo.getScenario().getId());
    }

    RUNTIME_STEP_DATA_FIELDS.forEach(json::remove);

    return json.toString();
  }

  /**
   * Copies the condition tree from a source step to a target step, preserving parent hierarchy.
   *
   * <p>Root conditions already copied for a previous step in the same {@link
   * #copyStepTemplate(Workflow, Workflow)} call (tracked via {@code copiedConditionsByOriginalId})
   * are reused instead of duplicated, so that a condition/event shared across multiple source steps
   * stays shared in the copy too.
   *
   * @param step source step with conditions
   * @param stepCopied target step to attach copied conditions to
   * @param copiedConditionsByOriginalId map of original condition id -> already-copied condition,
   *     shared across all steps copied in the same {@link #copyStepTemplate(Workflow, Workflow)}
   *     call
   */
  @Transactional(rollbackFor = Exception.class)
  void copyStepConditionTemplate(
      Step step, Step stepCopied, Map<String, Condition> copiedConditionsByOriginalId) {
    copyStepConditionTemplate(step, stepCopied, copiedConditionsByOriginalId, Map.of());
  }

  /**
   * Copies the condition tree from a source step to a target step, preserving parent hierarchy and
   * re-pointing every step reference the tree carries at the destination workflow.
   *
   * @param step source step with conditions
   * @param stepCopied target step to attach copied conditions to
   * @param copiedConditionsByOriginalId map of original condition id -> already-copied condition,
   *     shared across all steps copied in the same {@link #copyStepTemplate(Workflow, Workflow)}
   *     call
   * @param copiedStepsByOriginalId map of original step template id -> its copy, used to rewrite
   *     {@code DEPEND_ON} values and {@code step_from} references
   */
  @Transactional(rollbackFor = Exception.class)
  void copyStepConditionTemplate(
      Step step,
      Step stepCopied,
      Map<String, Condition> copiedConditionsByOriginalId,
      Map<String, Step> copiedStepsByOriginalId) {
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

      Step stepFrom = resolveCopiedStepFrom(firstCondition, copiedStepsByOriginalId);

      Condition first =
          Condition.builder()
              .type(firstCondition.getType())
              .key(firstCondition.getKey())
              .keyTypes(firstCondition.getKeyTypes())
              .value(remapConditionValue(firstCondition, copiedStepsByOriginalId))
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
        Step stepFromCondition = resolveCopiedStepFrom(condition, copiedStepsByOriginalId);

        Condition current =
            Condition.builder()
                .type(condition.getType())
                .key(condition.getKey())
                .keyTypes(condition.getKeyTypes())
                .value(remapConditionValue(condition, copiedStepsByOriginalId))
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
   * Resolves the {@code step_from} reference of a copied condition.
   *
   * <p>{@code step_from} points at a step template of the <b>source</b> workflow. Copying it
   * verbatim would leave the copy's condition tree reaching back into the object it was cloned
   * from, so it is translated through the step id map. A reference that is not in the map (the map
   * is empty when a caller copies a single step's conditions in isolation) falls back to the
   * original step, preserving the previous behaviour.
   *
   * @param condition the source condition
   * @param copiedStepsByOriginalId map of original step template id -> its copy
   * @return the step the copied condition must point at, or {@code null} when the source carries
   *     none
   */
  private Step resolveCopiedStepFrom(
      Condition condition, Map<String, Step> copiedStepsByOriginalId) {
    if (condition.getStepFrom() == null) {
      return null;
    }
    String stepFromId = condition.getStepFrom().getId();
    Step copied = copiedStepsByOriginalId.get(stepFromId);
    return copied != null ? copied : findStepFromCondition(stepFromId);
  }

  /**
   * Translates a condition value that holds a step template id.
   *
   * <p>A {@code DEPEND_ON} condition stores its prerequisite as a <b>step template id in {@code
   * condition_value}</b>, not as a foreign key. At runtime {@code
   * ConditionService.evaluateDependOnConditions} resolves it with {@code
   * existsByStepTemplateIdAndWorkflowId(value, workflowRun)}, and the run steps it looks for carry
   * the template ids of the <b>destination</b> workflow. Copying the value verbatim therefore
   * leaves an id that can never match: the condition never becomes true, the dependent step is
   * never promoted to READY, and the branch is silently blocked forever.
   *
   * <p>Every other condition type stores a plain comparison value and is copied unchanged.
   *
   * @param condition the source condition
   * @param copiedStepsByOriginalId map of original step template id -> its copy
   * @return the value the copied condition must carry
   */
  private static String remapConditionValue(
      Condition condition, Map<String, Step> copiedStepsByOriginalId) {
    String value = condition.getValue();
    if (condition.getType() != ConditionType.DEPEND_ON || value == null || value.isBlank()) {
      return value;
    }
    Step copiedTarget = copiedStepsByOriginalId.get(value);
    if (copiedTarget == null) {
      // The prerequisite is not part of this copy (e.g. a single-step copy). Keeping the source id
      // would produce a permanently unsatisfiable dependency, so this is logged loudly.
      log.warn(
          "DEPEND_ON condition {} references step template {} which is not part of the copy; the dependency cannot be remapped",
          condition.getId(),
          value);
      return value;
    }
    return copiedTarget.getId();
  }

  /**
   * Copies the conditions of the source workflow that are not linked to any step.
   *
   * <p>An event authored in the Logic UI exists as a condition tree before it is attached to an
   * action, and stays unlinked for as long as the author has not wired it. Those trees are authored
   * content: a copy that skipped them would silently drop part of the user's work. The import path
   * ({@code V1_DataImporter.importConditionNodes}) already handles them; the copy path did not.
   *
   * <p>Conditions already copied through a step (present in {@code copiedConditionsByOriginalId})
   * are skipped, so nothing is duplicated.
   *
   * @param workflowFrom source workflow
   * @param workflowTo destination workflow
   * @param copiedConditionsByOriginalId conditions already copied via their step links; updated in
   *     place
   * @param copiedStepsByOriginalId map of original step template id -> its copy
   */
  private void copyStandaloneConditions(
      Workflow workflowFrom,
      Workflow workflowTo,
      Map<String, Condition> copiedConditionsByOriginalId,
      Map<String, Step> copiedStepsByOriginalId) {
    List<Condition> allSourceConditions =
        conditionService.findAllNonMapperConditionsByWorkflowId(workflowFrom.getId());
    if (allSourceConditions.isEmpty()) {
      return;
    }

    Map<String, List<Condition>> childrenByParentId =
        allSourceConditions.stream()
            .filter(condition -> condition.getConditionParent() != null)
            .collect(Collectors.groupingBy(condition -> condition.getConditionParent().getId()));

    List<Condition> standaloneRoots =
        allSourceConditions.stream()
            .filter(condition -> condition.getConditionParent() == null)
            .filter(condition -> !copiedConditionsByOriginalId.containsKey(condition.getId()))
            .toList();

    // Breadth-first so a parent is always copied - and present in the map - before its children.
    Queue<Condition> toCopy = new LinkedList<>(standaloneRoots);
    while (!toCopy.isEmpty()) {
      Condition source = toCopy.poll();
      Condition parentCopy =
          source.getConditionParent() == null
              ? null
              : copiedConditionsByOriginalId.get(source.getConditionParent().getId());

      Condition copy =
          Condition.builder()
              .type(source.getType())
              .key(source.getKey())
              .keyTypes(source.getKeyTypes())
              .value(remapConditionValue(source, copiedStepsByOriginalId))
              .caseSensitive(source.isCaseSensitive())
              .mappingType(source.getMappingType())
              .name(source.getName())
              .description(source.getDescription())
              .workflowId(workflowTo.getId())
              .conditionParent(parentCopy)
              .stepFrom(resolveCopiedStepFrom(source, copiedStepsByOriginalId))
              .build();

      copy = conditionService.saveCondition(copy);
      copiedConditionsByOriginalId.put(source.getId(), copy);

      toCopy.addAll(childrenByParentId.getOrDefault(source.getId(), List.of()));
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
   * Re-aligns the asset perimeter baked into every asset-centric INJECT_EXECUTION step template of
   * a workflow with the workflow scope.
   *
   * <p>Actions do not own their asset targets: in the chaining logic map the asset perimeter is
   * defined once by the workflow scope and merely <i>copied</i> into {@code
   * step.data.inject_assets} when the action is configured.
   *
   * @param workflow the workflow whose step templates must be realigned
   * @param scopedAssetIds the current in-scope asset IDs (denylist already applied)
   * @return the number of step templates actually rewritten
   */
  @Transactional(rollbackFor = Exception.class)
  public int syncScopeAssetsOnStepTemplates(Workflow workflow, List<String> scopedAssetIds) {
    List<Step> updated = new ArrayList<>();
    for (Step template : findAllStepTemplateByWorkflow(workflow.getId())) {
      if (!StepActionClass.INJECT_EXECUTION.equals(template.getStepAction())
          || template.getData() == null
          || !stepTargetingService.isAssetCentric(template)) {
        continue;
      }
      String newData = withScopeAssets(template, scopedAssetIds);
      if (newData != null) {
        template.setData(newData);
        updated.add(template);
      }
    }
    if (!updated.isEmpty()) {
      saveSteps(updated);
      log.debug(
          "[Chaining] Realigned {} step template(s) of workflow {} on the {} in-scope asset(s)",
          updated.size(),
          workflow.getId(),
          scopedAssetIds.size());
    }
    return updated.size();
  }

  /**
   * Returns the step data with {@code inject_assets} replaced by the given asset IDs, or {@code
   * null} when the data is already up to date (or cannot be parsed) so the caller skips the write.
   */
  private String withScopeAssets(Step template, List<String> scopedAssetIds) {
    try {
      JsonObject dataObject = JsonParser.parseString(template.getData()).getAsJsonObject();
      JsonArray assets = new JsonArray();
      scopedAssetIds.forEach(assets::add);
      if (assets.equals(dataObject.get("inject_assets"))) {
        return null;
      }
      dataObject.add("inject_assets", assets);
      return dataObject.toString();
    } catch (Exception e) {
      log.warn(
          "[Chaining] Failed to realign scope assets on step template {}: {}",
          template.getId(),
          e.getMessage());
      return null;
    }
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
