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
import io.openaev.rest.settings.PreviewFeature;
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
import org.springframework.web.server.ResponseStatusException;

/**
 * Lifecycle owner for autonomous (AI-driven) attack-path runs. This is OpenAEV's half of the
 * feature: it seeds and drives a chained simulation as the execution/visualization substrate, hands
 * the objective to the XTM One orchestrator (the "brain"), and exposes the run's live state,
 * decision timeline, and real-time steering surface back to the UI.
 *
 * <p>The orchestrator streams its progress back through {@link #recordEvent} / {@link
 * #updateStatus} and reads operator steering through {@link #consumePendingDirectives}, so a run can
 * be followed and re-steered without ever stopping it. Every mutation is gated behind {@link
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
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Autonomous run not found"));
  }

  // region lifecycle

  /**
   * Creates a run: resolves the objective (free text or template), auto-provisions the attack-path
   * (chaining) substrate, spins up its running simulation, and persists the durable run handle in
   * {@code CREATED}. The run is fully autonomous - the operator never authors an attack path; the AI
   * orchestrator builds and executes it. An existing chaining scenario may still be passed
   * explicitly (advanced), otherwise a fresh one is provisioned from the objective. The orchestrator
   * is engaged separately by {@link #start} so creation stays fast and idempotent.
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
        scenarioToExerciseService.toExercise(scenario, now().truncatedTo(MINUTES).plus(1, MINUTES), true);
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
   * Engages the XTM One orchestrator for a created run. The call is a short fire-and-forget enqueue;
   * the orchestrator then drives OpenAEV back through the platform MCP tools and these callbacks.
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
    Map<String, Object> handle =
        xtmOneClient.startAutonomousRun(
            run.getXtmAgentSlug(),
            run.getObjective(),
            run.getId(),
            run.getSimulationId(),
            run.getScopeAssetGroupId(),
            openAEVConfig.getBaseUrl());
    if (handle != null) {
      run.setXtmSessionId(asString(handle.get("session_id")));
      String slug = asString(handle.get("agent_slug"));
      if (slug != null) {
        run.setXtmAgentSlug(slug);
      }
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
    return saved;
  }

  /** Pauses a live run and the underlying chained simulation, keeping all state for resumption. */
  @Transactional(rollbackFor = Exception.class)
  public AutonomousRun pause(String runId) {
    requireFeature();
    AutonomousRun run = require(runId);
    transitionSimulation(run, ExerciseStatus.PAUSED);
    run.setStatus(AutonomousRunStatus.PAUSED);
    AutonomousRun saved = runRepository.save(run);
    eventService.append(
        runId, run.getSimulationId(), AutonomousEventType.STATUS, "Run paused", null, null);
    return saved;
  }

  /** Resumes a paused run and its chained simulation. */
  @Transactional(rollbackFor = Exception.class)
  public AutonomousRun resume(String runId) {
    requireFeature();
    AutonomousRun run = require(runId);
    transitionSimulation(run, ExerciseStatus.RUNNING);
    run.setStatus(AutonomousRunStatus.RUNNING);
    AutonomousRun saved = runRepository.save(run);
    eventService.append(
        runId, run.getSimulationId(), AutonomousEventType.STATUS, "Run resumed", null, null);
    return saved;
  }

  /** Cancels a run and its chained simulation. Terminal. */
  @Transactional(rollbackFor = Exception.class)
  public AutonomousRun cancel(String runId) {
    requireFeature();
    AutonomousRun run = require(runId);
    transitionSimulation(run, ExerciseStatus.CANCELED);
    run.setStatus(AutonomousRunStatus.CANCELED);
    AutonomousRun saved = runRepository.save(run);
    eventService.append(
        runId, run.getSimulationId(), AutonomousEventType.STATUS, "Run canceled", null, null);
    return saved;
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

  /** Applies a run-status transition pushed by the orchestrator (waiting-input, completed, failed...). */
  @Transactional(rollbackFor = Exception.class)
  public AutonomousRun updateStatus(
      String runId,
      AutonomousRunStatus status,
      String lastError,
      String title,
      String content) {
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
    return saved;
  }

  /**
   * Applies a live scope / rate-limit / safe-mode edit to the run's RUN workflow(s) without stopping
   * it. The chaining engine reads the updated scope on its next decision cycle, so a denylist entry
   * added here walls off the matching assets immediately.
   */
  @Transactional(rollbackFor = Exception.class)
  public List<Workflow> applyLiveConfiguration(String runId, WorkflowConfigurationInput input) {
    requireFeature();
    AutonomousRun run = require(runId);
    List<Workflow> updated = workflowService.updateRunWorkflowConfiguration(run.getSimulationId(), input);
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
