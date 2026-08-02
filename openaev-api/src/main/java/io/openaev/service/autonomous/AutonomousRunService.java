package io.openaev.service.autonomous;

import static java.time.Instant.now;
import static java.time.temporal.ChronoUnit.MINUTES;
import static org.springframework.util.StringUtils.hasText;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.api.autonomous.dto.AutonomousAttackPathStepState;
import io.openaev.api.autonomous.dto.AutonomousInputMapping;
import io.openaev.api.autonomous.dto.AutonomousPromotedAssetResult;
import io.openaev.api.autonomous.dto.AutonomousRunCreateInput;
import io.openaev.api.autonomous.dto.AutonomousScopeEntry;
import io.openaev.api.autonomous.dto.AutonomousScopeView;
import io.openaev.api.autonomous.dto.AutonomousStepTrigger;
import io.openaev.api.autonomous.dto.AutonomousTargetTeamResult;
import io.openaev.api.autonomous.dto.AutonomousTriggerFilter;
import io.openaev.api.chaining.dto.ConditionCreateInput;
import io.openaev.api.chaining.dto.WorkflowConfigurationInput;
import io.openaev.api.chaining.dto.WorkflowScopeRuleInput;
import io.openaev.config.OpenAEVConfig;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.ConditionType;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.ExerciseStatus;
import io.openaev.database.model.Finding;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.MappingType;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.ScopeRuleSelectedMode;
import io.openaev.database.model.ScopeRuleSource;
import io.openaev.database.model.Team;
import io.openaev.database.model.User;
import io.openaev.database.model.Workflow;
import io.openaev.database.model.WorkflowScopeRule;
import io.openaev.database.model.autonomous.AutonomousDirective;
import io.openaev.database.model.autonomous.AutonomousDirectiveStatus;
import io.openaev.database.model.autonomous.AutonomousEvent;
import io.openaev.database.model.autonomous.AutonomousEventType;
import io.openaev.database.model.autonomous.AutonomousObjectiveTemplate;
import io.openaev.database.model.autonomous.AutonomousRun;
import io.openaev.database.model.autonomous.AutonomousRunStatus;
import io.openaev.database.model.autonomous.AutonomousScopeTarget;
import io.openaev.database.repository.AssetGroupRepository;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.FindingRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.TeamRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.database.repository.autonomous.AutonomousDirectiveRepository;
import io.openaev.database.repository.autonomous.AutonomousRunRepository;
import io.openaev.rest.asset.endpoint.form.EndpointInput;
import io.openaev.rest.exception.ChainingException;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.rest.inject.form.InjectInput;
import io.openaev.service.EndpointService;
import io.openaev.service.PreviewFeatureService;
import io.openaev.service.ScenarioToExerciseService;
import io.openaev.service.chaining.WorkflowService;
import io.openaev.service.scenario.ScenarioService;
import io.openaev.xtmone.XtmOneClient;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

