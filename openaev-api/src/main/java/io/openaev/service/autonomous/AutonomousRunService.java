package io.openaev.service.autonomous;

import static java.time.Instant.now;
import static java.time.temporal.ChronoUnit.MINUTES;
import static org.springframework.util.StringUtils.hasText;

import io.openaev.api.autonomous.dto.AutonomousRunCreateInput;
import io.openaev.api.chaining.dto.WorkflowConfigurationInput;
import io.openaev.config.OpenAEVConfig;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.ExerciseStatus;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Workflow;
import io.openaev.database.model.autonomous.AutonomousDirective;
import io.openaev.database.model.autonomous.AutonomousDirectiveStatus;
import io.openaev.database.model.autonomous.AutonomousEvent;
import io.openaev.database.model.autonomous.AutonomousEventType;
import io.openaev.database.model.autonomous.AutonomousObjectiveTemplate;
import io.openaev.database.model.autonomous.AutonomousRun;
import io.openaev.database.model.autonomous.AutonomousRunStatus;
import io.openaev.database.repository.autonomous.AutonomousDirectiveRepository;
import io.openaev.database.repository.autonomous.AutonomousRunRepository;
import io.openaev.rest.exception.ChainingException;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.service.PreviewFeatureService;
import io.openaev.service.ScenarioToExerciseService;
import io.openaev.service.chaining.WorkflowService;
import io.openaev.service.scenario.ScenarioService;
import io.openaev.xtmone.XtmOneClient;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
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
    } else {
      // Autonomous default: provision a fresh attack-path scenario so the operator never has to
      // build one. The AI orchestrator populates and drives it after start().
      scenario = provisionAutonomousScenario(objective, input.getName(), input.getDescription());
      scenarioId = scenario.getId();
    }

    Exercise simulation =
        scenarioToExerciseService.toExercise(
            scenario, now().truncatedTo(MINUTES).plus(1, MINUTES), true);
    try {
      workflowService.startWorkflowByScenarioIdAndSimulation(scenarioId, simulation);
    } catch (ChainingException e) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Failed to start the chained simulation: " + e.getMessage(), e);
    }

    AutonomousRun run = new AutonomousRun();
    run.setObjective(objective);
    run.setObjectiveTemplateKey(input.getObjectiveTemplateKey());
    run.setScenarioId(scenarioId);
    run.setSimulationId(simulation.getId());
    run.setScopeAssetGroupId(input.getScopeAssetGroupId());
    run.setXtmAgentSlug(input.getAgentSlug());
    run.setStatus(AutonomousRunStatus.CREATED);
    AutonomousRun saved = runRepository.save(run);

    eventService.append(
        saved.getId(),
        simulation.getId(),
        AutonomousEventType.STATUS,
        "Run created",
        "Autonomous attack-path run created from scenario \"" + scenario.getName() + "\".",
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
    run.setStatus(AutonomousRunStatus.RUNNING);
    run.setLastError(null);
    AutonomousRun saved = runRepository.save(run);
    eventService.append(
        runId,
        run.getSimulationId(),
        AutonomousEventType.STATUS,
        "Run started",
        "Orchestrator engaged; autonomous execution is now running.",
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
    final String scopeMode = resolveScopeMode(run.getObjectiveTemplateKey());
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              engageOrchestratorNow(
                  runId, agentSlug, objective, simulationId, scopeAssetGroupId, scopeMode);
            }
          });
    } else {
      engageOrchestratorNow(
          runId, agentSlug, objective, simulationId, scopeAssetGroupId, scopeMode);
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
      String scopeMode) {
    try {
      Map<String, Object> handle =
          xtmOneClient.startAutonomousRun(
              agentSlug,
              objective,
              runId,
              simulationId,
              scopeAssetGroupId,
              scopeMode,
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
   * <p>Deliberately resilient: moving the run to {@code CANCELED} must ALWAYS succeed, even when the
   * underlying simulation is already terminal (e.g. it was canceled a moment earlier and the
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
    Exercise simulation =
        scenarioToExerciseService.toExercise(
            scenario, now().truncatedTo(MINUTES).plus(1, MINUTES), true);
    try {
      workflowService.startWorkflowByScenarioIdAndSimulation(run.getScenarioId(), simulation);
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
    return eventService.append(runId, run.getSimulationId(), type, title, content, data);
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
    // Reflect a terminal orchestrator decision onto the chained simulation so both stay consistent.
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
    try {
      return scenarioService.createScenarioChaining(scenario);
    } catch (ChainingException e) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Failed to provision the autonomous attack-path scenario: " + e.getMessage(),
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
      target = AutonomousRunStatus.COMPLETED;
    } else {
      // Scheduled / running / paused: the simulation is still live, no desync to fix.
      return run;
    }
    if (target == current) {
      return run;
    }
    run.setStatus(target);
    AutonomousRun saved = runRepository.save(run);
    eventService.append(
        run.getId(),
        run.getSimulationId(),
        AutonomousEventType.STATUS,
        target == AutonomousRunStatus.CANCELED ? "Run canceled" : "Run completed",
        "Synced from the underlying simulation ("
            + (simStatus == null ? "deleted" : simStatus.name())
            + "): an autonomous run and its simulation are always kept in lockstep.",
        null);
    // The run is now terminal, so stop the orchestrator loop too (purge on cancel so a later
    // restart starts clean). Best-effort, after commit.
    cancelOrchestratorAfterCommit(
        run.getId(),
        "run reconciled to " + target + " from simulation status",
        target == AutonomousRunStatus.CANCELED);
    return saved;
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
