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
    engageOrchestrator(run);
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
    return saved;
  }

  /**
   * Engages (or re-engages) the XTM One orchestrator for {@code run} and captures the returned
   * session handle. The upstream start endpoint is idempotent AND re-engaging: it resets the run's
   * existing durable execution to a fresh engagement when one already exists (restart / resume) or
   * creates one otherwise, so this single call covers first start, resume, and restart. Shared by
   * {@link #start} and {@link #resume} so both attribute the run to the acting operator's JWT and
   * refresh {@code xtmSessionId} the same way.
   */
  private void engageOrchestrator(AutonomousRun run) {
    Map<String, Object> handle =
        xtmOneClient.startAutonomousRun(
            run.getXtmAgentSlug(),
            run.getObjective(),
            run.getId(),
            run.getSimulationId(),
            run.getScopeAssetGroupId(),
            resolveScopeMode(run.getObjectiveTemplateKey()),
            openAEVConfig.getBaseUrl());
    if (handle != null) {
      run.setXtmSessionId(asString(handle.get("session_id")));
      String slug = asString(handle.get("agent_slug"));
      if (slug != null) {
        run.setXtmAgentSlug(slug);
      }
    }
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
    cancelOrchestratorAfterCommit(runId, "run paused by operator");
    return saved;
  }

  /** Resumes a paused run and its chained simulation, re-engaging the XTM One orchestrator. */
  @Transactional(rollbackFor = Exception.class)
  public AutonomousRun resume(String runId) {
    requireFeature();
    AutonomousRun run = require(runId);
    transitionSimulation(run, ExerciseStatus.RUNNING);
    engageOrchestrator(run);
    run.setStatus(AutonomousRunStatus.RUNNING);
    run.setLastError(null);
    AutonomousRun saved = runRepository.save(run);
    eventService.append(
        runId, run.getSimulationId(), AutonomousEventType.STATUS, "Run resumed", null, null);
    return saved;
  }

  /** Cancels a run and its chained simulation, halting the XTM One orchestrator. Terminal. */
  @Transactional(rollbackFor = Exception.class)
  public AutonomousRun cancel(String runId) {
    requireFeature();
    AutonomousRun run = require(runId);
    transitionSimulation(run, ExerciseStatus.CANCELED);
    run.setStatus(AutonomousRunStatus.CANCELED);
    AutonomousRun saved = runRepository.save(run);
    eventService.append(
        runId, run.getSimulationId(), AutonomousEventType.STATUS, "Run canceled", null, null);
    cancelOrchestratorAfterCommit(runId, "run canceled by operator");
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
    cancelOrchestratorAfterCommit(runId, "run restarted by operator");
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
    AutonomousRunStatus status = run.getStatus();
    if (status == AutonomousRunStatus.CREATED
        || status == AutonomousRunStatus.RUNNING
        || status == AutonomousRunStatus.PAUSED
        || status == AutonomousRunStatus.WAITING_INPUT) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Stop the autonomous run before deleting its scenario");
    }
    // Halt the XTM One orchestration first: once the run row is gone OpenAEV can no longer be
    // driven, but a still-live durable execution would keep self-resuming and dispatching injects
    // against the deleted simulation. Fired after commit (the run id is captured now) so the
    // upstream cancel resolves the same execution by its stable dedup key.
    String runId = run.getId();
    cancelOrchestratorAfterCommit(runId, "autonomous scenario deleted");
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
  private void cancelOrchestratorAfterCommit(String runId, String reason) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              xtmOneClient.cancelAutonomousRun(runId, reason);
            }
          });
    } else {
      xtmOneClient.cancelAutonomousRun(runId, reason);
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

  @Transactional(readOnly = true)
  public AutonomousRun get(String runId) {
    requireFeature();
    return require(runId);
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
  @Transactional(readOnly = true)
  public AutonomousRun getBySimulation(String simulationId) {
    requireFeature();
    return runRepository
        .findBySimulationId(simulationId)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "No autonomous run drives this simulation"));
  }

  /**
   * Returns the run driving a given scenario, if any. An autonomous run owns exactly one scenario
   * (and its single simulation), so this is the scenario-side twin of {@link #getBySimulation}: it
   * lets the scenario detail page render the same AI-driven cockpit and steer the underlying
   * simulation. 404 when the scenario is not autonomous.
   */
  @Transactional(readOnly = true)
  public AutonomousRun getByScenario(String scenarioId) {
    requireFeature();
    return runRepository
        .findByScenarioId(scenarioId)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "No autonomous run drives this scenario"));
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