/**
 * Lifecycle owner for autonomous (AI-driven) attack-path runs. This is OpenAEV's half of the
 * feature: it seeds and drives a chained simulation as the execution/visualization substrate, hands
 * the objective to the XTM One orchestrator (the "brain"), and exposes the run's live state,
 * decision timeline, and real-time steering surface back to the UI.
 *
 * <p>The orchestrator streams its progress back through {@link #recordEvent} / {@link
 * #updateStatus} and reads operator steering through {@link #consumePendingDirectives}, so a run
 * can be followed and re-steered without ever stopping it. Every mutation is gated behind {@link
 * PreviewFeatureService#isAutonomousAttackPathEnabled()}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AutonomousRunService {

  /** Temporary-id anchor for the synthetic AND/OR root of a finding-driven trigger tree. */
  private static final String TRIGGER_ROOT_TMP_ID = "trigger-root";

  private final AutonomousRunRepository runRepository;
  private final AutonomousDirectiveRepository directiveRepository;
  private final AutonomousEventService eventService;
  private final AutonomousObjectiveTemplateService templateService;
  private final ScenarioService scenarioService;
  private final ScenarioToExerciseService scenarioToExerciseService;
  private final WorkflowService workflowService;
  private final ExerciseService exerciseService;
  private final PreviewFeatureService previewFeatureService;
  private final XtmOneClient xtmOneClient;
  private final OpenAEVConfig openAEVConfig;
  private final ObjectMapper objectMapper;
  private final InjectRepository injectRepository;
  private final FindingRepository findingRepository;
  private final EndpointService endpointService;
  private final TeamRepository teamRepository;
  private final UserRepository userRepository;
  private final AssetGroupRepository assetGroupRepository;
  private final ExerciseRepository exerciseRepository;

  // A dedicated bean persists the read-path reconcile in its OWN transaction (REQUIRES_NEW) through
  // its Spring proxy. It must be a separate class: a same-class call would bypass the proxy, keep
  // writing inside the caller's (read-only) transaction, and mark it rollback-only.
  private final AutonomousRunReconciliationWriter reconciliationWriter;

  private void requireFeature() {
    if (!previewFeatureService.isAutonomousAttackPathEnabled()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
  }

  private AutonomousRun require(String runId) {
    return runRepository
        .findById(runId)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Autonomous run not found"));
  }

  // region lifecycle

  /**
   * Creates a run: resolves the objective (free text or template), auto-provisions the attack-path
   * (chaining) substrate, spins up its running simulation, and persists the durable run handle in
   * {@code CREATED}. The run is fully autonomous - the operator never authors an attack path; the
   * AI orchestrator builds and executes it. An existing chaining scenario may still be passed
   * explicitly (advanced), otherwise a fresh one is provisioned from the objective. The
   * orchestrator is engaged separately by {@link #start} so creation stays fast and idempotent.
   */
  @Transactional(rollbackFor = Exception.class)
  public AutonomousRun create(AutonomousRunCreateInput input) {
    requireFeature();
    String objective = resolveObjective(input);

    Scenario scenario;
    String scenarioId = input.getScenarioId();
    if (scenarioId != null && !scenarioId.isBlank()) {
      // Advanced path: seed from a caller-provided chaining scenario.
      scenario = scenarioService.scenario(scenarioId);
      if (!workflowService.isScenarioChaining(scenarioId)) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "The scenario must define a chaining (attack path) workflow to run autonomously");
      }
      // Mark the caller-provided scenario autonomous too, so its detail page renders the AI cockpit
      // without a lookup probe (persisted via dirty-checking in this transaction).
      if (!scenario.isAutonomous()) {
        scenario.setAutonomous(true);
        scenario = scenarioService.updateScenario(scenario);
      }
    } else {
      // Autonomous default: provision a fresh attack-path scenario so the operator never has to
      // build one. The AI orchestrator populates and drives it after start().
      scenario = provisionAutonomousScenario(objective, input.getName(), input.getDescription());
      scenarioId = scenario.getId();
    }

    // Keep the chaining workflow alive: an autonomous run launches with an EMPTY workflow and the
    // orchestrator authors its steps incrementally, so the workflow must not be ended at launch or
    // between decision cycles (and its 1h timeout must be off). Marked on the scenario template
    // BEFORE launch so the flag propagates to the simulation template and RUN workflow.
    markWorkflowKeepAlive(scenarioId);

    boolean planMode = input.isPlanMode();
    Exercise simulation =
        scenarioToExerciseService.toExercise(
            scenario, now().truncatedTo(MINUTES).plus(1, MINUTES), true);
    // Dry-run: provision the simulation substrate so the orchestrator's authoring / mirror / read
    // tools work unchanged, but never start its RUN workflow. With no run workflow the chaining
    // engine has nothing to ready or dispatch, so a plan authors a full attack path without ever
    // executing an inject. A live run starts the workflow as usual.
    //
    // A plan STILL needs the simulation-scoped TEMPLATE workflow (and its step templates): without
    // it every author call fails with "Workflow (TEMPLATE) not found", AND the simulation is not
    // recognized as chaining, so the auto-closing scheduler finishes the empty plan simulation out
    // from under the orchestrator (the reported "simulation FINISHED without user action" +
    // "no workflow to author into" block). So we provision the TEMPLATE only in plan mode.
    try {
      if (planMode) {
        workflowService.provisionSimulationTemplateWorkflow(scenarioId, simulation);
      } else {
        workflowService.startWorkflowByScenarioIdAndSimulation(scenarioId, simulation);
      }
    } catch (ChainingException e) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Failed to start the chained simulation: " + e.getMessage(), e);
    }

    AutonomousRun run = new AutonomousRun();
    run.setObjective(objective);
    run.setObjectiveTemplateKey(input.getObjectiveTemplateKey());
    run.setPlanMode(planMode);
    run.setScenarioId(scenarioId);
    run.setSimulationId(simulation.getId());
    // The run's mixed scope projection is the union of the legacy target list ('scope' +
    // single-id shortcuts) and any allow-listed ENTITY rules coming from the launch stepper's full
    // scope definition. Manual IP / CIDR / hostname / CSV and deny-list rules cannot be expressed
    // as
    // entity targets, so they live only on the workflow (and drive the Scope tab) - the projection
    // stays entity-only for the orchestrator and restart/reconcile paths.
    List<AutonomousScopeTarget> scope = resolveScope(input);
    for (AutonomousScopeTarget target : allowlistTargetsFromRules(input.getScopeRules())) {
      addScopeIfAbsent(scope, target.getType(), target.getId());
    }
    run.setScope(scope);
    // Convenience projections (first of each kind) keep the preset asset-group flow and any legacy
    // single-scope consumers working while the mixed list stays authoritative.
    run.setScopeAssetGroupId(firstScopeIdOfType(scope, "ASSETS_GROUPS"));
    run.setScopeTeamId(firstScopeIdOfType(scope, "TEAMS"));
    // Persist the launch-time scope onto the scenario + run workflows (both allow-list AND
    // deny-list, every source), so the perimeter is enforced and shown in the Scope tab (and the
    // empty-allowlist warning clears) instead of living only in the orchestrator's head. The full
    // 'scope_rules' definition from the stepper is combined with the legacy entity targets (as
    // allow-list). When nothing is scoped this is a no-op and the orchestrator records its resolved
    // scope later via set_openaev_run_scope.
    List<WorkflowScopeRuleInput> seededScopeRules = new ArrayList<>();
    if (input.getScopeRules() != null) {
      for (WorkflowScopeRuleInput rule : input.getScopeRules()) {
        if (rule != null
            && rule.getSelectedMode() != null
            && rule.getRuleSource() != null
            && hasText(rule.getRuleValue())) {
          seededScopeRules.add(rule);
        }
      }
    }
    seededScopeRules.addAll(toAllowlistScopeInputs(resolveScope(input)));
    workflowService.writeScopeRules(scenarioId, simulation.getId(), seededScopeRules);
    // Make any allow-listed team's members deliverable on the simulation, so a human-targeted step
    // that inherits the scope can actually be sent (mirrors setRunScope / inject authoring).
    enableTargetedTeamMembers(
        simulation.getId(),
        scope.stream()
            .filter(t -> "TEAMS".equals(t.getType()))
            .map(AutonomousScopeTarget::getId)
            .toList());
    run.setXtmAgentSlug(input.getAgentSlug());
    run.setStatus(AutonomousRunStatus.CREATED);
    AutonomousRun saved = runRepository.save(run);

    eventService.append(
        saved.getId(),
        simulation.getId(),
        AutonomousEventType.STATUS,
        planMode ? "Plan created" : "Run created",
        (planMode
            ? "Autonomous attack-path DRY-RUN created from scenario \""
                + scenario.getName()
                + "\". The orchestrator will design the attack path without executing anything."
            : "Autonomous attack-path run created from scenario \"" + scenario.getName() + "\"."),
        null);
    return saved;
  }

  /**
   * Engages the XTM One orchestrator for a created run. The call is a short fire-and-forget
   * enqueue; the orchestrator then drives OpenAEV back through the platform MCP tools and these
   * callbacks.
   */
  @Transactional(rollbackFor = Exception.class)
  public AutonomousRun start(String runId) {
    requireFeature();
    AutonomousRun run = require(runId);
    if (run.getStatus() != AutonomousRunStatus.CREATED
        && run.getStatus() != AutonomousRunStatus.PAUSED) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Run cannot be started from status " + run.getStatus());
    }
    boolean planMode = run.isPlanMode();
    run.setStatus(planMode ? AutonomousRunStatus.PLANNING : AutonomousRunStatus.RUNNING);
    run.setLastError(null);
    AutonomousRun saved = runRepository.save(run);
    eventService.append(
        runId,
        run.getSimulationId(),
        AutonomousEventType.STATUS,
        planMode ? "Planning started" : "Run started",
        planMode
            ? "Orchestrator engaged in dry-run: designing the attack path, nothing is executed."
            : "Orchestrator engaged; autonomous execution is now running.",
        null);
    engageOrchestratorAfterCommit(saved);
    return saved;
  }

  /**
   * Schedules engagement (or re-engagement) of the XTM One orchestrator for {@code run} once the
   * surrounding transaction has committed. The upstream start endpoint is idempotent AND
   * re-engaging: it resets the run's existing durable execution to a fresh engagement when one
   * already exists (restart / resume) or creates one otherwise, so this single call covers first
   * start, resume, and restart.
   *
   * <p><b>Never call the orchestrator inside the transaction.</b> {@link
   * XtmOneClient#startAutonomousRun} is a blocking HTTP call; running it before commit parks the
   * request thread with the run + simulation row locks still held, which is exactly what wedged a
   * scenario delete behind an {@code idle in transaction} connection. Deferring to {@code
   * afterCommit} (mirroring the cancel / wake paths) means the locks are already released when the
   * call runs, so a slow or unreachable XTM One can never block OpenAEV's own read / delete paths.
   * The returned session handle is persisted in its own short transaction, and an engage failure is
   * recorded on the run (never rolls the start/resume back).
   */
  private void engageOrchestratorAfterCommit(AutonomousRun run) {
    // Snapshot the values the call needs now, while the entity is managed - the callback runs after
    // this transaction has committed and the entity may be detached.
    final String runId = run.getId();
    final String agentSlug = run.getXtmAgentSlug();
    final String objective = run.getObjective();
    final String simulationId = run.getSimulationId();
    final String scopeAssetGroupId = run.getScopeAssetGroupId();
    final String scopeTeamId = run.getScopeTeamId();
    final List<AutonomousScopeTarget> scope =
        run.getScope() != null ? new ArrayList<>(run.getScope()) : new ArrayList<>();
    final String scopeMode = resolveScopeMode(run.getObjectiveTemplateKey());
    final boolean planMode = run.isPlanMode();
    // A promoted real run carries the dry-run's plan summary as guidance ("follow this plan but
    // adapt to live findings"); a plan run and a plain live run pass none.
    final String priorPlan = planMode ? null : run.getPlanGuidance();
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              engageOrchestratorNow(
                  runId,
                  agentSlug,
                  objective,
                  simulationId,
                  scopeAssetGroupId,
                  scopeTeamId,
                  scope,
                  scopeMode,
                  planMode,
                  priorPlan);
            }
          });
    } else {
      engageOrchestratorNow(
          runId,
          agentSlug,
          objective,
          simulationId,
          scopeAssetGroupId,
          scopeTeamId,
          scope,
          scopeMode,
          planMode,
          priorPlan);
    }
  }

  /**
   * Performs the actual (transaction-free) orchestrator engagement and persists the returned
   * session handle. Best-effort: any failure is recorded on the run as {@code lastError} rather
   * than thrown, because the run is already committed as RUNNING and the durable execution / manual
   * resume is the backstop.
   */
  private void engageOrchestratorNow(
      String runId,
      String agentSlug,
      String objective,
      String simulationId,
      String scopeAssetGroupId,
      String scopeTeamId,
      List<AutonomousScopeTarget> scope,
      String scopeMode,
      boolean planMode,
      String priorPlan) {
    try {
      Map<String, Object> handle =
          xtmOneClient.startAutonomousRun(
              agentSlug,
              objective,
              runId,
              simulationId,
              scopeAssetGroupId,
              scopeTeamId,
              scope,
              scopeMode,
              planMode,
              priorPlan,
              openAEVConfig.getBaseUrl());
      String sessionId = handle != null ? asString(handle.get("session_id")) : null;
      String resolvedSlug = handle != null ? asString(handle.get("agent_slug")) : null;
      persistSessionHandle(runId, sessionId, resolvedSlug, null);
    } catch (Exception e) {
      log.warn("[Autonomous] Failed to engage orchestrator for run {}", runId, e);
      persistSessionHandle(
          runId, null, null, "Failed to engage the XTM One orchestrator: " + e.getMessage());
    }
  }

  /**
   * Persists the orchestrator session handle (and/or an engage error) onto the run in its own short
   * transaction, after the engage call has already returned. Uses a fresh load so it is safe to run
   * post-commit on a detached entity.
   */
  private void persistSessionHandle(
      String runId, String sessionId, String agentSlug, String error) {
    runRepository
        .findById(runId)
        .ifPresent(
            run -> {
              boolean changed = false;
              if (sessionId != null) {
                run.setXtmSessionId(sessionId);
                changed = true;
              }
              if (agentSlug != null) {
                run.setXtmAgentSlug(agentSlug);
                changed = true;
              }
              if (error != null) {
                run.setLastError(error);
                changed = true;
              }
              if (changed) {
                runRepository.save(run);
              }
            });
  }

  /**
   * Pauses a live run and the underlying chained simulation, keeping all state for resumption. The
   * XTM One orchestration is cancelled too so it stops burning decision cycles while paused; {@link
   * #resume} re-engages it (the upstream start is re-engaging, so a fresh cycle picks the run back
   * up from the current OpenAEV + shared state).
   */
  @Transactional(rollbackFor = Exception.class)
  public AutonomousRun pause(String runId) {
    requireFeature();
    AutonomousRun run = require(runId);
    transitionSimulation(run, ExerciseStatus.PAUSED);
    run.setStatus(AutonomousRunStatus.PAUSED);
    AutonomousRun saved = runRepository.save(run);
    eventService.append(
        runId, run.getSimulationId(), AutonomousEventType.STATUS, "Run paused", null, null);
    // Pause must NOT purge: a resume re-engages the SAME run and should continue from its existing
    // shared state + open work items, not start over.
    cancelOrchestratorAfterCommit(runId, "run paused by operator", false);
    return saved;
  }

  /** Resumes a paused run and its chained simulation, re-engaging the XTM One orchestrator. */
  @Transactional(rollbackFor = Exception.class)
  public AutonomousRun resume(String runId) {
    requireFeature();
    AutonomousRun run = require(runId);
    transitionSimulation(run, ExerciseStatus.RUNNING);
    run.setStatus(AutonomousRunStatus.RUNNING);
    run.setLastError(null);
    AutonomousRun saved = runRepository.save(run);
    eventService.append(
        runId, run.getSimulationId(), AutonomousEventType.STATUS, "Run resumed", null, null);
    engageOrchestratorAfterCommit(saved);
    return saved;
  }

  /**
   * Cancels a run and its chained simulation, halting the XTM One orchestrator. Terminal.
   *
   * <p>Deliberately resilient: moving the run to {@code CANCELED} must ALWAYS succeed, even when
   * the underlying simulation is already terminal (e.g. it was canceled a moment earlier and the
   * backend died before this run row was saved - the exact "simulation canceled but scenario still
   * running, now I can't stop or delete it" deadlock). So the simulation transition is best-effort
   * (never rolls the cancel back) and a run already terminal is an idempotent no-op.
   */
  @Transactional(rollbackFor = Exception.class)
  public AutonomousRun cancel(String runId) {
    requireFeature();
    AutonomousRun run = require(runId);
    if (run.getStatus() == AutonomousRunStatus.CANCELED) {
      return run;
    }
    // Best-effort, not strict: an already-CANCELED/FINISHED simulation must not block the run from
    // settling to CANCELED, otherwise a mid-cancel crash leaves the operator permanently stuck.
    transitionSimulationQuietly(run, ExerciseStatus.CANCELED);
    run.setStatus(AutonomousRunStatus.CANCELED);
    AutonomousRun saved = runRepository.save(run);
    eventService.append(
        runId, run.getSimulationId(), AutonomousEventType.STATUS, "Run canceled", null, null);
    // Stop purges the run's coordination state so a later restart starts clean (re-asks the
    // operator
    // for scope instead of re-reading a stale resolved target / open exploitation task).
    cancelOrchestratorAfterCommit(runId, "run canceled by operator", true);
    return saved;
  }

  /**
   * Restarts a terminal run <b>in place</b>: reuses the SAME scenario, tears down the previous
   * simulation (attack-path rows included) and the run's decision timeline + steering directives,
   * provisions a fresh simulation from that scenario, and resets the run to {@code CREATED}. The
   * caller then {@link #start}s it again. This keeps the invariant "one scenario == one run == one
   * live simulation" instead of spawning a brand-new scenario on every restart, and gives the
   * cockpit (overview, attack-path graph, reasoning panel) a clean slate to animate from scratch.
   */
  @Transactional(rollbackFor = Exception.class)
  public AutonomousRun restart(String runId) {
    requireFeature();
    AutonomousRun run = require(runId);
    if (run.getStatus() != AutonomousRunStatus.COMPLETED
        && run.getStatus() != AutonomousRunStatus.FAILED
        && run.getStatus() != AutonomousRunStatus.CANCELED) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Run can only be restarted once it has stopped");
    }
    // Stop the previous XTM One orchestration before tearing its simulation down, so a lingering
    // decision cycle can't dispatch injects against the simulation we are about to delete. The
    // subsequent start() re-engages the run cleanly (upstream start resets the same execution).
    // Restart is a full reset: purge the run's coordination state so the fresh simulation is not
    // driven by the previous run's assumptions.
    cancelOrchestratorAfterCommit(runId, "run restarted by operator", true);
    // Tear the previous simulation down (attack-path executions + findings included) so the graph
    // and posture reset to empty rather than lingering from the previous run.
    if (hasText(run.getSimulationId())) {
      exerciseService.deleteById(run.getSimulationId());
    }
    // Fresh simulation from the SAME scenario - no new scenario is ever provisioned on restart.
    Scenario scenario = scenarioService.scenario(run.getScenarioId());
    // Clear the previous run's mirrored scenario steps first: the scenario workflow doubles as the
    // seed the fresh simulation is copied from, so leaving them would make the restarted simulation
    // start non-empty and duplicate the attack path. Only the AI-mirrored steps are removed
    // (tracked
    // in stepMirror), so any caller-authored steps of an advanced scenario are preserved.
    if (run.getStepMirror() != null && !run.getStepMirror().isEmpty()) {
      workflowService.deleteScenarioMirrorSteps(run.getStepMirror().values());
      run.setStepMirror(new HashMap<>());
    }
    // Re-assert keep-alive on the scenario workflow before relaunching, so the restarted simulation
    // parks empty and awaits the orchestrator exactly like the first launch.
    markWorkflowKeepAlive(run.getScenarioId());
    Exercise simulation =
        scenarioToExerciseService.toExercise(
            scenario, now().truncatedTo(MINUTES).plus(1, MINUTES), true);
    try {
      // Mirror create(): a plan-mode restart provisions the simulation TEMPLATE workflow only (no
      // RUN), so the restarted plan can be re-authored without executing; a live restart starts the
      // RUN workflow as usual.
      if (run.isPlanMode()) {
        workflowService.provisionSimulationTemplateWorkflow(run.getScenarioId(), simulation);
      } else {
        workflowService.startWorkflowByScenarioIdAndSimulation(run.getScenarioId(), simulation);
      }
    } catch (ChainingException e) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Failed to start the restarted simulation: " + e.getMessage(), e);
    }
    // Full reset of the run's decision history + steering so the cockpit starts clean.
    directiveRepository.deleteByRunId(runId);
    eventService.deleteByRun(runId);
    run.setSimulationId(simulation.getId());
    run.setStatus(AutonomousRunStatus.CREATED);
    run.setLastError(null);
    run.setXtmSessionId(null);
    AutonomousRun saved = runRepository.save(run);
    eventService.append(
        saved.getId(),
        simulation.getId(),
        AutonomousEventType.STATUS,
        "Run restarted",
        "Autonomous attack-path run restarted; a fresh simulation was provisioned from the same "
            + "scenario.",
        null);
    return saved;
  }

  /**
   * Promotes a completed dry-run to a real, executing run <b>in place</b>. Same machinery as {@link
   * #restart}: cancel the planning orchestration, tear down the (non-executing) plan simulation and
   * the run's mirrored scenario steps + decision timeline, provision a FRESH simulation and start
   * its RUN workflow so the chaining engine can dispatch. Unlike restart, it clears {@code
   * planMode} (the run is now live) but KEEPS {@code planGuidance}, which {@link #start} then hands
   * to the orchestrator as the prior plan to follow-while-adapting. The scenario's resolved scope
   * survives (the fresh simulation is copied from it), so the live run starts scoped but with an
   * empty attack path. The caller then {@link #start}s it again.
   */
  @Transactional(rollbackFor = Exception.class)
  public AutonomousRun promoteToRealRun(String runId) {
    requireFeature();
    AutonomousRun run = require(runId);
    if (!run.isPlanMode()) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Only a dry-run plan can be promoted to a real run");
    }
    if (run.getStatus() != AutonomousRunStatus.PLANNED
        && run.getStatus() != AutonomousRunStatus.FAILED
        && run.getStatus() != AutonomousRunStatus.CANCELED) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "A plan can only be run for real once planning has settled");
    }
    // Stop the planning orchestration and purge its coordination state so the live run does not
    // inherit the plan cycle's assumptions.
    cancelOrchestratorAfterCommit(runId, "plan promoted to a real run by operator", true);
    if (hasText(run.getSimulationId())) {
      exerciseService.deleteById(run.getSimulationId());
    }
    Scenario scenario = scenarioService.scenario(run.getScenarioId());
    // Drop the mirrored plan steps so the promoted simulation starts with an empty attack path and
    // the orchestrator rebuilds it live (guided by planGuidance) rather than replaying the plan.
    if (run.getStepMirror() != null && !run.getStepMirror().isEmpty()) {
      workflowService.deleteScenarioMirrorSteps(run.getStepMirror().values());
      run.setStepMirror(new HashMap<>());
    }
    markWorkflowKeepAlive(run.getScenarioId());
    Exercise simulation =
        scenarioToExerciseService.toExercise(
            scenario, now().truncatedTo(MINUTES).plus(1, MINUTES), true);
    try {
      workflowService.startWorkflowByScenarioIdAndSimulation(run.getScenarioId(), simulation);
    } catch (ChainingException e) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Failed to start the promoted simulation: " + e.getMessage(), e);
    }
    directiveRepository.deleteByRunId(runId);
    eventService.deleteByRun(runId);
    run.setSimulationId(simulation.getId());
    run.setPlanMode(false);
    run.setStatus(AutonomousRunStatus.CREATED);
    run.setLastError(null);
    run.setXtmSessionId(null);
    AutonomousRun saved = runRepository.save(run);
    eventService.append(
        saved.getId(),
        simulation.getId(),
        AutonomousEventType.STATUS,
        "Plan promoted to live run",
        "The dry-run plan was promoted to a real run; a fresh executing simulation was provisioned."
            + " The orchestrator will follow the plan as closely as possible while adapting to live"
            + " findings.",
        null);
    return saved;
  }

  /**
   * Tears down the autonomous run owning {@code scenarioId} together with its single underlying
   * simulation (attack-path rows included) and its timeline/directives. An autonomous scenario and
   * its simulation are one unit, so deleting the scenario must delete the simulation too -
   * otherwise an orphan run keeps driving a simulation whose scenario is gone.
   *
   * <p>Deliberately a best-effort no-op for manual scenarios (and when the preview feature is off),
   * so the generic scenario-delete endpoint can call it unconditionally. A still-active run
   * (created / running / paused / waiting-input) is refused with 409: the operator must stop it
   * first, mirroring the UI's disabled Delete entry.
   */
  @Transactional(rollbackFor = Exception.class)
  public void deleteForScenario(String scenarioId) {
    if (!previewFeatureService.isAutonomousAttackPathEnabled()) {
      return;
    }
    AutonomousRun run = runRepository.findByScenarioId(scenarioId).orElse(null);
    if (run == null) {
      return;
    }
    // Hard-link first: if the simulation already died (canceled/finished/deleted) the run must be
    // treated as terminal so a stale "still running" status can't wrongly block the delete.
    run = reconcileWithSimulation(run);
    AutonomousRunStatus status = run.getStatus();
    if (status == AutonomousRunStatus.CREATED
        || status == AutonomousRunStatus.RUNNING
        || status == AutonomousRunStatus.PAUSED
        || status == AutonomousRunStatus.WAITING_INPUT) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Stop the autonomous run before deleting its scenario");
    }
    tearDownRun(run);
  }

  /**
   * Best-effort teardown of the autonomous run owning {@code scenarioId}, used by the <b>bulk</b>
   * scenario-delete path (which resolves and deletes many scenarios in independent chunked
   * transactions and therefore cannot rely on the single-delete endpoint calling {@link
   * #deleteForScenario}). Unlike {@link #deleteForScenario} it does <b>not</b> refuse an active run
   * with 409: the operator has already committed to deleting these scenarios, so a still-live run
   * is force-canceled (orchestrator halted + coordination state purged) rather than left as an
   * orphan {@code autonomous_runs} row whose XTM One orchestration keeps running against a deleted
   * simulation. A no-op for manual scenarios and when the preview feature is off, so the bulk path
   * can call it unconditionally per id.
   */
  @Transactional(rollbackFor = Exception.class)
  public void deleteForScenarioForce(String scenarioId) {
    if (!previewFeatureService.isAutonomousAttackPathEnabled()) {
      return;
    }
    AutonomousRun run = runRepository.findByScenarioId(scenarioId).orElse(null);
    if (run == null) {
      return;
    }
    tearDownRun(run);
  }

  /**
   * Tears an autonomous run down together with its underlying simulation, decision timeline, and
   * steering directives, and halts the XTM One orchestration. Shared by both scenario-delete paths
   * (single and bulk) so an autonomous run is cleaned up the same way however its scenario is
   * deleted - never leaving an orphaned run row or a self-resuming durable execution behind.
   */
  private void tearDownRun(AutonomousRun run) {
    // Halt the XTM One orchestration first: once the run row is gone OpenAEV can no longer be
    // driven, but a still-live durable execution would keep self-resuming and dispatching injects
    // against the deleted simulation. Fired after commit (the run id is captured now) so the
    // upstream cancel resolves the same execution by its stable dedup key. Purge so no orphaned
    // shared state / work items linger after the scenario and its simulation are gone.
    String runId = run.getId();
    cancelOrchestratorAfterCommit(runId, "autonomous scenario deleted", true);
    if (hasText(run.getSimulationId())) {
      exerciseService.deleteById(run.getSimulationId());
    }
    directiveRepository.deleteByRunId(runId);
    eventService.deleteByRun(runId);
    runRepository.delete(run);
  }

  // endregion

  // region orchestrator callbacks

  /** Appends a timeline event pushed by the orchestrator and nudges the live view. */
  @Transactional(rollbackFor = Exception.class)
  public AutonomousEvent recordEvent(
      String runId, AutonomousEventType type, String title, String content, String data) {
    requireFeature();
    AutonomousRun run = require(runId);
    // A proof of exploitation is only valid when it is backed by at least one finding: the
    // orchestrator must pass the substantiating finding(s) in the event's structured data as a
    // non-empty "findings" array. This is the platform-side guarantee behind "no proof without a
    // finding" - the UI then renders those linked findings on the proof card / dialog.
    if (type == AutonomousEventType.PROOF && !hasLinkedFinding(data)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "A proof of exploitation must reference at least one finding: pass a non-empty "
              + "\"findings\" array in the event data. There is no valid proof without an "
              + "associated finding.");
    }
    return eventService.append(runId, run.getSimulationId(), type, title, content, data);
  }

  /**
   * True when {@code data} is a JSON object carrying a non-empty {@code findings} array - the
   * substantiating findings a {@code PROOF} event must be backed by. Malformed or absent data is
   * treated as "no linked finding" so a proof without evidence is rejected rather than trusted.
   */
  private boolean hasLinkedFinding(String data) {
    if (!hasText(data)) {
      return false;
    }
    try {
      JsonNode findings = objectMapper.readTree(data).get("findings");
      return findings != null && findings.isArray() && !findings.isEmpty();
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Applies a run-status transition pushed by the orchestrator (waiting-input, completed,
   * failed...).
   */
  @Transactional(rollbackFor = Exception.class)
  public AutonomousRun updateStatus(
      String runId, AutonomousRunStatus status, String lastError, String title, String content) {
    requireFeature();
    AutonomousRun run = require(runId);
    run.setStatus(status);
    if (lastError != null) {
      run.setLastError(lastError);
    }
    // A dry-run that reaches PLANNED captures the orchestrator's plan summary as guidance, so
    // promoting it to a real run can hand the plan to the live orchestrator ("follow but adapt").
    if (status == AutonomousRunStatus.PLANNED && hasText(content)) {
      run.setPlanGuidance(content);
    }
    // Reflect a terminal orchestrator decision onto the chained simulation so both stay consistent.
    // PLANNED is settled but not terminal (the plan sim never ran), so it never touches the sim.
    if (status == AutonomousRunStatus.COMPLETED || status == AutonomousRunStatus.FAILED) {
      transitionSimulationQuietly(run, ExerciseStatus.FINISHED);
    }
    AutonomousRun saved = runRepository.save(run);
    eventService.append(
        runId,
        run.getSimulationId(),
        AutonomousEventType.STATUS,
        title != null ? title : "Status: " + status,
        content,
        null);
    return saved;
  }

  /**
   * Returns the run's pending steering directives and atomically marks them consumed. Called by the
   * orchestrator at the start of each decision cycle: this is the live-steering read path.
   */
  @Transactional(rollbackFor = Exception.class)
  public List<AutonomousDirective> consumePendingDirectives(String runId) {
    requireFeature();
    require(runId);
    List<AutonomousDirective> pending =
        directiveRepository.findByRunIdAndStatusOrderByCreatedAtAsc(
            runId, AutonomousDirectiveStatus.PENDING);
    Instant consumedAt = Instant.now();
    for (AutonomousDirective directive : pending) {
      directive.setStatus(AutonomousDirectiveStatus.CONSUMED);
      directive.setConsumedAt(consumedAt);
    }
    directiveRepository.saveAll(pending);
    if (!pending.isEmpty()) {
      AutonomousRun run = require(runId);
      eventService.append(
          runId,
          run.getSimulationId(),
          AutonomousEventType.DIRECTIVE,
          "Directives applied",
          pending.size() + " operator directive(s) consumed by the orchestrator.",
          null);
    }
    return pending;
  }

  // endregion

  // region steering

  /** Queues a real-time operator steering directive for the next decision cycle. */
  @Transactional(rollbackFor = Exception.class)
  public AutonomousDirective addDirective(String runId, String content) {
    requireFeature();
    AutonomousRun run = require(runId);
    AutonomousDirective directive = new AutonomousDirective();
    directive.setRunId(runId);
    directive.setContent(content);
    directive.setStatus(AutonomousDirectiveStatus.PENDING);
    AutonomousDirective saved = directiveRepository.save(directive);
    eventService.append(
        runId,
        run.getSimulationId(),
        AutonomousEventType.DIRECTIVE,
        "Operator directive queued",
        content,
        null);
    // Re-arm the orchestrator so it picks up the directive now, not only at its next scheduled
    // re-check - crucial when the run is parked in WAITING_INPUT after asking the operator a
    // question. Fired after commit so the orchestrator can never consume before the row is visible.
    wakeOrchestratorAfterCommit(runId, "operator directive queued");
    return saved;
  }

  /**
   * Registers a best-effort, after-commit wake to the XTM One orchestrator for {@code runId}. Runs
   * only once the surrounding transaction commits (so the directive is visible to the
   * orchestrator's consume-directives read); a synchronous fallback is used when there is no active
   * transaction.
   */
  private void wakeOrchestratorAfterCommit(String runId, String reason) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              xtmOneClient.wakeAutonomousRun(runId, reason);
            }
          });
    } else {
      xtmOneClient.wakeAutonomousRun(runId, reason);
    }
  }

  /**
   * Registers a best-effort, after-commit cancel of the XTM One orchestration for {@code runId}.
   * Deferred to {@code afterCommit} so the orchestrator loop is only stopped once the OpenAEV-side
   * stop / delete / restart has durably committed (never on a rolled-back transaction); a
   * synchronous fallback covers the no-active-transaction case. Best-effort: a transport failure is
   * swallowed by the client and the adapter's own self-guard is the backstop.
   */
  private void cancelOrchestratorAfterCommit(String runId, String reason, boolean purge) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              xtmOneClient.cancelAutonomousRun(runId, reason, purge);
            }
          });
    } else {
      xtmOneClient.cancelAutonomousRun(runId, reason, purge);
    }
  }

  /**
   * Applies a live scope / rate-limit / safe-mode edit to the run's RUN workflow(s) without
   * stopping it. The chaining engine reads the updated scope on its next decision cycle, so a
   * denylist entry added here walls off the matching assets immediately.
   */
  @Transactional(rollbackFor = Exception.class)
  public List<Workflow> applyLiveConfiguration(String runId, WorkflowConfigurationInput input) {
    requireFeature();
    AutonomousRun run = require(runId);
    List<Workflow> updated =
        workflowService.updateRunWorkflowConfiguration(run.getSimulationId(), input);
    eventService.append(
        runId,
        run.getSimulationId(),
        AutonomousEventType.DIRECTIVE,
        "Live configuration updated",
        "Scope / rate-limit / safe-mode edited on the running workflow.",
        null);
    return updated;
  }

  /**
   * Records the orchestrator's resolved scope onto the run: the given targets REPLACE the run's
   * ALLOWLIST perimeter on both the scenario template and the live simulation workflow(s), so the
   * scope is enforced and visible in the Scope tab (and the empty-allowlist warning clears) instead
   * of living only in the AI's reasoning. Other workflow configuration (timeout / rate-limit /
   * keep-alive / denylist) is preserved. The run's own scope projection is updated too so a restart
   * or reconciliation sees the same perimeter. Team members in scope are enabled on the simulation
   * so any human-targeted step that inherits the scope is deliverable.
   */
  @Transactional(rollbackFor = Exception.class)
  public AutonomousRun setRunScope(String runId, List<AutonomousScopeTarget> targets) {
    requireFeature();
    AutonomousRun run = require(runId);
    List<AutonomousScopeTarget> scope = targets != null ? new ArrayList<>(targets) : List.of();
    workflowService.writeAllowlistScope(
        run.getScenarioId(), run.getSimulationId(), toAllowlistScopeInputs(scope), true);
    run.setScope(scope);
    run.setScopeAssetGroupId(firstScopeIdOfType(scope, "ASSETS_GROUPS"));
    run.setScopeTeamId(firstScopeIdOfType(scope, "TEAMS"));
    AutonomousRun saved = runRepository.save(run);
    enableTargetedTeamMembers(
        run.getSimulationId(),
        scope.stream()
            .filter(t -> "TEAMS".equals(t.getType()))
            .map(AutonomousScopeTarget::getId)
            .toList());
    eventService.append(
        runId,
        run.getSimulationId(),
        AutonomousEventType.DECISION,
        "Scope set",
        scope.isEmpty()
            ? "Scope cleared; the run now has no restricted perimeter."
            : "Resolved scope recorded on the run ("
                + scope.size()
                + " target(s) in the allowlist).",
        null);
    return saved;
  }

  /**
   * Reads the run's live, authoritative scope back from its workflow so the orchestrator can see
   * the real perimeter before acting - not the start-time snapshot it was handed, and not just its
   * own in-memory reasoning. Returns both the allow-list (what may be attacked) and the deny-list
   * (carve-outs that always win), across every source (assets, asset groups, teams, persons, and
   * manual IP / CIDR / hostname / CSV rules), with resolved display names for entities. The
   * scenario template workflow is the canonical source (it is what a restart re-seeds from and what
   * the Scope tab shows); the live simulation run is the fallback when no template is resolvable.
   */
  @Transactional(readOnly = true)
  public AutonomousScopeView getRunScopeView(String runId) {
    requireFeature();
    AutonomousRun run = require(runId);
    List<WorkflowScopeRule> rules = readScopeRules(run);
    Map<String, String> names = resolveScopeNames(rules);
    List<AutonomousScopeEntry> allow = new ArrayList<>();
    List<AutonomousScopeEntry> deny = new ArrayList<>();
    for (WorkflowScopeRule rule : rules) {
      if (rule == null || rule.getRuleSource() == null || rule.getSelectedMode() == null) {
        continue;
      }
      String value = rule.getRuleValue();
      AutonomousScopeEntry entry =
          new AutonomousScopeEntry(
              rule.getRuleSource().name(),
              scopeTypeForSource(rule.getRuleSource()),
              value,
              names.getOrDefault(nameKey(rule.getRuleSource(), value), value));
      if (rule.getSelectedMode() == ScopeRuleSelectedMode.DENYLIST) {
        deny.add(entry);
      } else {
        allow.add(entry);
      }
    }
    return new AutonomousScopeView(runId, allow, deny);
  }

  private List<WorkflowScopeRule> readScopeRules(AutonomousRun run) {
    if (hasText(run.getScenarioId())) {
      try {
        List<WorkflowScopeRule> fromTemplate =
            workflowService
                .findWorkflowTemplateByScenarioId(run.getScenarioId())
                .map(w -> new ArrayList<>(w.getWorkflowScopeRules()))
                .orElse(null);
        if (fromTemplate != null) {
          return fromTemplate;
        }
      } catch (ChainingException e) {
        log.warn("[Autonomous] Could not read scenario {} scope", run.getScenarioId(), e);
      }
    }
    if (hasText(run.getSimulationId())) {
      return workflowService.findWorkflowRunBySimulationId(run.getSimulationId()).stream()
          .flatMap(w -> w.getWorkflowScopeRules().stream())
          .collect(Collectors.toList());
    }
    return List.of();
  }

  private String scopeTypeForSource(ScopeRuleSource source) {
    return switch (source) {
      case ASSET -> "ASSETS";
      case ASSET_GROUP -> "ASSETS_GROUPS";
      case TEAM -> "TEAMS";
      case PLAYER -> "PLAYERS";
      case MANUAL, CSV -> "MANUAL";
    };
  }

  private String nameKey(ScopeRuleSource source, String value) {
    return source.name() + "|" + value;
  }

  /** Batch-resolves entity display names per kind so scope reads never issue a query per rule. */
  private Map<String, String> resolveScopeNames(List<WorkflowScopeRule> rules) {
    Map<String, String> names = new HashMap<>();
    List<String> assetIds = idsForSource(rules, ScopeRuleSource.ASSET);
    List<String> groupIds = idsForSource(rules, ScopeRuleSource.ASSET_GROUP);
    List<String> teamIds = idsForSource(rules, ScopeRuleSource.TEAM);
    List<String> playerIds = idsForSource(rules, ScopeRuleSource.PLAYER);
    if (!assetIds.isEmpty()) {
      for (Endpoint endpoint : endpointService.endpoints(assetIds)) {
        names.put(nameKey(ScopeRuleSource.ASSET, endpoint.getId()), endpoint.getName());
      }
    }
    if (!groupIds.isEmpty()) {
      for (AssetGroup group : assetGroupRepository.findAllById(groupIds)) {
        names.put(nameKey(ScopeRuleSource.ASSET_GROUP, group.getId()), group.getName());
      }
    }
    if (!teamIds.isEmpty()) {
      for (Team team : teamRepository.findAllById(teamIds)) {
        names.put(nameKey(ScopeRuleSource.TEAM, team.getId()), team.getName());
      }
    }
    if (!playerIds.isEmpty()) {
      for (User user : userRepository.findAllById(playerIds)) {
        names.put(nameKey(ScopeRuleSource.PLAYER, user.getId()), scopePlayerName(user));
      }
    }
    return names;
  }

  private List<String> idsForSource(List<WorkflowScopeRule> rules, ScopeRuleSource source) {
    return rules.stream()
        .filter(r -> r != null && r.getRuleSource() == source && hasText(r.getRuleValue()))
        .map(WorkflowScopeRule::getRuleValue)
        .distinct()
        .toList();
  }

  private String scopePlayerName(User user) {
    String name = user.getName();
    if (name != null && !name.isBlank()) {
      return name.trim();
    }
    return user.getEmail();
  }

  // endregion

  // region attack-path authoring

  /**
   * Appends a chained inject step to the run's live attack path and returns the created step
   * template id so the orchestrator can chain the next step onto it. This is the ONLY sanctioned
   * way for the AI orchestrator to build the attack path: every technique becomes a workflow step
   * that executes through the chaining engine and therefore renders in the live attack-path map.
   * Standalone injects and atomic tests (which run outside the engine and never populate the map)
   * are denied to the orchestrator on the XTM One side.
   *
   * @param runId the autonomous run
   * @param injectInput the inject to wrap as a chained step
   * @param parentStepTemplateId optional step template id this step depends on (null for a root)
   * @return the id of the created step template
   */
  public String appendAttackPathStep(
      String runId, InjectInput injectInput, String parentStepTemplateId) {
    return appendAttackPathStep(runId, injectInput, parentStepTemplateId, null);
  }

  /**
   * Finding-driven overload of {@link #appendAttackPathStep(String, InjectInput, String)}. The step
   * is authored with a finding {@code trigger} (a filter tree and/or {@code MAPPER} bindings) so it
   * readies off findings and consumes their values - the way a hand-built chained scenario works,
   * and the way the attack path draws itself. A {@code null} trigger with no parent is a SEED step
   * that readies immediately against the run scope.
   */
  @Transactional(rollbackFor = Exception.class)
  public String appendAttackPathStep(
      String runId,
      InjectInput injectInput,
      String parentStepTemplateId,
      AutonomousStepTrigger trigger) {
    requireFeature();
    AutonomousRun run = require(runId);
    if (!hasText(run.getSimulationId())) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "The autonomous run has no live simulation to build steps on");
    }
    List<ConditionCreateInput> triggerConditions = toTriggerConditions(trigger);
    String stepTemplateId;
    try {
      stepTemplateId =
          workflowService.appendChainedStep(
              run.getSimulationId(), injectInput, parentStepTemplateId, triggerConditions);
    } catch (ChainingException e) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Failed to author the attack-path step: " + e.getMessage(), e);
    }
    // A human-in-the-loop inject (email, SMS, ...) delivers only to the players ENABLED on the
    // simulation (exercise_teams_users), not merely to team members. The orchestrator can
    // legitimately
    // target a team directly - an operator-pre-selected audience, or one it built - without routing
    // through ensure_openaev_target_team, and that team's members are then NOT enabled on this
    // simulation, so the step ERRORs with "Email needs at least one user". Enable every targeted
    // team's members here so any team-targeted step is deliverable regardless of how it was wired.
    enableTargetedTeamMembers(run.getSimulationId(), injectInput.getTeams());
    // Mirror the step onto the run's SCENARIO so the scenario carries the attack path and can be
    // exported/reproduced (the executing copy lives on the simulation; this twin never runs).
    mirrorStepOntoScenario(
        run, injectInput, parentStepTemplateId, stepTemplateId, triggerConditions);
    boolean findingDriven = !triggerConditions.isEmpty();
    eventService.append(
        runId,
        run.getSimulationId(),
        AutonomousEventType.TOOL_ACTION,
        "Attack-path step authored",
        "A chained step was added to the live attack path"
            + (findingDriven
                ? " (fires on a finding and consumes its values)."
                : hasText(parentStepTemplateId)
                    ? " (depends on a previous step)."
                    : " (seed step - readies immediately against the scope)."),
        null);
    return stepTemplateId;
  }

  /**
   * Translates the orchestrator-facing {@link AutonomousStepTrigger} into the engine's condition
   * vocabulary: a finding-trigger filter tree (an AND/OR root with one leaf per predicate) plus one
   * {@code MAPPER} condition per input binding. Readiness matches on the finding's primitive
   * key-type, so the trigger fires whenever an upstream step emits a matching finding, once per
   * value, and the mappers bind those values into this step's inject inputs. Returns an empty list
   * for a {@code null}/empty trigger (a seed or DEPEND_ON-only step).
   *
   * <p>If mappings are given without explicit filters, an {@code IS_NOT_NULL} filter is synthesised
   * per distinct mapped key-type, so the step fires as soon as the finding values it needs exist.
   */
  private List<ConditionCreateInput> toTriggerConditions(AutonomousStepTrigger trigger) {
    List<ConditionCreateInput> conditions = new ArrayList<>();
    if (trigger == null) {
      return conditions;
    }
    List<AutonomousTriggerFilter> filters =
        trigger.getFilters() == null ? List.of() : trigger.getFilters();
    List<AutonomousInputMapping> mappings =
        trigger.getMappings() == null ? List.of() : trigger.getMappings();

    // Build the leaves: explicit filters, or a synthesised IS_NOT_NULL per mapped key-type.
    List<ConditionCreateInput> leaves = new ArrayList<>();
    for (AutonomousTriggerFilter filter : filters) {
      if (filter == null || filter.getKeyType() == null) {
        continue;
      }
      ConditionType op =
          filter.getOperator() == null ? ConditionType.IS_NOT_NULL : filter.getOperator();
      leaves.add(
          ConditionCreateInput.builder()
              .temporaryId(UUID.randomUUID().toString())
              .temporaryIdConditionParent(TRIGGER_ROOT_TMP_ID)
              .type(op)
              .keyTypes(List.of(filter.getKeyType()))
              .value(filter.getValue())
              .caseSensitive(filter.getCaseSensitive() == null || filter.getCaseSensitive())
              .build());
    }
    if (leaves.isEmpty() && !mappings.isEmpty()) {
      mappings.stream()
          .map(AutonomousInputMapping::getKeyType)
          .filter(Objects::nonNull)
          .distinct()
          .forEach(
              keyType ->
                  leaves.add(
                      ConditionCreateInput.builder()
                          .temporaryId(UUID.randomUUID().toString())
                          .temporaryIdConditionParent(TRIGGER_ROOT_TMP_ID)
                          .type(ConditionType.IS_NOT_NULL)
                          .keyTypes(List.of(keyType))
                          .build()));
    }
    if (!leaves.isEmpty()) {
      ConditionType rootType =
          "OR".equalsIgnoreCase(trigger.getMatch()) ? ConditionType.OR : ConditionType.AND;
      conditions.add(
          ConditionCreateInput.builder().temporaryId(TRIGGER_ROOT_TMP_ID).type(rootType).build());
      conditions.addAll(leaves);
    }

    // MAPPER conditions bind finding values into this step's inject inputs (default GLOBAL pool).
    for (AutonomousInputMapping mapping : mappings) {
      if (mapping == null || mapping.getKeyType() == null || !hasText(mapping.getInputKey())) {
        continue;
      }
      conditions.add(
          ConditionCreateInput.builder()
              .temporaryId(UUID.randomUUID().toString())
              .type(ConditionType.MAPPER)
              .mappingType(
                  mapping.getMappingType() == null ? MappingType.GLOBAL : mapping.getMappingType())
              .key(mapping.getInputKey())
              .keyTypes(List.of(mapping.getKeyType()))
              .build());
    }
    return conditions;
  }

  /**
   * Updates an existing chained step's inject definition IN PLACE - same step template id, same
   * DEPEND_ON parent - so the orchestrator edits a step it already authored (payload / target /
   * injector contract / title) instead of minting a duplicate. The scenario mirror twin is updated
   * in lock-step so the exported attack path stays faithful, and any newly targeted team's members
   * are re-enabled so a re-targeted human step stays deliverable.
   *
   * @param runId the autonomous run
   * @param stepTemplateId the step template to update (from the attack-path state read)
   * @param injectInput the new inject definition
   * @return the (unchanged) step template id
   */
  @Transactional(rollbackFor = Exception.class)
  public String updateAttackPathStep(String runId, String stepTemplateId, InjectInput injectInput) {
    requireFeature();
    AutonomousRun run = require(runId);
    if (!hasText(run.getSimulationId())) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "The autonomous run has no live simulation to update steps on");
    }
    try {
      workflowService.updateChainedStep(stepTemplateId, injectInput);
    } catch (ChainingException e) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Failed to update the attack-path step: " + e.getMessage(), e);
    }
    // Keep the scenario mirror twin in lock-step so the exported attack path reflects the edit.
    Map<String, String> mirror = run.getStepMirror();
    String scenarioStepId = mirror == null ? null : mirror.get(stepTemplateId);
    if (hasText(scenarioStepId)) {
      try {
        workflowService.updateChainedStep(scenarioStepId, injectInput);
      } catch (Exception e) {
        log.warn(
            "[Autonomous] Failed to update scenario mirror step {} for run {}: {}",
            scenarioStepId,
            runId,
            e.getMessage());
      }
    }
    enableTargetedTeamMembers(run.getSimulationId(), injectInput.getTeams());
    eventService.append(
        runId,
        run.getSimulationId(),
        AutonomousEventType.TOOL_ACTION,
        "Attack-path step updated",
        "An existing chained step was updated in place (no new step created).",
        null);
    return stepTemplateId;
  }

  /**
   * Mirrors a just-authored simulation step onto the run's scenario workflow so the scenario is a
   * faithful, exportable copy of the attack path (the simulation owns the executing steps; the
   * scenario twin never runs). The orchestrator only knows simulation step ids, so the run keeps a
   * sim->scenario step-id map to reattach a step's {@code DEPEND_ON} to the correct scenario parent
   * and preserve kill-chain ordering. This is best-effort: a mirror failure must never fail the
   * orchestrator's author call (the executing simulation step already succeeded), so it is logged
   * and swallowed rather than propagated.
   */
  private void mirrorStepOntoScenario(
      AutonomousRun run,
      InjectInput injectInput,
      String parentSimStepId,
      String simStepId,
      List<ConditionCreateInput> triggerConditions) {
    String scenarioId = run.getScenarioId();
    if (!hasText(scenarioId)) {
      return;
    }
    Map<String, String> mirror =
        run.getStepMirror() == null ? new HashMap<>() : new HashMap<>(run.getStepMirror());
    String parentScenarioStepId = hasText(parentSimStepId) ? mirror.get(parentSimStepId) : null;
    try {
      String scenarioStepId =
          workflowService.appendChainedStepToScenario(
              scenarioId, injectInput, parentScenarioStepId, triggerConditions);
      mirror.put(simStepId, scenarioStepId);
      run.setStepMirror(mirror);
      runRepository.save(run);
    } catch (Exception e) {
      log.warn(
          "[Autonomous] Failed to mirror attack-path step onto scenario {} for run {}: {}",
          scenarioId,
          run.getId(),
          e.getMessage());
    }
  }

  /**
   * Enables, on the run's simulation, the members of every team a just-authored step targets so a
   * human-in-the-loop inject (email, SMS, credential harvesting, ...) can actually reach them. The
   * email/SMS executor resolves recipients from {@code exercise_teams_users} (players ENABLED on
   * the simulation), not from raw team membership, so targeting a team whose members were never
   * enabled fails with "Email needs at least one user". This makes any team-targeted step
   * deliverable regardless of how the orchestrator wired the team (a pre-selected audience, a
   * wrapper it built, or {@code ensure_openaev_target_team}). Best-effort and idempotent: {@code
   * enablePlayers} skips already-enabled links, and a team-less (asset-only) step targets nothing
   * here.
   */
  private void enableTargetedTeamMembers(String simulationId, List<String> teamIds) {
    if (teamIds == null || teamIds.isEmpty() || !hasText(simulationId)) {
      return;
    }
    Exercise simulation = exerciseRepository.findById(simulationId).orElse(null);
    if (simulation == null) {
      return;
    }
    for (String teamId : teamIds.stream().filter(id -> hasText(id)).distinct().toList()) {
      Team team = teamRepository.findById(teamId).orElse(null);
      if (team == null) {
        continue;
      }
      List<String> memberIds = team.getUsers().stream().map(User::getId).distinct().toList();
      if (memberIds.isEmpty()) {
        continue;
      }
      boolean onSimulation = simulation.getTeams().stream().anyMatch(t -> teamId.equals(t.getId()));
      if (!onSimulation) {
        team.getExercises().add(simulation);
        teamRepository.save(team);
      }
      exerciseService.enablePlayers(simulationId, team, memberIds);
    }
  }

  /**
   * Re-evaluates the run's live workflow so freshly authored steps ready and execute now. The
   * orchestrator calls this after appending one or more steps in a decision cycle.
   */
  @Transactional(rollbackFor = Exception.class)
  public void evaluateAttackPath(String runId) {
    requireFeature();
    AutonomousRun run = require(runId);
    if (!hasText(run.getSimulationId())) {
      return;
    }
    // Belt-and-suspenders for dry-run: a plan run never starts a RUN workflow, so there is nothing
    // to ready here anyway, but guard explicitly so a stray evaluate call can never dispatch an
    // inject while planning.
    if (run.isPlanMode()) {
      return;
    }
    // Re-evaluate the run's live workflow(s) so freshly authored step templates ready and execute
    // now instead of waiting for an in-flight step. Done here (cross-bean to WorkflowService)
    // rather
    // than through a WorkflowService helper so we never self-invoke its @Transactional evaluator.
    try {
      for (Workflow runWorkflow :
          workflowService.findWorkflowRunBySimulationId(run.getSimulationId())) {
        workflowService.saveWorkflowRun(workflowService.evaluateWorkflowProgress(runWorkflow));
      }
    } catch (ChainingException e) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Failed to evaluate the attack path: " + e.getMessage(), e);
    }
  }

  /**
   * Live snapshot of every step already authored on the run's attack path: each backing inject, its
   * current execution status, and its execution traces. This is the read path the orchestrator
   * polls before authoring anything, so it can see what it has ALREADY built (and not duplicate it)
   * and what each step actually did (its traces) before deciding the next move.
   */
  @Transactional(readOnly = true)
  public List<AutonomousAttackPathStepState> attackPathState(String runId) {
    requireFeature();
    AutonomousRun run = require(runId);
    if (!hasText(run.getSimulationId())) {
      return List.of();
    }
    // Build the snapshot from the simulation TEMPLATE workflow's steps (the STABLE authoring
    // handles the orchestrator built) rather than from materialized injects, so the read returns
    // each step's step_template_id + DEPEND_ON parent + target - the graph the orchestrator needs
    // to chain onto or UPDATE an existing step instead of re-authoring a duplicate.
    return workflowService.readAuthoredAttackPath(run.getSimulationId()).stream()
        .map(this::toStepState)
        .toList();
  }

  private AutonomousAttackPathStepState toStepState(WorkflowService.AuthoredAttackStep authored) {
    JsonNode data = parseInjectData(authored.injectDataJson());
    List<Inject> injects =
        authored.runInjectIds().stream()
            .map(id -> injectRepository.findById(id).orElse(null))
            .filter(Objects::nonNull)
            .toList();
    String title = jsonText(data, "inject_title");
    String type = jsonText(data, "inject_type");
    String contractId = injectContractId(data);
    if (!injects.isEmpty()) {
      Inject last = injects.get(injects.size() - 1);
      if (!hasText(title)) {
        title = last.getTitle();
      }
      if (!hasText(type)) {
        type = last.getType();
      }
      if (!hasText(contractId)) {
        contractId = last.getInjectorContract().map(InjectorContract::getId).orElse(null);
      }
    }
    String injectId = injects.isEmpty() ? "" : injects.get(injects.size() - 1).getId();
    return new AutonomousAttackPathStepState(
        authored.stepTemplateId(),
        authored.parentStepTemplateId(),
        injectId,
        title,
        type,
        contractId,
        targetSummary(data),
        aggregateStatus(injects),
        aggregateTraces(injects));
  }

  private JsonNode parseInjectData(String json) {
    if (!hasText(json)) {
      return null;
    }
    try {
      return objectMapper.readTree(json);
    } catch (Exception e) {
      return null;
    }
  }

  private static String jsonText(JsonNode node, String field) {
    if (node == null) {
      return null;
    }
    JsonNode value = node.get(field);
    return value != null && !value.isNull() ? value.asText() : null;
  }

  private static String injectContractId(JsonNode data) {
    if (data == null) {
      return null;
    }
    JsonNode contract = data.get("inject_injector_contract");
    if (contract == null || contract.isNull()) {
      return null;
    }
    if (contract.isTextual()) {
      return contract.asText();
    }
    JsonNode id = contract.get("injector_contract_id");
    return id != null && !id.isNull() ? id.asText() : null;
  }

  private static List<String> jsonIdArray(JsonNode data, String field) {
    List<String> ids = new ArrayList<>();
    if (data == null) {
      return ids;
    }
    JsonNode array = data.get(field);
    if (array != null && array.isArray()) {
      array.forEach(
          element -> {
            if (element != null && !element.isNull()) {
              ids.add(element.asText());
            }
          });
    }
    return ids;
  }

  private static String targetSummary(JsonNode data) {
    if (data == null) {
      return "inherits run scope";
    }
    List<String> parts = new ArrayList<>();
    JsonNode allTeams = data.get("inject_all_teams");
    if (allTeams != null && allTeams.asBoolean(false)) {
      parts.add("all teams");
    }
    List<String> teams = jsonIdArray(data, "inject_teams");
    if (!teams.isEmpty()) {
      parts.add("teams=" + teams);
    }
    List<String> assets = jsonIdArray(data, "inject_assets");
    if (!assets.isEmpty()) {
      parts.add("assets=" + assets);
    }
    List<String> groups = jsonIdArray(data, "inject_asset_groups");
    if (!groups.isEmpty()) {
      parts.add("asset_groups=" + groups);
    }
    return parts.isEmpty() ? "inherits run scope" : String.join("; ", parts);
  }

  private static String aggregateStatus(List<Inject> injects) {
    if (injects.isEmpty()) {
      return "PENDING";
    }
    List<String> names =
        injects.stream()
            .map(
                inject ->
                    inject
                        .getStatus()
                        .map(s -> s.getName() != null ? s.getName().name() : "PENDING")
                        .orElse("PENDING"))
            .toList();
    if (names.contains("ERROR")) {
      return "ERROR";
    }
    if (names.stream()
        .anyMatch(
            n ->
                n.equals("QUEUING")
                    || n.equals("EXECUTING")
                    || n.equals("PENDING")
                    || n.equals("DRAFT"))) {
      return "EXECUTING";
    }
    return names.get(names.size() - 1);
  }

  private static List<String> aggregateTraces(List<Inject> injects) {
    List<String> traces = new ArrayList<>();
    for (Inject inject : injects) {
      inject
          .getStatus()
          .ifPresent(
              s ->
                  s.getTraces()
                      .forEach(
                          t ->
                              traces.add(
                                  (t.getAction() != null ? t.getAction().name() : "TRACE")
                                      + "/"
                                      + (t.getStatus() != null ? t.getStatus().name() : "")
                                      + ": "
                                      + t.getMessage())));
    }
    return traces;
  }

  /**
   * Promotes a run finding (a host/IP/hostname discovered during the attack path) into a real,
   * targetable endpoint asset so the orchestrator can pivot to attacking it. The ORIGINAL finding
   * is kept - the new asset is simply linked onto it - which is the whole point: the finding
   * remains the evidence trail while the asset becomes the inject target for the next chained step
   * (lateral movement).
   */
  @Transactional(rollbackFor = Exception.class)
  public AutonomousPromotedAssetResult promoteFindingToAsset(String runId, String findingId) {
    requireFeature();
    AutonomousRun run = require(runId);
    Finding finding =
        findingRepository
            .findById(findingId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Finding not found"));
    String label = hasText(finding.getName()) ? finding.getName() : finding.getValue();
    EndpointInput input = new EndpointInput();
    input.setName(label);
    input.setDescription(
        "Promoted from autonomous finding " + findingId + " (" + finding.getType() + ")");
    // A finding value discovered during recon is either an IP or a hostname; feed it to the right
    // field so the endpoint resolves against the fleet. Platform/arch default to Unknown - the
    // orchestrator refines them via its normal endpoint tools if it needs to.
    if (looksLikeIpAddress(finding.getValue())) {
      input.setIps(new String[] {finding.getValue()});
    } else {
      input.setHostname(finding.getValue());
    }
    Endpoint endpoint = endpointService.createEndpoint(input);
    // Keep the original finding; just link the promoted asset onto it (promotion, not replacement).
    findingRepository.insertFindingAsset(findingId, endpoint.getId());
    eventService.append(
        runId,
        run.getSimulationId(),
        AutonomousEventType.TOOL_ACTION,
        "Finding promoted to asset",
        "Finding '"
            + label
            + "' is now a targetable endpoint ("
            + endpoint.getId()
            + "). The original finding is kept and linked to the asset.",
        null);
    return new AutonomousPromotedAssetResult(endpoint.getId(), label, findingId);
  }

  private boolean looksLikeIpAddress(String value) {
    return value != null && value.matches("^\\d{1,3}(\\.\\d{1,3}){3}$");
  }

  /**
   * Guarantees a targetable team wrapping the given persons for a human-in-the-loop step (phishing,
   * smishing, credential harvesting, ...). An OpenAEV inject can only target a TEAM, and an email /
   * SMS inject only resolves recipients from the players ENABLED on the simulation ({@code
   * exercise_teams_users}) - not merely from team membership. Doing this in three separate
   * orchestrator calls (create player, create team, set members) routinely left the team unattached
   * or its players not enabled, which surfaced as the "Email needs at least one user" execution
   * error. This does all of it atomically: reuse-or-create a CONTEXTUAL team on the run's
   * simulation, set its members, and enable those players for delivery, then hand back a team id
   * the next chained step can target directly.
   */
  @Transactional(rollbackFor = Exception.class)
  public AutonomousTargetTeamResult ensureTargetTeam(
      String runId, List<String> playerIds, String name, String teamId) {
    requireFeature();
    AutonomousRun run = require(runId);
    String simulationId = run.getSimulationId();
    if (!hasText(simulationId)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "The autonomous run has no live simulation to attach a team to");
    }
    List<String> requestedPlayerIds =
        playerIds == null
            ? List.of()
            : playerIds.stream().filter(id -> hasText(id)).distinct().toList();
    if (requestedPlayerIds.isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Provide at least one player id to wrap in the team - a team with no members cannot"
              + " receive a human-targeted inject");
    }
    List<User> players = new ArrayList<>();
    userRepository.findAllById(requestedPlayerIds).forEach(players::add);
    if (players.isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND, "None of the provided player ids resolved to a person");
    }
    Exercise simulation =
        exerciseRepository
            .findById(simulationId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Simulation not found"));

    final Team team;
    if (hasText(teamId)) {
      // Idempotent reuse: augment an existing wrapper with any newly requested members.
      Team existing =
          teamRepository
              .findById(teamId)
              .orElseThrow(
                  () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found"));
      LinkedHashMap<String, User> members = new LinkedHashMap<>();
      existing.getUsers().forEach(u -> members.put(u.getId(), u));
      players.forEach(u -> members.put(u.getId(), u));
      existing.setUsers(new ArrayList<>(members.values()));
      boolean alreadyOnSimulation =
          simulation.getTeams().stream().anyMatch(t -> teamId.equals(t.getId()));
      if (!alreadyOnSimulation) {
        existing.getExercises().add(simulation);
      }
      team = teamRepository.save(existing);
    } else {
      Team created = new Team();
      created.setName(hasText(name) ? name : defaultTargetTeamName(players));
      created.setContextual(true);
      created.setUsers(new ArrayList<>(players));
      created.setExercises(new ArrayList<>(List.of(simulation)));
      team = teamRepository.save(created);
    }

    // Enable the players on the simulation so the email/SMS executor resolves them as recipients.
    exerciseService.enablePlayers(simulationId, team, players.stream().map(User::getId).toList());

    List<String> enabledPlayerIds = team.getUsers().stream().map(User::getId).toList();
    eventService.append(
        runId,
        simulationId,
        AutonomousEventType.TOOL_ACTION,
        "Target team ready",
        "Team '"
            + team.getName()
            + "' now wraps "
            + enabledPlayerIds.size()
            + " enabled recipient(s) and can be targeted by a human-in-the-loop inject.",
        null);
    return new AutonomousTargetTeamResult(team.getId(), team.getName(), enabledPlayerIds);
  }

  private String defaultTargetTeamName(List<User> players) {
    String who =
        players.stream()
            .limit(3)
            .map(u -> hasText(u.getEmail()) ? u.getEmail() : u.getId())
            .collect(Collectors.joining(", "));
    if (players.size() > 3) {
      who = who + " +" + (players.size() - 3);
    }
    return "AI target " + who;
  }

  // endregion

  // region reads

  // Not read-only: reconcileWithSimulation may need to persist a status sync so a run whose
  // simulation died out-of-band settles itself instead of dangling active forever.
  @Transactional(rollbackFor = Exception.class)
  public AutonomousRun get(String runId) {
    requireFeature();
    return reconcileWithSimulation(require(runId));
  }

  @Transactional(readOnly = true)
  public List<AutonomousRun> list() {
    requireFeature();
    return runRepository.findAllByOrderByCreatedAtDesc();
  }

  /**
   * Returns the run driving a given simulation, if any. Used by the simulation detail page to
   * decide whether to render the autonomous (AI-driven) cockpit instead of the manual chaining
   * editor. 404 when the simulation is not autonomous, so the caller can treat the absence as
   * "manual".
   */
  @Transactional(rollbackFor = Exception.class)
  public AutonomousRun getBySimulation(String simulationId) {
    requireFeature();
    return reconcileWithSimulation(
        runRepository
            .findBySimulationId(simulationId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No autonomous run drives this simulation")));
  }

  /**
   * Returns the run driving a given scenario, if any. An autonomous run owns exactly one scenario
   * (and its single simulation), so this is the scenario-side twin of {@link #getBySimulation}: it
   * lets the scenario detail page render the same AI-driven cockpit and steer the underlying
   * simulation. 404 when the scenario is not autonomous.
   */
  @Transactional(rollbackFor = Exception.class)
  public AutonomousRun getByScenario(String scenarioId) {
    requireFeature();
    return reconcileWithSimulation(
        runRepository
            .findByScenarioId(scenarioId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No autonomous run drives this scenario")));
  }

  @Transactional(readOnly = true)
  public List<AutonomousEvent> timeline(String runId, long sinceSequence) {
    requireFeature();
    require(runId);
    return sinceSequence > 0
        ? eventService.timelineSince(runId, sinceSequence)
        : eventService.timeline(runId);
  }

  @Transactional(readOnly = true)
  public List<AutonomousDirective> directives(String runId) {
    requireFeature();
    require(runId);
    return directiveRepository.findByRunIdOrderByCreatedAtAsc(runId);
  }

  @Transactional(readOnly = true)
  public List<AutonomousObjectiveTemplate> objectiveTemplates() {
    requireFeature();
    return templateService.listForCurrentTenant();
  }

  // endregion

  // region helpers

  /**
   * Auto-provisions a fresh attack-path (chaining) scenario for a fully autonomous run. Only a name
   * is strictly required by the model; {@link ScenarioService#createScenarioChaining} fills the
   * mandatory sender emails and attaches the (empty) TEMPLATE workflow the AI orchestrator then
   * builds upon. Kept intentionally minimal so launching a run never asks the operator for anything
   * beyond the objective.
   */
  private Scenario provisionAutonomousScenario(String objective, String name, String description) {
    Scenario scenario = new Scenario();
    scenario.setName(hasText(name) ? name.trim() : defaultScenarioName());
    scenario.setDescription(
        hasText(description) ? description.trim() : defaultScenarioDescription(objective));
    scenario.setCategory("attack-scenario");
    // Persist the autonomous marker so the scenario payload states it directly: the detail page
    // then renders the AI cockpit without probing the autonomous-run lookup, and a manual scenario
    // (flag false) never fires that lookup at all.
    scenario.setAutonomous(true);
    try {
      return scenarioService.createScenarioChaining(scenario);
    } catch (ChainingException e) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Failed to provision the autonomous attack-path scenario: " + e.getMessage(),
          e);
    }
  }

  /**
   * Marks the scenario's chaining workflow as keep-alive (and disables its timeout) so an
   * autonomous run survives an empty launch and long idle gaps between decision cycles. Best-effort
   * on a missing workflow so an advanced caller-provided scenario without a workflow surfaces the
   * standard chaining error at launch rather than here.
   */
  private void markWorkflowKeepAlive(String scenarioId) {
    try {
      workflowService.markScenarioWorkflowKeepAlive(scenarioId);
    } catch (ChainingException e) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Failed to prepare the autonomous attack-path workflow: " + e.getMessage(),
          e);
    }
  }

  private String defaultScenarioName() {
    return "Autonomous attack path - "
        + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
  }

  private String defaultScenarioDescription(String objective) {
    return "Auto-provisioned for an autonomous AI attack-path run.\n\nObjective:\n" + objective;
  }

  /**
   * Resolves the objective's scope mode ({@code environment} vs {@code target}) from its template
   * so the orchestrator can deterministically decide, on its first cycle, whether it must resolve a
   * specific target (and ask the operator when ambiguous). Free-text runs have no template, so
   * {@code null} is returned and the orchestrator classifies the objective itself.
   */
  private String resolveScopeMode(String objectiveTemplateKey) {
    if (!hasText(objectiveTemplateKey)) {
      return null;
    }
    AutonomousObjectiveTemplate template = templateService.findByKeyOrNull(objectiveTemplateKey);
    return template != null ? template.getScopeMode() : null;
  }

  /**
   * Builds the run's authoritative mixed scope from the create input. The new {@code scope} list is
   * the source of truth; the legacy single {@code scope_asset_group_id} / {@code scope_team_id}
   * shortcuts (preset flows, older callers) are folded in, de-duplicated by (type,id).
   */
  private List<AutonomousScopeTarget> resolveScope(AutonomousRunCreateInput input) {
    List<AutonomousScopeTarget> scope = new ArrayList<>();
    if (input.getScope() != null) {
      for (AutonomousScopeTarget target : input.getScope()) {
        if (target != null && hasText(target.getType()) && hasText(target.getId())) {
          addScopeIfAbsent(scope, target.getType().trim(), target.getId().trim());
        }
      }
    }
    if (hasText(input.getScopeAssetGroupId())) {
      addScopeIfAbsent(scope, "ASSETS_GROUPS", input.getScopeAssetGroupId());
    }
    if (hasText(input.getScopeTeamId())) {
      addScopeIfAbsent(scope, "TEAMS", input.getScopeTeamId());
    }
    return scope;
  }

  private void addScopeIfAbsent(List<AutonomousScopeTarget> scope, String type, String id) {
    boolean present =
        scope.stream().anyMatch(t -> type.equals(t.getType()) && id.equals(t.getId()));
    if (!present) {
      scope.add(new AutonomousScopeTarget(type, id));
    }
  }

  private String firstScopeIdOfType(List<AutonomousScopeTarget> scope, String type) {
    return scope.stream()
        .filter(t -> type.equals(t.getType()))
        .map(AutonomousScopeTarget::getId)
        .findFirst()
        .orElse(null);
  }

  /**
   * Translates the run's mixed scope list ({@link AutonomousScopeTarget}, using the {@code
   * TargetType} vocabulary) into ALLOWLIST workflow scope-rule inputs so the perimeter can be
   * persisted onto the workflow. Each kind maps to its scope-rule source (asset, asset group, team,
   * person); unknown kinds are skipped.
   */
  private List<WorkflowScopeRuleInput> toAllowlistScopeInputs(List<AutonomousScopeTarget> scope) {
    if (scope == null || scope.isEmpty()) {
      return List.of();
    }
    List<WorkflowScopeRuleInput> inputs = new ArrayList<>();
    for (AutonomousScopeTarget target : scope) {
      if (target == null || !hasText(target.getId()) || target.getType() == null) {
        continue;
      }
      ScopeRuleSource source =
          switch (target.getType()) {
            case "ASSETS" -> ScopeRuleSource.ASSET;
            case "ASSETS_GROUPS" -> ScopeRuleSource.ASSET_GROUP;
            case "TEAMS" -> ScopeRuleSource.TEAM;
            case "PLAYERS" -> ScopeRuleSource.PLAYER;
            default -> null;
          };
      if (source == null) {
        continue;
      }
      inputs.add(
          WorkflowScopeRuleInput.builder()
              .selectedMode(ScopeRuleSelectedMode.ALLOWLIST)
              .ruleSource(source)
              .ruleValue(target.getId())
              .build());
    }
    return inputs;
  }

  /**
   * Extracts the allow-listed ENTITY targets (asset, asset group, team, person) from a full scope
   * rule list so they can be folded into the run's {@link AutonomousScopeTarget} projection. Manual
   * IP / CIDR / hostname / CSV rules and deny-list rules are intentionally skipped: they are not
   * targetable entities and live only on the workflow scope, not the run projection.
   */
  private List<AutonomousScopeTarget> allowlistTargetsFromRules(
      List<WorkflowScopeRuleInput> rules) {
    if (rules == null || rules.isEmpty()) {
      return List.of();
    }
    List<AutonomousScopeTarget> targets = new ArrayList<>();
    for (WorkflowScopeRuleInput rule : rules) {
      if (rule == null
          || rule.getSelectedMode() != ScopeRuleSelectedMode.ALLOWLIST
          || rule.getRuleSource() == null
          || !hasText(rule.getRuleValue())) {
        continue;
      }
      String type =
          switch (rule.getRuleSource()) {
            case ASSET -> "ASSETS";
            case ASSET_GROUP -> "ASSETS_GROUPS";
            case TEAM -> "TEAMS";
            case PLAYER -> "PLAYERS";
            default -> null;
          };
      if (type != null) {
        targets.add(new AutonomousScopeTarget(type, rule.getRuleValue().trim()));
      }
    }
    return targets;
  }

  private String resolveObjective(AutonomousRunCreateInput input) {
    String objective = input.getObjective();
    if ((objective == null || objective.isBlank()) && input.getObjectiveTemplateKey() != null) {
      AutonomousObjectiveTemplate template =
          templateService.findByKeyOrNull(input.getObjectiveTemplateKey());
      if (template == null) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Unknown objective template: " + input.getObjectiveTemplateKey());
      }
      objective = template.getPrompt();
    }
    if (objective == null || objective.isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "An objective (free text or template key) is required");
    }
    return objective.trim();
  }

  /**
   * Hard-links the run's lifecycle to its simulation. An autonomous scenario, its run, and its
   * single simulation are one unit, so if the simulation reaches a terminal state out-of-band -
   * canceled from the simulations list, finished by the chaining engine, timed out, or (the
   * reported case) canceled by a Stop click whose backend died before the run row was saved - the
   * run must follow instead of dangling in an active state the operator can neither stop nor
   * delete. A deleted simulation counts as terminal too (nothing left to drive -> canceled).
   *
   * <p>Idempotent: only an ACTIVE run is ever reconciled, and a run already matching its simulation
   * is left untouched, so this is safe to call on every read (page load / poll). Best-effort on the
   * orchestrator halt so a transport hiccup never blocks the sync.
   */
  private AutonomousRun reconcileWithSimulation(AutonomousRun run) {
    if (run == null || !hasText(run.getSimulationId())) {
      return run;
    }
    AutonomousRunStatus current = run.getStatus();
    boolean active =
        current == AutonomousRunStatus.CREATED
            || current == AutonomousRunStatus.RUNNING
            || current == AutonomousRunStatus.PAUSED
            || current == AutonomousRunStatus.WAITING_INPUT;
    if (!active) {
      return run;
    }
    ExerciseStatus simStatus = loadSimulationStatusOrNull(run.getSimulationId());
    AutonomousRunStatus target;
    if (simStatus == null) {
      // Simulation deleted out-of-band: there is nothing left to drive, settle the run.
      target = AutonomousRunStatus.CANCELED;
    } else if (simStatus == ExerciseStatus.CANCELED) {
      target = AutonomousRunStatus.CANCELED;
    } else if (simStatus == ExerciseStatus.FINISHED) {
      // A FINISHED simulation only means the RUN is complete when the run is actually EXECUTING.
      // Two active states are not executing, so a finished/idle simulation beneath them is expected
      // and must NOT auto-complete the run:
      //   - plan mode (dry-run): the plan simulation never runs, so it reads FINISHED from the
      //     start; the settled state of a plan is PLANNED, set explicitly by the orchestrator when
      //     the design is done - never COMPLETED derived from the empty sim.
      //   - WAITING_INPUT: the orchestrator deliberately parked awaiting the operator, and an empty
      //     simulation reads FINISHED while it waits. Completing here terminated the run before the
      //     operator could answer the scoping question (the reported plan-mode bug).
      // Cancellation / deletion of the simulation still settles both (handled above), so the
      // operator can always stop a parked or plan run; only the FINISHED->COMPLETED sync is
      // skipped.
      if (run.isPlanMode() || current == AutonomousRunStatus.WAITING_INPUT) {
        return run;
      }
      target = AutonomousRunStatus.COMPLETED;
    } else {
      // Scheduled / running / paused: the simulation is still live, no desync to fix.
      return run;
    }
    if (target == current) {
      return run;
    }
    // Persist the sync in its OWN transaction so a read endpoint (getByScenario / getBySimulation,
    // which run read-only) never writes inside - and therefore never poisons - its request
    // transaction. Writing the status flip + STATUS event directly here was the root cause of the
    // "UnexpectedRollbackException: marked as rollback-only" 500 on Stop: the INSERT into the event
    // table inside a read-only transaction marked it rollback-only, the outer commit blew up, and
    // the failed GET made the UI fall back to the manual (non-AI) view. Best-effort: if the
    // isolated
    // write fails, we still return the run with the target status applied in-memory so the read is
    // truthful and never 500s.
    String detail =
        "Synced from the underlying simulation ("
            + (simStatus == null ? "deleted" : simStatus.name())
            + "): an autonomous run and its simulation are always kept in lockstep.";
    try {
      AutonomousRun settled = reconciliationWriter.settleRunStatus(run.getId(), target, detail);
      if (settled != null) {
        // The run is now terminal, so stop the orchestrator loop too (purge on cancel so a later
        // restart starts clean). Best-effort, after commit.
        cancelOrchestratorAfterCommit(
            run.getId(),
            "run reconciled to " + target + " from simulation status",
            target == AutonomousRunStatus.CANCELED);
        return settled;
      }
    } catch (Exception e) {
      log.warn(
          "[Autonomous] Could not persist reconciled status {} for run {}; returning in-memory"
              + " sync",
          target,
          run.getId(),
          e);
    }
    run.setStatus(target);
    return run;
  }

  /** Reads the simulation's status, or {@code null} when it no longer exists / is unreadable. */
  private ExerciseStatus loadSimulationStatusOrNull(String simulationId) {
    try {
      return exerciseService.exercise(simulationId).getStatus();
    } catch (Exception e) {
      return null;
    }
  }

  /** Transitions the chained simulation, surfacing an invalid transition as a 400. */
  private void transitionSimulation(AutonomousRun run, ExerciseStatus target) {
    if (run.getSimulationId() == null) {
      return;
    }
    try {
      exerciseService.changeExerciseStatus(target, run.getSimulationId());
    } catch (ChainingException | UnsupportedOperationException e) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Cannot move the simulation to " + target + ": " + e.getMessage(),
          e);
    }
  }

  /** Best-effort terminal transition from an orchestrator callback; never fails the callback. */
  private void transitionSimulationQuietly(AutonomousRun run, ExerciseStatus target) {
    if (run.getSimulationId() == null) {
      return;
    }
    try {
      exerciseService.changeExerciseStatus(target, run.getSimulationId());
    } catch (Exception e) {
      log.warn(
          "[Autonomous] Could not move simulation {} to {} on run {} completion",
          run.getSimulationId(),
          target,
          run.getId(),
          e);
    }
  }

  private static String asString(Object value) {
    return value != null ? value.toString() : null;
  }

  // endregion
}
