package io.openaev.service.autonomous;

import static java.time.Instant.now;
import static java.time.temporal.ChronoUnit.MINUTES;
import static org.springframework.util.StringUtils.hasText;

import com.fasterxml.jackson.core.type.TypeReference;
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
import io.openaev.api.autonomous.dto.ConvertToManualMode;
import io.openaev.api.chaining.dto.ConditionCreateInput;
import io.openaev.api.chaining.dto.WorkflowConfigurationInput;
import io.openaev.api.chaining.dto.WorkflowScopeRuleInput;
import io.openaev.api.xtmone.dto.ChatbotAgentOutput;
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
import io.openaev.database.model.PrimitiveType;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.ScopeRuleSelectedMode;
import io.openaev.database.model.ScopeRuleSource;
import io.openaev.database.model.Setting;
import io.openaev.database.model.Team;
import io.openaev.database.model.TenantSettingKeys;
import io.openaev.database.model.User;
import io.openaev.database.model.Workflow;
import io.openaev.database.model.WorkflowScopeRule;
import io.openaev.database.model.autonomous.AutonomousDirective;
import io.openaev.database.model.autonomous.AutonomousDirectiveStatus;
import io.openaev.database.model.autonomous.AutonomousDiscoveryMode;
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
import io.openaev.database.repository.SettingRepository;
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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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

  /**
   * Slug of the license-independent built-in specialist (payload creator) the orchestrator consults
   * by default. Used to resolve its XTM One id when a tenant has not configured its default agents.
   */
  private static final String BUILTIN_AGENT_SLUG = "openaev-payload-creator";

  /**
   * Reserved key for the orchestrator's own entry in a run's per-agent discovery-mode map. The
   * orchestrator's concrete XTM One id is only resolved at engage time (by the {@code
   * aev.attack_path_orchestrator} intent), so the UI and this service key its mode under this
   * sentinel instead. {@link #resolveDiscoveryMode} falls back to it for any creation not
   * attributed to a known specialist - i.e. the orchestrator acting on its own. Must match {@code
   * ORCHESTRATOR_AGENT_ID} in the frontend's autonomous-types.
   */
  private static final String ORCHESTRATOR_AGENT_ID = "__orchestrator__";

  /**
   * {@code data} payload stamped on a STATUS event that marks the orchestrator as freshly ENGAGED
   * and actively working (start / restart-then-start / resume), as opposed to an end-of-cycle park.
   *
   * <p>The reasoning panel treats the newest STATUS event as a calm "Awaiting the next event" park
   * - correct for a genuine end-of-cycle wait (phishing lure in flight, waiting on a finding), but
   * wrong right after engagement, where the orchestrator can churn for minutes (building arsenal,
   * resolving contracts) before it emits its first DECISION. Without a discriminator the cockpit
   * looked frozen on "Awaiting the next event" for that whole window even though a burst of work
   * had already begun. This marker lets the panel render an active "Getting to work" caption for
   * the engagement window and fall back to the parked caption for a real park. i18n-safe (a
   * structured flag, never matched on the human title).
   */
  private static final String ENGAGED_EVENT_DATA = "{\"phase\":\"engaged\"}";

  /**
   * Default OpenAEV-enforced run timeout when the launcher does not specify one: 24 hours.
   * Autonomous runs are long-lived (recon, waiting on human-in-the-loop injects, slow exploitation)
   * so the default is far larger than the 1h chained-scenario workflow timeout.
   */
  public static final long DEFAULT_TIMEOUT_SECONDS = 24L * 60L * 60L;

  /** Lead time of the first winddown steering nudge before the hard stop (5 minutes). */
  static final long WINDDOWN_5M_SECONDS = 5L * 60L;

  /** Lead time of the final winddown steering nudge before the hard stop (1 minute). */
  static final long WINDDOWN_1M_SECONDS = 60L;

  static final String WINDDOWN_PHASE_5M = "WINDDOWN_5M";
  static final String WINDDOWN_PHASE_1M = "WINDDOWN_1M";

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
  private final SettingRepository settingRepository;

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

  /** A settled run: canceled, completed or failed. Nothing may execute or be authored on it. */
  private static boolean isTerminal(AutonomousRunStatus status) {
    return status == AutonomousRunStatus.CANCELED
        || status == AutonomousRunStatus.COMPLETED
        || status == AutonomousRunStatus.FAILED;
  }

  /**
   * Refuses an orchestrator authoring call against a run that has already ended. A cancel / timeout
   * / settle stops the XTM One orchestrator, but a decision cycle already in flight (or the next
   * one, before it has seen the stop) can still call an authoring tool. Left unguarded, that call
   * both adds steps to a dead run AND - because the authoring path persists the whole run entity to
   * mirror the step - reverts the run's terminal status to active via a stale full-entity save
   * (lost update, this row is not optimistically locked). The read-path reconcile then re-cancels
   * the resurrected run every poll, which is the source of the repeated "Run canceled" cadence.
   * Failing the late author closed keeps the terminal state authoritative.
   */
  private void assertRunAcceptsAuthoring(AutonomousRun run) {
    if (isTerminal(run.getStatus())) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "This autonomous run has ended (" + run.getStatus() + "); it no longer accepts"
              + " attack-path authoring.");
    }
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
    return doCreate(input);
  }

  /**
   * Body of {@link #create}. Kept un-annotated so composite transactional entry points ({@link
   * #launchFromScenario}) can reuse it without the intra-class {@code @Transactional}
   * self-invocation trap (a same-class call bypasses the Spring proxy). Must be called inside an
   * active transaction.
   */
  private AutonomousRun doCreate(AutonomousRunCreateInput input) {
    requireFeature();
    String objective = resolveObjective(input);

    Scenario scenario;
    String scenarioId = input.getScenarioId();
    boolean seededFromScenario = scenarioId != null && !scenarioId.isBlank();
    if (seededFromScenario) {
      // Seed from a caller-provided chaining scenario. Autonomy is a launch-time MODE now, not a
      // scenario type, so the scenario is a plain reusable chained scenario and is NOT stamped
      // autonomous. The AI cockpit is discovered from the run row (by-simulation / by-scenario
      // lookups), not from a flag on the scenario.
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
    // OpenAEV owns the run's lifetime: a live run gets an enforced timeout (default 24h) after
    // which the watchdog steers the orchestrator to converge and then hard-stops it, exactly like
    // an operator Stop. Plan/dry-runs only design the path and are short, so they stay untimed; the
    // absolute deadline is stamped when the run actually goes live (see start()).
    run.setTimeoutSeconds(planMode ? null : resolveTimeoutSeconds(input.getTimeoutSeconds()));
    run.setScenarioId(scenarioId);
    // Durable Normal/Autonomous marker: survives the run-row teardown on rebuild/relaunch, so the
    // simulations history and the simulation hero keep telling the two modes apart afterwards.
    simulation.setAutonomous(true);
    exerciseService.saveSimulation(simulation);
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
    // Specialist agents the orchestrator may consult. The per-run selection is authoritative once
    // the
    // launcher provides one (a non-null list, even empty, means the operator explicitly chose - up
    // to
    // disabling every agent including the built-in). Only when the launcher omits it entirely
    // (null)
    // do we fall back to the tenant's configured default additional agents.
    List<String> selectedAgentIds = input.getAgentIds();
    List<String> resolvedAgentIds =
        selectedAgentIds != null
            ? new ArrayList<>(selectedAgentIds)
            : readDefaultAdditionalAgentIds();
    run.setAgentIds(resolvedAgentIds);
    // Per-agent discovery mode: authoritative per-run selection when provided, else the tenant
    // default map; then normalized so every enabled specialist agent has an explicit mode
    // (defaulting
    // EXPANSIVE - specialists expand the perimeter by default; the orchestrator itself stays
    // SCOPED).
    Map<String, String> selectedModes = input.getAgentModes();
    run.setAgentModes(
        normalizeAgentModes(
            selectedModes != null ? selectedModes : readDefaultAdditionalAgentModes(),
            resolvedAgentIds));
    // Seed-and-adapt: when launched in autonomous mode from a scenario that already carries an
    // authored attack path, hand the orchestrator a starting-plan guidance so it verifies and
    // executes the seeded steps first, then adapts/extends from live findings (unless a prior plan
    // guidance was already carried in, e.g. a promoted dry-run).
    if (seededFromScenario && !planMode && !hasText(run.getPlanGuidance())) {
      run.setPlanGuidance(buildScenarioSeedGuidance(scenario));
    }
    run.setStatus(AutonomousRunStatus.CREATED);
    AutonomousRun saved = runRepository.save(run);

    eventService.append(
        saved.getId(),
        simulation.getId(),
        AutonomousEventType.STATUS,
        planMode ? "Plan created" : "Run created",
        (planMode
            ? "AI builder started from scenario \""
                + scenario.getName()
                + "\". The orchestrator will author the scenario's logic; nothing is executed."
            : "Autonomous run created from scenario \"" + scenario.getName() + "\"."),
        null);
    return saved;
  }

  /**
   * Launches an existing chained scenario in AUTONOMOUS mode and engages the orchestrator to drive
   * it live. This is the scenario-side entry point behind {@code POST
   * /scenarios/{id}/exercise/autonomous}; the plain {@code exercise} endpoint keeps launching a
   * normal (operator-driven) simulation.
   *
   * <p>Autonomy is a launch-time MODE, not a scenario type, so it works whether or not the scenario
   * already has logic:
   *
   * <ul>
   *   <li>If the scenario already has authored steps (built by hand OR by the AI builder), those are
   *       seeded onto the live simulation as the starting attack path - the orchestrator executes
   *       them first, then adapts/extends from live findings.
   *   <li>If the scenario is still empty (no steps), there is nothing to seed - the orchestrator
   *       builds the attack path live "as it goes" from the objective and scope.
   * </ul>
   *
   * <p>Creates the run bound to the scenario and immediately engages the orchestrator in one
   * transaction (the {@code afterCommit} engage still fires once this method's transaction commits,
   * exactly like {@link #start}). No scenario is ever provisioned here.
   */
  @Transactional(rollbackFor = Exception.class)
  public AutonomousRun launchFromScenario(String scenarioId, AutonomousRunCreateInput input) {
    requireFeature();
    if (!hasText(scenarioId)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A scenario id is required");
    }
    if (!workflowService.isScenarioChaining(scenarioId)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "The scenario must define a chaining (attack path) workflow to run autonomously");
    }
    // Relaunching is first-class: a scenario whose previous autonomous run (or plan) has settled
    // can be launched again. The settled run row is superseded so the new run can bind (one run
    // per scenario); a still-active run is refused instead.
    supersedePriorRun(scenarioId, "scenario relaunched in autonomous mode");
    AutonomousRunCreateInput effective = input != null ? input : new AutonomousRunCreateInput();
    effective.setScenarioId(scenarioId);
    effective.setPlanMode(false);
    if (!hasText(effective.getObjective()) && !hasText(effective.getObjectiveTemplateKey())) {
      Scenario scenario = scenarioService.scenario(scenarioId);
      // The default mission depends on whether the scenario already carries any logic: with authored
      // steps the run executes them first then adapts; on an empty scenario there is nothing to
      // execute, so the orchestrator designs and drives the path live ("as it goes").
      boolean hasAuthoredSteps = !workflowService.readAuthoredAttackPathForScenario(scenarioId).isEmpty();
      effective.setObjective(
          hasAuthoredSteps
              ? "Run the chained scenario \""
                  + scenario.getName()
                  + "\" autonomously: verify and execute its authored attack path, then adapt and"
                  + " extend it from live findings to achieve the objective."
              : "Run the chained scenario \""
                  + scenario.getName()
                  + "\" autonomously: it has no predefined logic yet, so design and drive the attack"
                  + " path live - recon, exploitation, lateral movement - adapting to findings and"
                  + " expanding within the authorized scope to achieve the objective.");
    }
    // The un-annotated internals run inside THIS transaction: doCreate() builds + saves the run,
    // doStart() flips it live and registers the post-commit orchestrator engagement. Calling the
    // public @Transactional create()/start() here would self-invoke past the Spring proxy.
    AutonomousRun created = doCreate(effective);
    return doStart(created.getId());
  }

  /**
   * Author-scenario (AI planning) mode: engages the orchestrator to design a reusable chained
   * scenario by writing steps directly onto the scenario's workflow TEMPLATE - no simulation is
   * ever provisioned and nothing is executed. The operator later launches the authored scenario in
   * normal OR autonomous mode. This is the scenario-side entry point behind {@code POST
   * /scenarios/{id}/plan-with-ai}.
   *
   * <p>Build is also REBUILD: it always starts from a blank logic map. Any previously settled run
   * (an earlier plan or a finished live run) is superseded, and the scenario workflow is fully
   * wiped - steps AND event/trigger conditions - before the orchestrator is engaged, so the AI
   * designs the path fresh instead of stacking on top of (or partially colliding with) the previous
   * one. A still-active run is refused with 409.
   */
  @Transactional(rollbackFor = Exception.class)
  public AutonomousRun planScenario(String scenarioId, AutonomousRunCreateInput input) {
    requireFeature();
    if (!hasText(scenarioId)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A scenario id is required");
    }
    if (!workflowService.isScenarioChaining(scenarioId)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "The scenario must be a chained scenario to be planned by the orchestrator");
    }
    // Rebuild support: supersede the previous settled run (if any) so the fresh plan run can bind,
    // then wipe the whole logic map (steps + events/triggers) so the orchestrator starts fresh.
    supersedePriorRun(scenarioId, "plan rebuilt by operator");
    workflowService.deleteAllScenarioSteps(scenarioId);
    AutonomousRunCreateInput effective = input != null ? input : new AutonomousRunCreateInput();
    Scenario scenario = scenarioService.scenario(scenarioId);
    if (!hasText(effective.getObjective()) && !hasText(effective.getObjectiveTemplateKey())) {
      effective.setObjective(
          "Design the attack path for the chained scenario \""
              + scenario.getName()
              + "\": author its steps (recon, exploitation, lateral movement, objective) onto the"
              + " scenario workflow so it can later be launched in normal or autonomous mode.");
    }
    String objective = resolveObjective(effective);

    // Keep the scenario workflow alive so the authoring window survives (no timeout, not ended).
    markWorkflowKeepAlive(scenarioId);

    AutonomousRun run = new AutonomousRun();
    run.setObjective(objective);
    run.setObjectiveTemplateKey(effective.getObjectiveTemplateKey());
    // Author-scenario mode is a dry-run with NO simulation: the orchestrator writes onto the
    // scenario workflow itself. It stays untimed like any plan/dry-run.
    run.setPlanMode(true);
    run.setTimeoutSeconds(null);
    run.setScenarioId(scenarioId);
    run.setSimulationId(null);

    List<AutonomousScopeTarget> scope = resolveScope(effective);
    for (AutonomousScopeTarget target : allowlistTargetsFromRules(effective.getScopeRules())) {
      addScopeIfAbsent(scope, target.getType(), target.getId());
    }
    run.setScope(scope);
    run.setScopeAssetGroupId(firstScopeIdOfType(scope, "ASSETS_GROUPS"));
    run.setScopeTeamId(firstScopeIdOfType(scope, "TEAMS"));
    // Persist any launch-time scope onto the scenario workflow only (no simulation to seed).
    List<WorkflowScopeRuleInput> seededScopeRules = new ArrayList<>();
    if (effective.getScopeRules() != null) {
      for (WorkflowScopeRuleInput rule : effective.getScopeRules()) {
        if (rule != null
            && rule.getSelectedMode() != null
            && rule.getRuleSource() != null
            && hasText(rule.getRuleValue())) {
          seededScopeRules.add(rule);
        }
      }
    }
    seededScopeRules.addAll(toAllowlistScopeInputs(resolveScope(effective)));
    workflowService.writeScopeRules(scenarioId, null, seededScopeRules);

    run.setXtmAgentSlug(effective.getAgentSlug());
    List<String> selectedAgentIds = effective.getAgentIds();
    List<String> resolvedAgentIds =
        selectedAgentIds != null
            ? new ArrayList<>(selectedAgentIds)
            : readDefaultAdditionalAgentIds();
    run.setAgentIds(resolvedAgentIds);
    Map<String, String> selectedModes = effective.getAgentModes();
    run.setAgentModes(
        normalizeAgentModes(
            selectedModes != null ? selectedModes : readDefaultAdditionalAgentModes(),
            resolvedAgentIds));
    run.setStatus(AutonomousRunStatus.CREATED);
    AutonomousRun saved = runRepository.save(run);
    eventService.append(
        saved.getId(),
        null,
        AutonomousEventType.STATUS,
        "Planning started",
        "The orchestrator will design a reusable attack path for scenario \""
            + scenario.getName()
            + "\" by authoring steps onto the scenario workflow. Nothing is executed.",
        null);
    // Flip to PLANNING and engage the orchestrator after commit (same transaction as this method);
    // the un-annotated internal avoids self-invoking the @Transactional start() past the proxy.
    return doStart(saved.getId());
  }

  /** Starting-plan guidance handed to the orchestrator when a scenario is launched autonomously. */
  private String buildScenarioSeedGuidance(Scenario scenario) {
    return "This run was launched from the chained scenario \""
        + scenario.getName()
        + "\". Its authored attack-path steps have been seeded into this simulation as your"
        + " starting plan. Read the current attack-path state first, then verify and execute the"
        + " seeded steps in kill-chain order, and adapt or extend the path from live findings to"
        + " achieve the objective.";
  }

  /**
   * Engages the XTM One orchestrator for a created run. The call is a short fire-and-forget
   * enqueue; the orchestrator then drives OpenAEV back through the platform MCP tools and these
   * callbacks.
   */
  @Transactional(rollbackFor = Exception.class)
  public AutonomousRun start(String runId) {
    return doStart(runId);
  }

  /**
   * Body of {@link #start}. Kept un-annotated so composite transactional entry points ({@link
   * #launchFromScenario}, {@link #planScenario}) can reuse it without the intra-class
   * {@code @Transactional} self-invocation trap (a same-class call bypasses the Spring proxy). Must
   * be called inside an active transaction; the orchestrator engagement it registers fires after
   * that transaction commits.
   */
  private AutonomousRun doStart(String runId) {
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
    stampDeadline(run);
    AutonomousRun saved = runRepository.save(run);
    eventService.append(
        runId,
        run.getSimulationId(),
        AutonomousEventType.STATUS,
        planMode ? "Planning started" : "Run started",
        planMode
            ? "Orchestrator engaged: building the scenario's logic, nothing is executed."
            : "Orchestrator engaged; autonomous execution is now running.",
        ENGAGED_EVENT_DATA);
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
    final String scenarioId = run.getScenarioId();
    // Author-scenario (AI planning) mode: a plan run with no simulation authors directly onto the
    // scenario workflow. XTM One must target the scenario for its attack-path tools, so it is told
    // both the scenario id and that the target is the scenario (not a simulation).
    final boolean authorScenario = run.isPlanMode() && !hasText(simulationId);
    final String scopeAssetGroupId = run.getScopeAssetGroupId();
    final String scopeTeamId = run.getScopeTeamId();
    final List<AutonomousScopeTarget> scope =
        run.getScope() != null ? new ArrayList<>(run.getScope()) : new ArrayList<>();
    final List<String> agentIds =
        run.getAgentIds() != null ? new ArrayList<>(run.getAgentIds()) : new ArrayList<>();
    final Map<String, String> agentModes =
        run.getAgentModes() != null ? new HashMap<>(run.getAgentModes()) : new HashMap<>();
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
                  scenarioId,
                  authorScenario,
                  scopeAssetGroupId,
                  scopeTeamId,
                  scope,
                  scopeMode,
                  planMode,
                  priorPlan,
                  agentIds,
                  agentModes);
            }
          });
    } else {
      engageOrchestratorNow(
          runId,
          agentSlug,
          objective,
          simulationId,
          scenarioId,
          authorScenario,
          scopeAssetGroupId,
          scopeTeamId,
          scope,
          scopeMode,
          planMode,
          priorPlan,
          agentIds,
          agentModes);
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
      String scenarioId,
      boolean authorScenario,
      String scopeAssetGroupId,
      String scopeTeamId,
      List<AutonomousScopeTarget> scope,
      String scopeMode,
      boolean planMode,
      String priorPlan,
      List<String> agentIds,
      Map<String, String> agentModes) {
    try {
      Map<String, Object> handle =
          xtmOneClient.startAutonomousRun(
              agentSlug,
              objective,
              runId,
              simulationId,
              scenarioId,
              authorScenario,
              scopeAssetGroupId,
              scopeTeamId,
              scope,
              scopeMode,
              planMode,
              priorPlan,
              openAEVConfig.getBaseUrl(),
              agentIds,
              agentModes);
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
    // Resuming grants a fresh time budget from now (and re-arms the winddown nudges): a run paused
    // for a while must not be hard-stopped the instant it comes back.
    stampDeadline(run);
    AutonomousRun saved = runRepository.save(run);
    eventService.append(
        runId,
        run.getSimulationId(),
        AutonomousEventType.STATUS,
        "Run resumed",
        null,
        ENGAGED_EVENT_DATA);
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
    // Read what the after-work needs BEFORE the bulk flip below: settleTerminalStatusIfActive is a
    // clearAutomatically UPDATE, so `run` is detached afterwards and a LAZY tenant fetch would blow
    // up.
    String tenantId = run.getTenant().getId();
    String simulationId = run.getSimulationId();
    // Claim the terminal flip with the SAME atomic conditional UPDATE the timeout watchdog and the
    // read-path reconcile use, instead of a plain read-modify-save. An operator Stop, a racing
    // reconcile and the watchdog can all reach a run at once; routing every terminal settle through
    // one row-guarded claim means exactly one of them flips the row and narrates - the plain save
    // this replaced could double-narrate with a concurrent reconcile (the reported duplicate). A
    // blind save also silently reverted a status a concurrent reconcile had already set, so the
    // atomic claim is the correctness fix too.
    int changed =
        runRepository.settleTerminalStatusIfActive(
            runId, tenantId, AutonomousRunStatus.CANCELED, now());
    // Best-effort, not strict: an already-CANCELED/FINISHED simulation must not block the run from
    // settling to CANCELED, otherwise a mid-cancel crash leaves the operator permanently stuck.
    transitionSimulationQuietly(run, ExerciseStatus.CANCELED);
    // Narrate at most once per run life: if a reconcile / watchdog already claimed this flip
    // (changed == 0) or already narrated the run's end, the guard drops this line.
    if (changed > 0) {
      eventService.appendTerminalStatusOnce(runId, simulationId, "Run canceled", null);
    }
    // Stop purges the run's coordination state so a later restart starts clean (re-asks the
    // operator
    // for scope instead of re-reading a stale resolved target / open exploitation task).
    cancelOrchestratorAfterCommit(runId, "run canceled by operator", true);
    // Re-read the freshly-settled row (the bulk UPDATE cleared the persistence context).
    return require(runId);
  }

  // region timeout / winddown (OpenAEV-owned run deadline)

  /**
   * Clamps a requested run timeout to a sane value, defaulting to 24h when unset or non-positive.
   */
  private static long resolveTimeoutSeconds(Long requested) {
    if (requested == null || requested <= 0) {
      return DEFAULT_TIMEOUT_SECONDS;
    }
    return requested;
  }

  /**
   * Stamps the live-start instant and computes the OpenAEV-enforced deadline from the run's
   * timeout. Called whenever the run (re)enters RUNNING (start / resume) so a resumed run gets a
   * fresh budget, and resets the winddown bookkeeping so the steering nudges fire again for the new
   * window. A plan/dry-run stays untimed (deadline cleared); a LIVE run is never left untimed - a
   * missing timeout (a promoted plan whose timeout was nulled at create, or a pre-migration row)
   * falls back to the standard 24h so the watchdog can always hard-stop it. Package-private for
   * unit tests.
   */
  void stampDeadline(AutonomousRun run) {
    Instant startInstant = now();
    run.setStartedAt(startInstant);
    run.setWinddownPhase(null);
    if (run.isPlanMode()) {
      // A dry-run only designs the path: no enforcement, and no stale deadline left behind from a
      // previous live window.
      run.setDeadlineAt(null);
      return;
    }
    if (run.getTimeoutSeconds() == null) {
      run.setTimeoutSeconds(DEFAULT_TIMEOUT_SECONDS);
    }
    run.setDeadlineAt(startInstant.plusSeconds(run.getTimeoutSeconds()));
  }

  /**
   * Applies the OpenAEV-owned deadline policy to a single run, invoked by the timeout watchdog
   * ({@code AutonomousTimeoutJob}) inside the run's tenant scope. Depending on how much time is
   * left it either queues a winddown steering nudge (5 min / 1 min before, once each) or hard-stops
   * the run at the deadline. Only acts on live runs (RUNNING / WAITING_INPUT) that carry a
   * deadline. The load is tenant-scoped ({@code @Filter} does not protect PK lookups): the caller
   * already resolved {@code runId} from a tenant-predicated query, and the read must stay
   * consistent with that scope.
   */
  @Transactional(rollbackFor = Exception.class)
  public void enforceDeadline(String runId, String tenantId) {
    AutonomousRun run = runRepository.findByIdAndTenantId(runId, tenantId).orElse(null);
    if (run == null || run.getDeadlineAt() == null) {
      return;
    }
    AutonomousRunStatus status = run.getStatus();
    if (status != AutonomousRunStatus.RUNNING && status != AutonomousRunStatus.WAITING_INPUT) {
      return;
    }
    long secondsRemaining = Duration.between(now(), run.getDeadlineAt()).getSeconds();
    if (secondsRemaining <= 0) {
      timeoutRun(run);
      return;
    }
    if (secondsRemaining <= WINDDOWN_1M_SECONDS) {
      queueWinddown(run, WINDDOWN_PHASE_1M, secondsRemaining);
    } else if (secondsRemaining <= WINDDOWN_5M_SECONDS) {
      queueWinddown(run, WINDDOWN_PHASE_5M, secondsRemaining);
    }
  }

  /**
   * Hard-stops a run that reached its OpenAEV-enforced deadline. Mirrors an operator Stop: the
   * chained simulation is torn down (best-effort, like {@link #cancel}) and the run settles to
   * CANCELED. The XTM One orchestration is stopped and purged by its own next-cycle stop check,
   * which reads this CANCELED status - OpenAEV is the source of truth for the run lifecycle - so a
   * userless cross-tenant HTTP push from the watchdog is not needed to clean XTM One up.
   *
   * <p>The terminal flip is claimed through an atomic conditional UPDATE, like the read-path
   * reconcile: an operator Stop, a racing reconcile and this watchdog can all reach a run at its
   * deadline, and only the claimer narrates the stop - so the timeline gets exactly one terminal
   * event, never a "Run canceled" plus a "Run timed out" for the same run.
   *
   * <p>The watchdog's flip is restricted to the two live statuses its decision was based on
   * (RUNNING / WAITING_INPUT), not merely "not yet terminal": the deadline was read in this
   * transaction, but the UPDATE may land after a concurrent transition committed. A restart (valid
   * from any status) resets the run to CREATED with a fresh simulation, and a pause parks it -
   * neither must be hard-stopped by a stale deadline claim, so a lost race is a silent no-op here
   * too.
   */
  private void timeoutRun(AutonomousRun run) {
    int changed =
        runRepository.settleTerminalStatusIfLive(
            run.getId(), run.getTenant().getId(), AutonomousRunStatus.CANCELED, now());
    if (changed == 0) {
      // Someone else (operator Stop, reconcile) already settled it; nothing left to narrate.
      return;
    }
    transitionSimulationQuietly(run, ExerciseStatus.CANCELED);
    eventService.appendTerminalStatusOnce(
        run.getId(),
        run.getSimulationId(),
        "Run timed out",
        "The run reached its OpenAEV-enforced deadline and was hard-stopped: the simulation was"
            + " stopped and the orchestration is torn down and cleaned up, exactly like an operator"
            + " Stop.");
  }

  /**
   * Queues a winddown steering nudge on the run's existing operator-directive channel, at most once
   * per phase. The orchestrator consumes pending directives at the start of every decision cycle
   * and is instructed to honour them, so a live (cycling) run picks the nudge up on its next cycle
   * with zero XTM One changes - no wake call (and thus no current-user JWT) is needed from the
   * watchdog.
   */
  private void queueWinddown(AutonomousRun run, String phase, long secondsRemaining) {
    if (phaseAlreadyReached(run.getWinddownPhase(), phase)) {
      return;
    }
    long minutes = Math.max(1, Math.round(secondsRemaining / 60.0));
    String content = winddownMessage(phase, minutes);
    run.setWinddownPhase(phase);
    runRepository.save(run);
    AutonomousDirective directive = new AutonomousDirective();
    directive.setRunId(run.getId());
    directive.setContent(content);
    directive.setStatus(AutonomousDirectiveStatus.PENDING);
    directiveRepository.save(directive);
    eventService.append(
        run.getId(),
        run.getSimulationId(),
        AutonomousEventType.DIRECTIVE,
        WINDDOWN_PHASE_1M.equals(phase)
            ? "Winddown: ~1 minute left"
            : "Winddown: ~" + minutes + " minutes left",
        content,
        null);
  }

  /** Winddown phases are ordered NONE -> 5M -> 1M; never re-send an earlier-or-equal phase. */
  private static boolean phaseAlreadyReached(String current, String candidate) {
    return winddownRank(current) >= winddownRank(candidate);
  }

  private static int winddownRank(String phase) {
    if (WINDDOWN_PHASE_1M.equals(phase)) {
      return 2;
    }
    if (WINDDOWN_PHASE_5M.equals(phase)) {
      return 1;
    }
    return 0;
  }

  private static String winddownMessage(String phase, long minutes) {
    if (WINDDOWN_PHASE_1M.equals(phase)) {
      return "TIME BUDGET - FINAL WARNING: about 1 minute remains before OpenAEV hard-stops this"
          + " run. Stop authoring new steps immediately. Record proof/findings for the progress you"
          + " have made and set the run status now: COMPLETED if the objective is proven achieved"
          + " (or provably unreachable), otherwise summarize what you reached. No new work.";
    }
    return "TIME BUDGET - WINDDOWN: about "
        + minutes
        + " minutes remain before OpenAEV hard-stops this run. Begin converging on the objective"
        + " now: stop opening new branches, finish and capture proof for the most promising path,"
        + " and prepare to report. If the objective is already proven (or provably unreachable),"
        + " set the run COMPLETED before the deadline.";
  }

  // endregion

  /**
   * Restarts a run <b>in place</b>, from ANY status: reuses the SAME scenario, tears down the
   * previous simulation (attack-path rows included) and the run's decision timeline + steering
   * directives, provisions a fresh simulation from that scenario, and resets the run to {@code
   * CREATED}. The caller then {@link #start}s it again. This keeps the invariant "one scenario ==
   * one run == one live simulation" instead of spawning a brand-new scenario on every restart, and
   * gives the cockpit (overview, attack-path graph, reasoning panel) a clean slate to animate from
   * scratch.
   *
   * <p>Restart is a deliberate, operator-triggered HARD RESET, so it is valid from any status, not
   * just a settled one: the teardown stops an active run cleanly, and restarting a live run is
   * simply a stop-then-restart - which is what the operator expects when they click Restart. The
   * previous "settled only" guard rejected any non-terminal status with a 409, which the cockpit
   * surfaced as the misleading "The element already exists" toast whenever Restart was offered on a
   * run the backend still considered active (parked in WAITING_INPUT / RUNNING, or a status desync
   * after repeated start/stop cycles). Re-planning a settled dry-run plan (e.g. after the operator
   * filled a capability gap or steered the scope) is the same reset - a plan-mode restart
   * re-provisions the TEMPLATE workflow so the AI rebuilds the plan from scratch.
   */
  @Transactional(rollbackFor = Exception.class)
  public AutonomousRun restart(String runId) {
    requireFeature();
    AutonomousRun run = require(runId);
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
    // Clear EVERY step on the scenario workflow (the seed the fresh simulation is copied from), not
    // just the ones tracked in the run's best-effort stepMirror. An un-mirrored step (a swallowed
    // mirror failure or a lost update on the run row) would otherwise survive and re-seed the
    // restarted simulation, leaving the Logic tab and attack-path map populated after a reset the
    // operator expected to wipe. The autonomous scenario is AI-owned and launches empty, so a full
    // clear is safe and is exactly the "fully recreate on launch" behaviour operators expect.
    workflowService.deleteAllScenarioSteps(run.getScenarioId());
    run.setStepMirror(new HashMap<>());
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
    simulation.setAutonomous(true);
    exerciseService.saveSimulation(simulation);
    run.setSimulationId(simulation.getId());
    run.setStatus(AutonomousRunStatus.CREATED);
    run.setLastError(null);
    run.setXtmSessionId(null);
    // The previous live window's time budget is void after a hard reset: the follow-up start()
    // stamps a fresh one, and a CREATED run must never sit with a stale (possibly past) deadline
    // from the life it just shed.
    run.setStartedAt(null);
    run.setDeadlineAt(null);
    run.setWinddownPhase(null);
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
          HttpStatus.CONFLICT, "Only built (non-executed) logic can be launched as a live run");
    }
    if (run.getStatus() != AutonomousRunStatus.PLANNED
        && run.getStatus() != AutonomousRunStatus.FAILED
        && run.getStatus() != AutonomousRunStatus.CANCELED) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "A plan can only be launched once building has settled");
    }
    // Stop the planning orchestration and purge its coordination state so the live run does not
    // inherit the plan cycle's assumptions.
    cancelOrchestratorAfterCommit(runId, "plan promoted to a real run by operator", true);
    if (hasText(run.getSimulationId())) {
      exerciseService.deleteById(run.getSimulationId());
    }
    Scenario scenario = scenarioService.scenario(run.getScenarioId());
    // Clear EVERY step on the scenario workflow so the promoted (fresh) simulation starts with an
    // empty attack path and the orchestrator rebuilds it live (guided by planGuidance) rather than
    // replaying - or duplicating - the plan. A full clear rather than the best-effort stepMirror,
    // so
    // an un-mirrored plan step can't survive and re-seed the live simulation.
    workflowService.deleteAllScenarioSteps(run.getScenarioId());
    run.setStepMirror(new HashMap<>());
    markWorkflowKeepAlive(run.getScenarioId());
    Exercise simulation =
        scenarioToExerciseService.toExercise(
            scenario, now().truncatedTo(MINUTES).plus(1, MINUTES), true);
    try {
      workflowService.startWorkflowByScenarioIdAndSimulation(run.getScenarioId(), simulation);
    } catch (ChainingException e) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Failed to start the live run: " + e.getMessage(), e);
    }
    directiveRepository.deleteByRunId(runId);
    eventService.deleteByRun(runId);
    simulation.setAutonomous(true);
    exerciseService.saveSimulation(simulation);
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
        "Plan launched as a live run",
        "The planned logic was launched as a live autonomous run; a fresh executing simulation was"
            + " provisioned. The orchestrator will follow the plan as closely as possible while"
            + " adapting to live findings.",
        null);
    return saved;
  }

  /**
   * Converts an autonomous (AI-driven) scenario into a plain MANUAL chained scenario the operator
   * can edit by hand, in one of two modes.
   *
   * <p>{@code DUPLICATE} (safe, non-destructive): copies the scenario's metadata + chaining
   * workflow (steps + configuration, keep-alive forced off) into a brand-new manual chained
   * scenario and returns it. The original autonomous run - its scenario, simulation, timeline and
   * XTM One orchestration - is left completely untouched, so the operator gets an editable copy
   * without giving up the live cockpit.
   *
   * <p>{@code IN_PLACE} (irreversible): turns THIS scenario manual. The XTM One orchestration is
   * force-halted and purged, the {@code autonomous_runs} row and its decision timeline / directives
   * are deleted, the scenario's {@code autonomous} flag is cleared and its workflow's keep-alive is
   * turned off. The scenario keeps its authored attack-path steps (now an editable manual logic
   * map) and its underlying simulation is preserved. Because every autonomous lock keys off the
   * presence of the run row, dropping it is exactly what turns that simulation back into a normal
   * chained simulation the operator can edit and delete. There is no way back to autonomous mode.
   *
   * <p>Works whether the run is a dry-run plan or has already executed / is live: force-halting the
   * orchestration (as the bulk scenario-delete path does) is what makes it safe to convert at any
   * point, so the operator never has to Stop the run first.
   *
   * @param runId the autonomous run to convert
   * @param mode IN_PLACE (irreversible flip of this scenario) or DUPLICATE (new manual copy)
   * @return the resulting manual chained scenario (this scenario for IN_PLACE, the new copy for
   *     DUPLICATE)
   */
  @Transactional(rollbackFor = Exception.class)
  public Scenario convertToManual(String runId, ConvertToManualMode mode) {
    requireFeature();
    AutonomousRun run = require(runId);
    String scenarioId = run.getScenarioId();
    if (!hasText(scenarioId)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "This autonomous run has no scenario to convert");
    }
    if (mode == ConvertToManualMode.DUPLICATE) {
      // Non-destructive: clone the scenario into a fresh manual chained scenario and leave the AI
      // run fully intact. getDuplicateScenario returns a persisted scenario whose autonomous flag
      // defaults to false; we then copy the attack-path workflow (steps + config) with keep-alive
      // forced off so the copy runs-and-ends like a hand-built chained scenario.
      Scenario duplicate = scenarioService.getDuplicateScenario(scenarioId);
      try {
        workflowService.copyScenarioChainingWorkflowAsManual(scenarioId, duplicate);
      } catch (ChainingException e) {
        // ChainingException is a generic internal wrapper; keep its detail in the logs and hand the
        // client a stable, safe message instead of leaking the raw cause.
        log.warn(
            "Failed to copy attack-path logic from scenario {} into duplicate {} on convert-to-manual",
            scenarioId,
            duplicate.getId(),
            e);
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Failed to copy the attack-path logic into the new scenario",
            e);
      }
      return duplicate;
    }
    // IN_PLACE (irreversible). Halt + purge the orchestration first so no lingering decision cycle
    // keeps authoring against a scenario that is about to be manual.
    cancelOrchestratorAfterCommit(runId, "converted to a manual chained scenario", true);
    // Converting a still-active run leaves its simulation parked in a keep-alive RUNNING state
    // with no orchestrator left to ever feed or end it - settle it exactly like a cancel would.
    // Settled runs (planned / completed / failed / canceled) already left the simulation in a
    // scheduled or terminal state, so there is nothing to transition.
    switch (run.getStatus()) {
      case CREATED, PLANNING, RUNNING, PAUSED, WAITING_INPUT ->
          transitionSimulationQuietly(run, ExerciseStatus.CANCELED);
      default -> {
        // Already settled - leave the simulation state untouched.
      }
    }
    Scenario scenario = scenarioService.scenario(scenarioId);
    scenario.setAutonomous(false);
    scenarioService.updateScenario(scenario);
    // A manual chained scenario runs-and-ends: drop the "park forever awaiting the orchestrator"
    // contract, or a launched run would hang open indefinitely with no orchestrator to feed it.
    workflowService.clearScenarioWorkflowKeepAlive(scenarioId);
    // Drop the AI run + its decision history. The simulation is intentionally KEPT (it becomes a
    // normal chained simulation) - removing the run row is precisely what unlocks it for edit and
    // delete on both the API and the UI.
    directiveRepository.deleteByRunId(runId);
    eventService.deleteByRun(runId);
    runRepository.delete(run);
    return scenario;
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

  /**
   * Supersedes the previous autonomous run bound to {@code scenarioId}, if any, so a rebuild (AI
   * builder Build) or a fresh autonomous launch can bind a new run without violating the
   * one-run-per-scenario invariant behind the by-scenario lookups. A still-active run (created /
   * planning / running / paused / waiting-input) is refused with 409 - the operator must stop it
   * first, mirroring the hero which hides the launch and build actions while a run is active.
   *
   * <p>A settled run is torn down like {@link #tearDownRun} except for its simulation: any
   * lingering XTM One orchestration is halted and purged, the decision timeline and steering
   * directives are deleted, and the run row is removed. A leftover plan-mode simulation (the legacy
   * non-executing dry-run substrate) is deleted with it, but a finished LIVE simulation is
   * deliberately KEPT: with the run row gone it is a plain chained simulation of the scenario and
   * stays as history, consistent with "a scenario can carry many simulations" (relaunching must
   * never destroy the previous run's results).
   */
  private void supersedePriorRun(String scenarioId, String reason) {
    supersedePriorRun(scenarioId, reason, true);
  }

  /**
   * Supersede overload with control over the still-active case. {@code refuseIfActive = true} is the
   * AI relaunch / rebuild contract (a live run blocks the new one with a 409); {@code false} is the
   * normal-launch contract, where an active run is left untouched as a defensive no-op rather than
   * torn down (see {@link #supersedeSettledRunOnManualLaunch}).
   */
  private void supersedePriorRun(String scenarioId, String reason, boolean refuseIfActive) {
    AutonomousRun prior = runRepository.findByScenarioId(scenarioId).orElse(null);
    if (prior == null) {
      return;
    }
    prior = reconcileWithSimulation(prior);
    AutonomousRunStatus status = prior.getStatus();
    if (status == AutonomousRunStatus.CREATED
        || status == AutonomousRunStatus.PLANNING
        || status == AutonomousRunStatus.RUNNING
        || status == AutonomousRunStatus.PAUSED
        || status == AutonomousRunStatus.WAITING_INPUT) {
      if (refuseIfActive) {
        throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "Stop the active autonomous run before rebuilding or relaunching this scenario");
      }
      // A normal (operator-driven) launch must never tear down a live orchestration: leave the
      // active run intact (the hero already hides the manual launch while a run is active).
      return;
    }
    String priorId = prior.getId();
    cancelOrchestratorAfterCommit(priorId, reason, true);
    if (prior.isPlanMode() && hasText(prior.getSimulationId())) {
      exerciseService.deleteById(prior.getSimulationId());
    }
    directiveRepository.deleteByRunId(priorId);
    eventService.deleteByRun(priorId);
    runRepository.delete(prior);
  }

  /**
   * A plain, operator-driven ("normal") launch of a chained scenario makes any AI outcome a
   * previous autonomous run left on the scenario stale: the latest run is now the manual one, so
   * the scenario overview and hero must revert to the normal (non-AI) view instead of keeping a
   * settled plan / run outcome, decision timeline and status chip on display. This supersedes
   * (tears down + unbinds) a SETTLED autonomous run bound to {@code scenarioId} so the by-scenario
   * lookup 404s and the manual overview returns - a plan-mode dry-run is discarded with its
   * throwaway substrate simulation, while a finished LIVE run keeps its simulation as history (see
   * {@link #supersedePriorRun}). The scenario's authored attack-path steps are never touched: it is
   * a normal chained scenario the operator just launched.
   *
   * <p>A no-op when the scenario carries no run, or the feature is off. A still-active run is left
   * untouched (never a 409): the hero hides the manual launch while a run is active, so this is only
   * ever reached for a settled run, and a normal launch must never tear down a live orchestration.
   */
  @Transactional(rollbackFor = Exception.class)
  public void supersedeSettledRunOnManualLaunch(String scenarioId) {
    if (!previewFeatureService.isAutonomousAttackPathEnabled() || !hasText(scenarioId)) {
      return;
    }
    supersedePriorRun(scenarioId, "scenario relaunched as a normal simulation", false);
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
    // A terminal status is final: CANCELED (operator Stop / OpenAEV timeout hard-stop) and
    // COMPLETED / FAILED (settled orchestration) all tear the run down. A late status callback from
    // the orchestrator - a COMPLETED it emits after we already hard-stopped, the gap-1 completion
    // backstop racing the watchdog, or a stray cycle that kept running after cancel - must NOT
    // resurrect a settled run. Treat any further write as an idempotent no-op so the terminal state
    // stays authoritative (this is what stops a canceled run from flipping back to active and being
    // re-canceled by the reconcile on the next read, spamming "Run canceled").
    if (run.getStatus() == AutonomousRunStatus.CANCELED
        || run.getStatus() == AutonomousRunStatus.COMPLETED
        || run.getStatus() == AutonomousRunStatus.FAILED) {
      return run;
    }
    run.setStatus(status);
    if (lastError != null) {
      run.setLastError(lastError);
    }
    // A dry-run that reaches PLANNED captures the orchestrator's plan summary as guidance, so
    // promoting it to a real run can hand the plan to the live orchestrator ("follow but adapt").
    if (status == AutonomousRunStatus.PLANNED && hasText(content)) {
      run.setPlanGuidance(content);
    }
    // Reflect a terminal/settled orchestrator decision onto the chained simulation so both stay
    // consistent. A plan-mode run needs the DIRECT finish: its simulation is the RUNNING authoring
    // substrate whose RUN workflow is never started, so no scheduler ever closes it, and
    // changeExerciseStatus refuses RUNNING -> FINISHED (only the scheduler sets FINISHED). Left
    // alone it stays "On-going" forever once the plan is ready. PLANNED is the normal settled
    // state;
    // COMPLETED / FAILED can also end a plan (e.g. it could not be designed).
    if (run.isPlanMode()) {
      if (status == AutonomousRunStatus.PLANNED
          || status == AutonomousRunStatus.COMPLETED
          || status == AutonomousRunStatus.FAILED) {
        finishSimulationTerminallyQuietly(run);
      }
    } else if (status == AutonomousRunStatus.COMPLETED || status == AutonomousRunStatus.FAILED) {
      // A live autonomous simulation is a RUNNING keep-alive chaining workflow, and RUNNING ->
      // FINISHED is not an allowed manual exercise transition (scheduler-only) - so the previous
      // transitionSimulationQuietly(FINISHED) silently threw and was swallowed, leaving a COMPLETED
      // run's simulation stuck "On-going" forever. Finish it with the direct terminal set instead.
      finishSimulationTerminallyQuietly(run);
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
    assertRunAcceptsAuthoring(run);
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
  @Transactional(rollbackFor = Exception.class)
  public String appendAttackPathStep(
      String runId, InjectInput injectInput, String parentStepTemplateId) {
    return doAppendAttackPathStep(runId, injectInput, parentStepTemplateId, null);
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
    return doAppendAttackPathStep(runId, injectInput, parentStepTemplateId, trigger);
  }

  // Shared body for both appendAttackPathStep overloads. Private and non-transactional on purpose:
  // both public overloads are @Transactional entry points that delegate here, so a single proxied
  // transaction wraps the authoring. One overload self-invoking the other would bypass the Spring
  // proxy (no transaction, no tenant scope) - which is exactly what the architecture rule forbids.
  private String doAppendAttackPathStep(
      String runId,
      InjectInput injectInput,
      String parentStepTemplateId,
      AutonomousStepTrigger trigger) {
    requireFeature();
    AutonomousRun run = require(runId);
    assertRunAcceptsAuthoring(run);
    List<ConditionCreateInput> triggerConditions = toTriggerConditions(trigger);
    // Author-scenario (AI planning) mode: no simulation exists, so the orchestrator authors the
    // step directly onto the scenario's workflow TEMPLATE. Nothing executes; there is no mirror
    // (the scenario IS the authored artifact) and the returned id is the scenario step id the
    // orchestrator chains the next step onto.
    if (!hasText(run.getSimulationId())) {
      if (!hasText(run.getScenarioId())) {
        throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "The autonomous run has neither a live simulation nor a scenario to build steps on");
      }
      String scenarioStepId;
      try {
        scenarioStepId =
            workflowService.appendChainedStepToScenario(
                run.getScenarioId(), injectInput, parentStepTemplateId, triggerConditions);
      } catch (ChainingException e) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Failed to author the attack-path step onto the scenario: " + e.getMessage(),
            e);
      }
      boolean findingDrivenPlan = !triggerConditions.isEmpty();
      eventService.append(
          runId,
          null,
          AutonomousEventType.TOOL_ACTION,
          "Attack-path step authored",
          "A chained step was added to the scenario's attack path"
              + (findingDrivenPlan
                  ? " (fires on a finding and consumes its values)."
                  : hasText(parentStepTemplateId)
                      ? " (depends on a previous step)."
                      : " (seed step - readies immediately against the scope)."),
          null);
      return scenarioStepId;
    }
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
      // Name the root condition: it is the EVENT node in the Logic graph, and an unnamed root shows
      // as "Untitled event", which makes the authored logic unreadable. Prefer the orchestrator's
      // explicit event_name; otherwise derive a readable one from the filters/mappings so an event
      // is NEVER untitled even when the orchestrator forgets to name it.
      String eventName = deriveEventName(trigger, filters, mappings);
      conditions.add(
          ConditionCreateInput.builder()
              .temporaryId(TRIGGER_ROOT_TMP_ID)
              .type(rootType)
              .name(eventName)
              .build());
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

  /** Friendly nouns for the finding primitives a trigger commonly fires on, for event naming. */
  private static final Map<PrimitiveType, String> EVENT_NAME_NOUNS =
      Map.ofEntries(
          Map.entry(PrimitiveType.Port, "Open port"),
          Map.entry(PrimitiveType.Host, "Host"),
          Map.entry(PrimitiveType.IPv4, "IP address"),
          Map.entry(PrimitiveType.IPv6, "IPv6 address"),
          Map.entry(PrimitiveType.CVE, "CVE"),
          Map.entry(PrimitiveType.Username, "Username"),
          Map.entry(PrimitiveType.AdminUsername, "Admin account"),
          Map.entry(PrimitiveType.Password, "Credentials"),
          Map.entry(PrimitiveType.Hash, "Credential hash"),
          Map.entry(PrimitiveType.ShareName, "SMB share"),
          Map.entry(PrimitiveType.Service, "Service"),
          Map.entry(PrimitiveType.Severity, "Finding"),
          Map.entry(PrimitiveType.ComputerName, "Computer"),
          Map.entry(PrimitiveType.KerberoastableAccount, "Kerberoastable account"),
          Map.entry(PrimitiveType.AsreproastableAccount, "AS-REP roastable account"),
          Map.entry(PrimitiveType.FileName, "File"),
          Map.entry(PrimitiveType.FilePath, "File"),
          Map.entry(PrimitiveType.Domain, "Domain"),
          Map.entry(PrimitiveType.Email, "Email"));

  /**
   * Resolves the display name of the EVENT node a trigger becomes in the Logic graph. Prefers the
   * orchestrator's explicit {@code event_name}; otherwise derives a short, readable phrase from the
   * primitives the trigger fires on (its filters, or its mappings when it has none) so a
   * finding-driven step is never surfaced as "Untitled event". Falls back to a generic label when
   * nothing usable is present.
   */
  private String deriveEventName(
      AutonomousStepTrigger trigger,
      List<AutonomousTriggerFilter> filters,
      List<AutonomousInputMapping> mappings) {
    if (trigger != null && hasText(trigger.getEventName())) {
      return trigger.getEventName().trim();
    }
    List<PrimitiveType> keyTypes =
        filters.stream()
            .filter(f -> f != null && f.getKeyType() != null)
            .map(AutonomousTriggerFilter::getKeyType)
            .distinct()
            .toList();
    if (keyTypes.isEmpty()) {
      keyTypes =
          mappings.stream()
              .filter(m -> m != null && m.getKeyType() != null)
              .map(AutonomousInputMapping::getKeyType)
              .distinct()
              .toList();
    }
    List<String> nouns = keyTypes.stream().limit(2).map(this::eventNounFor).distinct().toList();
    if (nouns.isEmpty()) {
      return "Finding available";
    }
    return String.join(" + ", nouns) + " found";
  }

  /** Friendly noun for a finding primitive (falls back to a de-underscored label). */
  private String eventNounFor(PrimitiveType keyType) {
    String noun = EVENT_NAME_NOUNS.get(keyType);
    if (noun != null) {
      return noun;
    }
    String label = keyType.label.replace('_', ' ').trim();
    if (label.isEmpty()) {
      return "Finding";
    }
    return Character.toUpperCase(label.charAt(0)) + label.substring(1);
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
    assertRunAcceptsAuthoring(run);
    // Author-scenario mode: the step id IS a scenario step template id; update it in place on the
    // scenario workflow (no simulation, no mirror twin).
    if (!hasText(run.getSimulationId())) {
      if (!hasText(run.getScenarioId())) {
        throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "The autonomous run has neither a live simulation nor a scenario to update steps on");
      }
      try {
        workflowService.updateChainedStep(stepTemplateId, injectInput);
      } catch (ChainingException e) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Failed to update the scenario attack-path step: " + e.getMessage(),
            e);
      }
      eventService.append(
          runId,
          null,
          AutonomousEventType.TOOL_ACTION,
          "Attack-path step updated",
          "An existing chained step was updated in place on the scenario (no new step created).",
          null);
      return stepTemplateId;
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
    // Author-scenario (AI planning) runs have no simulation to enable players on - nothing is
    // delivered while planning, so this is a no-op.
    if (!hasText(simulationId)) {
      return;
    }
    // Shared with the manual chaining execution path via ExerciseService so both keep one source of
    // truth (idempotent: attaches each team to the simulation if missing, then enables its
    // players).
    exerciseService.enableTargetedTeamMembers(simulationId, teamIds);
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
    // Author-scenario mode: read the steps authored onto the scenario workflow (no simulation).
    if (!hasText(run.getSimulationId())) {
      if (!hasText(run.getScenarioId())) {
        return List.of();
      }
      return workflowService.readAuthoredAttackPathForScenario(run.getScenarioId()).stream()
          .map(this::toStepState)
          .toList();
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
  public AutonomousPromotedAssetResult promoteFindingToAsset(
      String runId, String findingId, String actingAgentId) {
    requireFeature();
    AutonomousRun run = require(runId);
    Finding finding =
        findingRepository
            .findById(findingId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Finding not found"));
    // Promotion mints a brand-new endpoint, so it is a discovery-creation gated by the acting
    // agent's mode: EXISTING_ONLY forbids it outright, SCOPED requires the discovered value to sit
    // inside the run's allow-scope perimeter, EXPANSIVE allows it anywhere (deny-list still wins).
    AutonomousDiscoveryMode mode = resolveDiscoveryMode(run, actingAgentId);
    if (!mode.allowsCreation()) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN,
          "Discovery mode EXISTING_ONLY forbids promoting a finding into a new asset. Target an"
              + " existing in-scope endpoint instead.");
    }
    if (!isValueWithinScope(run, finding.getValue(), mode)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN,
          "Discovery mode SCOPED: '"
              + finding.getValue()
              + "' is outside the run's allow-scope perimeter. Widen the scope, or assign this"
              + " agent the EXPANSIVE mode to let it attack beyond the initial perimeter.");
    }
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
      String runId, List<String> playerIds, String name, String teamId, String actingAgentId) {
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
    // Wrapping people the run was not authorized to touch would silently expand the human
    // perimeter. EXISTING_ONLY and SCOPED both require every recipient to already sit inside the
    // run's identity allow-scope (an allow-listed person, or a member of an allow-listed team);
    // only EXPANSIVE may reach beyond it. If the run has no identity perimeter at all, nothing is
    // constrained (matching OpenAEV's "empty allow-list = no restriction" scope semantics).
    AutonomousDiscoveryMode teamMode = resolveDiscoveryMode(run, actingAgentId);
    if (teamMode.requiresInScope() || !teamMode.allowsCreation()) {
      List<String> outOfScope = playersOutsideAllowScope(run, players);
      if (!outOfScope.isEmpty()) {
        throw new ResponseStatusException(
            HttpStatus.FORBIDDEN,
            "Discovery mode "
                + teamMode
                + ": "
                + outOfScope.size()
                + " recipient(s) are outside the run's allow-scope. Add them to the scope, or"
                + " assign this agent the EXPANSIVE mode to target people beyond the perimeter.");
      }
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

  // region discovery policy

  /**
   * Resolves the discovery mode to enforce for a creation attempt, keyed to the agent on whose
   * behalf the discovery is being recorded (passed by XTM One). An agent absent from the run's
   * per-agent map - including an unattributed call - resolves to the safe middle ({@link
   * AutonomousDiscoveryMode#DEFAULT}).
   */
  private AutonomousDiscoveryMode resolveDiscoveryMode(AutonomousRun run, String actingAgentId) {
    Map<String, String> modes = run.getAgentModes();
    if (modes != null) {
      if (hasText(actingAgentId)) {
        String raw = modes.get(actingAgentId.trim());
        if (hasText(raw)) {
          return AutonomousDiscoveryMode.fromValue(raw);
        }
      }
      // Unattributed, or attributed to an actor not in the per-agent map, means the orchestrator
      // itself is recording the discovery (it is the only actor that writes to OpenAEV; specialists
      // are advisory and always carry their own id). Fall back to the orchestrator's own configured
      // mode, keyed by the reserved ORCHESTRATOR_AGENT_ID sentinel, when the operator set one.
      String orchestratorMode = modes.get(ORCHESTRATOR_AGENT_ID);
      if (hasText(orchestratorMode)) {
        return AutonomousDiscoveryMode.fromValue(orchestratorMode);
      }
    }
    return AutonomousDiscoveryMode.DEFAULT;
  }

  /**
   * True when a discovered network value (IP or hostname) may be brought into the run under {@code
   * mode}. EXPANSIVE always passes (deny-list still wins); SCOPED requires the value to match the
   * run's network allow-perimeter when one is defined - if the scope is entity-based only (no
   * MANUAL/CSV network rules) there is no network perimeter to be inside, so it passes.
   */
  private boolean isValueWithinScope(
      AutonomousRun run, String value, AutonomousDiscoveryMode mode) {
    if (!mode.requiresInScope()) {
      // EXPANSIVE: only an explicit deny-list rule blocks the value.
      return !matchesNetworkRules(readScopeRules(run), ScopeRuleSelectedMode.DENYLIST, value);
    }
    List<WorkflowScopeRule> rules = readScopeRules(run);
    if (matchesNetworkRules(rules, ScopeRuleSelectedMode.DENYLIST, value)) {
      return false;
    }
    boolean hasNetworkAllow =
        rules.stream()
            .anyMatch(
                r ->
                    r != null
                        && r.getSelectedMode() == ScopeRuleSelectedMode.ALLOWLIST
                        && isNetworkSource(r.getRuleSource()));
    // No network perimeter defined: an entity-based / empty allow-list does not constrain a raw
    // host, so SCOPED does not block it (mirrors "empty allow-list = no restriction").
    return !hasNetworkAllow || matchesNetworkRules(rules, ScopeRuleSelectedMode.ALLOWLIST, value);
  }

  /** The subset of {@code players} that fall outside the run's identity allow-scope, by id. */
  private List<String> playersOutsideAllowScope(AutonomousRun run, List<User> players) {
    List<WorkflowScopeRule> rules = readScopeRules(run);
    List<WorkflowScopeRule> allow =
        rules.stream()
            .filter(r -> r != null && r.getSelectedMode() == ScopeRuleSelectedMode.ALLOWLIST)
            .toList();
    Set<String> allowPlayerIds = new HashSet<>(idsForSource(allow, ScopeRuleSource.PLAYER));
    List<String> allowTeamIds = idsForSource(allow, ScopeRuleSource.TEAM);
    boolean hasIdentityPerimeter = !allowPlayerIds.isEmpty() || !allowTeamIds.isEmpty();
    if (!hasIdentityPerimeter) {
      // No identity allow-list at all: no perimeter to be outside of.
      return List.of();
    }
    Set<String> allowedMembers = new HashSet<>(allowPlayerIds);
    if (!allowTeamIds.isEmpty()) {
      teamRepository
          .findAllById(allowTeamIds)
          .forEach(team -> team.getUsers().forEach(user -> allowedMembers.add(user.getId())));
    }
    return players.stream()
        .map(User::getId)
        .filter(id -> !allowedMembers.contains(id))
        .distinct()
        .toList();
  }

  private boolean isNetworkSource(ScopeRuleSource source) {
    return source == ScopeRuleSource.MANUAL || source == ScopeRuleSource.CSV;
  }

  /** True when {@code value} matches any MANUAL/CSV rule of the given mode (exact or IPv4 CIDR). */
  private boolean matchesNetworkRules(
      List<WorkflowScopeRule> rules, ScopeRuleSelectedMode mode, String value) {
    if (!hasText(value)) {
      return false;
    }
    for (WorkflowScopeRule rule : rules) {
      if (rule == null
          || rule.getSelectedMode() != mode
          || !isNetworkSource(rule.getRuleSource())
          || !hasText(rule.getRuleValue())) {
        continue;
      }
      if (networkValueMatches(rule.getRuleValue().trim(), value.trim())) {
        return true;
      }
    }
    return false;
  }

  /** Matches a scope rule value against a candidate: case-insensitive exact, or IPv4-in-CIDR. */
  private boolean networkValueMatches(String ruleValue, String candidate) {
    if (ruleValue.equalsIgnoreCase(candidate)) {
      return true;
    }
    if (ruleValue.contains("/")) {
      return ipv4InCidr(candidate, ruleValue);
    }
    return false;
  }

  /** Minimal IPv4 CIDR containment check; returns false for anything it cannot parse as IPv4. */
  private boolean ipv4InCidr(String ip, String cidr) {
    try {
      String[] parts = cidr.split("/");
      if (parts.length != 2) {
        return false;
      }
      long base = ipv4ToLong(parts[0]);
      long addr = ipv4ToLong(ip);
      if (base < 0 || addr < 0) {
        return false;
      }
      int prefix = Integer.parseInt(parts[1].trim());
      if (prefix < 0 || prefix > 32) {
        return false;
      }
      long mask = prefix == 0 ? 0L : (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
      return (base & mask) == (addr & mask);
    } catch (RuntimeException e) {
      return false;
    }
  }

  /** IPv4 dotted-quad to unsigned long, or -1 when not a valid IPv4 literal. */
  private long ipv4ToLong(String ip) {
    String[] octets = ip.trim().split("\\.");
    if (octets.length != 4) {
      return -1;
    }
    long value = 0;
    for (String octet : octets) {
      int n;
      try {
        n = Integer.parseInt(octet);
      } catch (NumberFormatException e) {
        return -1;
      }
      if (n < 0 || n > 255) {
        return -1;
      }
      value = (value << 8) | n;
    }
    return value;
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

  /**
   * Read the autonomous-run configuration an operator saved on a chained scenario (the AI builder's
   * "Save for later"), or {@code null} when nothing has been saved. Lets the AI builder drawer
   * pre-fill from the last configuration so the operator can review, then Build or Launch.
   */
  @Transactional(readOnly = true)
  public AutonomousRunCreateInput getScenarioAutonomousConfig(String scenarioId) {
    requireFeature();
    if (!hasText(scenarioId)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A scenario id is required");
    }
    Scenario scenario = scenarioService.scenario(scenarioId);
    Map<String, Object> stored = scenario.getAutonomousConfig();
    if (stored == null || stored.isEmpty()) {
      return null;
    }
    return objectMapper.convertValue(stored, AutonomousRunCreateInput.class);
  }

  /**
   * Persist an autonomous-run configuration on a chained scenario WITHOUT starting a run - the AI
   * builder's "Save for later". The scenario stays a normal, editable chained scenario; the config
   * is only used to pre-fill the builder and to seed a later Build (plan) or Launch. Rejects
   * time-based scenarios, which can never carry an attack path.
   */
  @Transactional(rollbackFor = Exception.class)
  public AutonomousRunCreateInput saveScenarioAutonomousConfig(
      String scenarioId, AutonomousRunCreateInput input) {
    requireFeature();
    if (!hasText(scenarioId)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A scenario id is required");
    }
    if (!workflowService.isScenarioChaining(scenarioId)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Only a chained scenario can carry an autonomous-run configuration");
    }
    Scenario scenario = scenarioService.scenario(scenarioId);
    if (input == null) {
      scenario.setAutonomousConfig(null);
    } else {
      // Never persist the caller's plan/scenario wiring - the config is scenario-scoped and its run
      // mode (Build vs Launch) is decided at action time, not saved.
      input.setScenarioId(null);
      input.setPlanMode(false);
      scenario.setAutonomousConfig(
          objectMapper.convertValue(input, new TypeReference<Map<String, Object>>() {}));
    }
    scenarioService.updateScenario(scenario);
    return input;
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

  /**
   * Specialist agents the orchestrator can consult, sourced from the XTM One {@code
   * aev.attack_path_additional_agent} intent catalog. Returns an empty list when XTM One is not
   * configured or exposes no such agents, so the operator UI degrades to a CTA-only state.
   */
  @Transactional(readOnly = true)
  public List<ChatbotAgentOutput> availableAdditionalAgents() {
    requireFeature();
    return xtmOneClient.listAdditionalAttackAgents();
  }

  /** The tenant's default additional agents (ids) attached to every new autonomous run. */
  @Transactional(readOnly = true)
  public List<String> defaultAdditionalAgentIds() {
    requireFeature();
    return readDefaultAdditionalAgentIds();
  }

  /** Persists the tenant's default additional agents (ids). */
  @Transactional(rollbackFor = Exception.class)
  public List<String> updateDefaultAdditionalAgentIds(List<String> agentIds) {
    requireFeature();
    List<String> cleaned = new ArrayList<>();
    if (agentIds != null) {
      for (String id : agentIds) {
        if (hasText(id) && !cleaned.contains(id.trim())) {
          cleaned.add(id.trim());
        }
      }
    }
    String key = TenantSettingKeys.AUTONOMOUS_ADDITIONAL_AGENTS.key();
    Setting setting =
        settingRepository.findByKeyAndTenantIsNull(key).orElseGet(() -> new Setting(key, "[]"));
    try {
      setting.setValue(objectMapper.writeValueAsString(cleaned));
    } catch (Exception e) {
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Failed to serialize default agents", e);
    }
    settingRepository.save(setting);
    return cleaned;
  }

  private List<String> readDefaultAdditionalAgentIds() {
    Optional<Setting> setting =
        settingRepository.findByKeyAndTenantIsNull(
            TenantSettingKeys.AUTONOMOUS_ADDITIONAL_AGENTS.key());
    // No row at all: the tenant has never configured this, so the license-independent built-in
    // payload creator is the enabled-by-default specialist. An existing row (even an empty "[]") is
    // authoritative - it means the admin explicitly chose the set, up to disabling the built-in.
    if (setting.isEmpty()) {
      return resolveBuiltinDefaultAgentIds();
    }
    String raw = setting.get().getValue();
    if (!hasText(raw)) {
      return new ArrayList<>();
    }
    try {
      List<String> parsed = objectMapper.readValue(raw, new TypeReference<List<String>>() {});
      return parsed != null ? new ArrayList<>(parsed) : new ArrayList<>();
    } catch (Exception e) {
      log.warn("[Autonomous] Invalid default additional agents setting value: {}", raw, e);
      return new ArrayList<>();
    }
  }

  /**
   * The built-in payload creator's XTM One id, resolved from the additional-attack agent catalog by
   * its well-known slug. Used as the enabled-by-default specialist when the tenant has never
   * configured its default agents. Returns an empty list when XTM One is unconfigured or the
   * built-in is not exposed, so an unconfigured tenant simply runs with no default specialist.
   */
  private List<String> resolveBuiltinDefaultAgentIds() {
    try {
      return xtmOneClient.listAdditionalAttackAgents().stream()
          .filter(agent -> BUILTIN_AGENT_SLUG.equals(agent.slug()))
          .map(ChatbotAgentOutput::id)
          .filter(id -> id != null && !id.isBlank())
          .collect(Collectors.toCollection(ArrayList::new));
    } catch (Exception e) {
      log.warn("[Autonomous] Unable to resolve the built-in default agent id from XTM One", e);
      return new ArrayList<>();
    }
  }

  /** The tenant's default per-agent discovery modes (agent id -> mode name). */
  @Transactional(readOnly = true)
  public Map<String, String> defaultAdditionalAgentModes() {
    requireFeature();
    return readDefaultAdditionalAgentModes();
  }

  /** Persists the tenant's default per-agent discovery modes (canonicalized to valid modes). */
  @Transactional(rollbackFor = Exception.class)
  public Map<String, String> updateDefaultAdditionalAgentModes(Map<String, String> agentModes) {
    requireFeature();
    Map<String, String> cleaned = normalizeAgentModes(agentModes, null);
    String key = TenantSettingKeys.AUTONOMOUS_ADDITIONAL_AGENT_MODES.key();
    Setting setting =
        settingRepository.findByKeyAndTenantIsNull(key).orElseGet(() -> new Setting(key, "{}"));
    try {
      setting.setValue(objectMapper.writeValueAsString(cleaned));
    } catch (Exception e) {
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Failed to serialize default agent modes", e);
    }
    settingRepository.save(setting);
    return cleaned;
  }

  private Map<String, String> readDefaultAdditionalAgentModes() {
    Optional<Setting> setting =
        settingRepository.findByKeyAndTenantIsNull(
            TenantSettingKeys.AUTONOMOUS_ADDITIONAL_AGENT_MODES.key());
    if (setting.isEmpty() || !hasText(setting.get().getValue())) {
      return new HashMap<>();
    }
    try {
      Map<String, String> parsed =
          objectMapper.readValue(
              setting.get().getValue(), new TypeReference<Map<String, String>>() {});
      return parsed != null ? normalizeAgentModes(parsed, null) : new HashMap<>();
    } catch (Exception e) {
      log.warn(
          "[Autonomous] Invalid default additional agent modes setting value: {}",
          setting.get().getValue(),
          e);
      return new HashMap<>();
    }
  }

  /**
   * Canonicalizes a raw per-agent mode map: trims keys, validates every value to a real {@link
   * AutonomousDiscoveryMode} name, and (when {@code enabledIds} is non-null) guarantees each
   * enabled (specialist) agent has an explicit mode, defaulting to {@link
   * AutonomousDiscoveryMode#SPECIALIST_DEFAULT} (EXPANSIVE).
   */
  private Map<String, String> normalizeAgentModes(
      Map<String, String> raw, List<String> enabledIds) {
    Map<String, String> out = new HashMap<>();
    if (raw != null) {
      raw.forEach(
          (id, mode) -> {
            if (hasText(id)) {
              out.put(id.trim(), AutonomousDiscoveryMode.fromValue(mode).name());
            }
          });
    }
    if (enabledIds != null) {
      for (String id : enabledIds) {
        if (hasText(id)) {
          // enabledIds are specialist / additional agents (never the orchestrator sentinel), so an
          // agent the operator enabled without picking a mode gets the specialist default
          // (EXPANSIVE)
          // - they are recon-oriented and expected to expand the perimeter by default. The
          // orchestrator's own mode is keyed separately under ORCHESTRATOR_AGENT_ID and falls back
          // to
          // SCOPED via resolveDiscoveryMode.
          out.putIfAbsent(id.trim(), AutonomousDiscoveryMode.SPECIALIST_DEFAULT.name());
        }
      }
    }
    return out;
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
      AutonomousRun settled =
          reconciliationWriter.settleRunStatus(
              run.getId(), run.getTenant().getId(), target, detail);
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

  /**
   * Finishes an autonomous run's simulation directly (status FINISHED + end date), mirroring the
   * way {@code InjectsExecutionJob} closes a simulation. A plan simulation is created RUNNING as
   * the orchestrator's authoring / visualization substrate, but its RUN workflow is intentionally
   * never started (see {@link #create}), so the auto-closing scheduler never runs on it and it
   * would stay "On-going" forever after the plan is ready. We cannot route this through {@link
   * #transitionSimulationQuietly}: RUNNING -> FINISHED is not an allowed {@code
   * Exercise#nextPossibleStatus} manual transition (FINISHED is scheduler-only), so
   * changeExerciseStatus would just throw and be swallowed. A plan simulation has no RUN workflow
   * and no executed injects, so the normal close side effects do not apply - a plain terminal set
   * is both correct and sufficient. Best-effort: never fails the orchestrator callback.
   */
  private void finishSimulationTerminallyQuietly(AutonomousRun run) {
    String simulationId = run.getSimulationId();
    if (!hasText(simulationId)) {
      return;
    }
    try {
      Exercise simulation = exerciseService.exercise(simulationId);
      ExerciseStatus current = simulation.getStatus();
      if (current == ExerciseStatus.FINISHED || current == ExerciseStatus.CANCELED) {
        return;
      }
      simulation.setStatus(ExerciseStatus.FINISHED);
      simulation.setEnd(now());
      simulation.setUpdatedAt(now());
      exerciseRepository.save(simulation);
    } catch (Exception e) {
      log.warn(
          "[Autonomous] Could not finish simulation {} on run {}", simulationId, run.getId(), e);
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
