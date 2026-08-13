package io.openaev.service.autonomous;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.openaev.api.autonomous.dto.AutonomousRunCreateInput;
import io.openaev.api.autonomous.dto.ConvertToManualMode;
import io.openaev.api.chaining.dto.WorkflowScopeRuleInput;
import io.openaev.config.OpenAEVConfig;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.ExerciseStatus;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.ScopeRuleSelectedMode;
import io.openaev.database.model.ScopeRuleSource;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.autonomous.AutonomousDirective;
import io.openaev.database.model.autonomous.AutonomousEventType;
import io.openaev.database.model.autonomous.AutonomousRun;
import io.openaev.database.model.autonomous.AutonomousRunStatus;
import io.openaev.database.model.autonomous.AutonomousScopeTarget;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Focused unit tests for the dry-run ("plan mode") branches, the OpenAEV-owned timeout policy
 * (deadline stamping, winddown nudges, hard stop) and the restart hard-reset contract of {@link
 * AutonomousRunService}. Kept as a plain Mockito unit test (no Spring context / Docker) so it
 * verifies the suppression + guard logic in isolation: a plan run never dispatches, only a settled
 * plan can be promoted, each winddown phase fires at most once, the hard stop is claimed exactly
 * once, and restart is valid from any status (teardown + fresh simulation + reset to CREATED).
 */
@ExtendWith(MockitoExtension.class)
class AutonomousRunServiceTest {

  @Mock private AutonomousRunRepository runRepository;
  @Mock private AutonomousDirectiveRepository directiveRepository;
  @Mock private AutonomousEventService eventService;
  @Mock private WorkflowService workflowService;
  @Mock private ExerciseService exerciseService;
  @Mock private PreviewFeatureService previewFeatureService;
  @Mock private ScenarioService scenarioService;
  @Mock private ScenarioToExerciseService scenarioToExerciseService;
  @Mock private XtmOneClient xtmOneClient;
  @Mock private OpenAEVConfig openAEVConfig;
  // Lenient by default (void asserts are no-ops): these unit tests exercise lifecycle logic, not
  // authorization. The deny paths are covered by AutonomousRunAccessControlTest.
  @Mock private AutonomousRunAccessControl accessControl;

  @InjectMocks private AutonomousRunService service;

  @Test
  @DisplayName("evaluateAttackPath is a no-op in plan mode (never touches the run workflow)")
  void evaluateAttackPathIsNoOpInPlanMode() {
    when(previewFeatureService.isAutonomousAttackPathEnabled()).thenReturn(true);
    AutonomousRun run = new AutonomousRun();
    run.setPlanMode(true);
    run.setStatus(AutonomousRunStatus.PLANNING);
    run.setSimulationId("sim-1");
    when(runRepository.findById("run-1")).thenReturn(Optional.of(run));

    service.evaluateAttackPath("run-1");

    // A dry-run never evaluates its workflow, so nothing can be readied or dispatched.
    verify(workflowService, never()).findWorkflowRunBySimulationId(anyString());
  }

  @Test
  @DisplayName("promoteToRealRun refuses a run that is not a dry-run")
  void promoteRejectsNonPlanRun() {
    when(previewFeatureService.isAutonomousAttackPathEnabled()).thenReturn(true);
    AutonomousRun run = new AutonomousRun();
    run.setPlanMode(false);
    run.setStatus(AutonomousRunStatus.RUNNING);
    when(runRepository.findById("run-1")).thenReturn(Optional.of(run));

    assertThatThrownBy(() -> service.promoteToRealRun("run-1"))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  @DisplayName("promoteToRealRun refuses a plan that is still being designed (PLANNING)")
  void promoteRejectsUnsettledPlan() {
    when(previewFeatureService.isAutonomousAttackPathEnabled()).thenReturn(true);
    AutonomousRun run = new AutonomousRun();
    run.setPlanMode(true);
    run.setStatus(AutonomousRunStatus.PLANNING);
    when(runRepository.findById("run-1")).thenReturn(Optional.of(run));

    assertThatThrownBy(() -> service.promoteToRealRun("run-1"))
        .isInstanceOf(ResponseStatusException.class);
  }

  // region OpenAEV-owned timeout: deadline stamping, winddown nudges, hard stop

  private static AutonomousRun liveRun(Instant deadlineAt) {
    AutonomousRun run = new AutonomousRun();
    run.setId("run-1");
    run.setSimulationId("sim-1");
    run.setStatus(AutonomousRunStatus.RUNNING);
    run.setPlanMode(false);
    run.setDeadlineAt(deadlineAt);
    Tenant tenant = new Tenant();
    tenant.setId("tenant-1");
    run.setTenant(tenant);
    return run;
  }

  @Test
  @DisplayName("stampDeadline defaults a live run with no timeout to the standard 24h budget")
  void stampDeadlineDefaultsLiveRunTimeout() {
    // A promoted plan run had its timeout nulled at create (plan mode is untimed); going live it
    // must still end up enforceable, never untimed forever.
    AutonomousRun run = new AutonomousRun();
    run.setPlanMode(false);
    run.setTimeoutSeconds(null);
    run.setWinddownPhase("WINDDOWN_5M");

    service.stampDeadline(run);

    assertThat(run.getTimeoutSeconds()).isEqualTo(AutonomousRunService.DEFAULT_TIMEOUT_SECONDS);
    assertThat(run.getStartedAt()).isNotNull();
    assertThat(run.getDeadlineAt())
        .isEqualTo(run.getStartedAt().plusSeconds(AutonomousRunService.DEFAULT_TIMEOUT_SECONDS));
    assertThat(run.getWinddownPhase()).isNull();
  }

  @Test
  @DisplayName("stampDeadline keeps a plan run untimed and clears any stale deadline")
  void stampDeadlineClearsDeadlineForPlanMode() {
    AutonomousRun run = new AutonomousRun();
    run.setPlanMode(true);
    run.setTimeoutSeconds(3600L);
    run.setDeadlineAt(Instant.now().plusSeconds(3600));
    run.setWinddownPhase("WINDDOWN_1M");

    service.stampDeadline(run);

    assertThat(run.getStartedAt()).isNotNull();
    assertThat(run.getDeadlineAt()).isNull();
    assertThat(run.getWinddownPhase()).isNull();
  }

  @Test
  @DisplayName("enforceDeadline ignores a run that is no longer live")
  void enforceDeadlineIgnoresSettledRun() {
    AutonomousRun run = liveRun(Instant.now().minusSeconds(10));
    run.setStatus(AutonomousRunStatus.CANCELED);
    when(runRepository.findByIdAndTenantId("run-1", "tenant-1")).thenReturn(Optional.of(run));

    service.enforceDeadline("run-1", "tenant-1");

    verifyNoInteractions(directiveRepository, eventService, exerciseService);
  }

  @Test
  @DisplayName("enforceDeadline queues the 5-minute winddown nudge at most once")
  void enforceDeadlineQueuesWinddownOnce() {
    AutonomousRun run = liveRun(Instant.now().plusSeconds(200));
    when(runRepository.findByIdAndTenantId("run-1", "tenant-1")).thenReturn(Optional.of(run));

    service.enforceDeadline("run-1", "tenant-1");
    // Second sweep in the same window (the job fires every 30s): the persisted phase must
    // suppress a duplicate nudge.
    service.enforceDeadline("run-1", "tenant-1");

    assertThat(run.getWinddownPhase()).isEqualTo("WINDDOWN_5M");
    verify(directiveRepository, times(1)).save(any(AutonomousDirective.class));
    verify(eventService, times(1))
        .append(
            eq("run-1"),
            eq("sim-1"),
            eq(AutonomousEventType.DIRECTIVE),
            anyString(),
            anyString(),
            eq(null));
    verifyNoInteractions(exerciseService);
  }

  @Test
  @DisplayName("enforceDeadline escalates from the 5-minute to the 1-minute winddown phase")
  void enforceDeadlineEscalatesToFinalWinddown() {
    AutonomousRun run = liveRun(Instant.now().plusSeconds(30));
    run.setWinddownPhase("WINDDOWN_5M");
    when(runRepository.findByIdAndTenantId("run-1", "tenant-1")).thenReturn(Optional.of(run));

    service.enforceDeadline("run-1", "tenant-1");

    assertThat(run.getWinddownPhase()).isEqualTo("WINDDOWN_1M");
    verify(directiveRepository, times(1)).save(any(AutonomousDirective.class));
  }

  @Test
  @DisplayName("enforceDeadline never downgrades a reached winddown phase")
  void enforceDeadlineNeverDowngradesPhase() {
    // Already at the final phase; a sweep landing back in the 5-minute window (clock skew,
    // redelivery) must not re-nudge.
    AutonomousRun run = liveRun(Instant.now().plusSeconds(200));
    run.setWinddownPhase("WINDDOWN_1M");
    when(runRepository.findByIdAndTenantId("run-1", "tenant-1")).thenReturn(Optional.of(run));

    service.enforceDeadline("run-1", "tenant-1");

    verifyNoInteractions(directiveRepository, eventService, exerciseService);
  }

  @Test
  @DisplayName("enforceDeadline hard-stops a run past its deadline, exactly like an operator Stop")
  void enforceDeadlineHardStopsPastDeadline() throws Exception {
    AutonomousRun run = liveRun(Instant.now().minusSeconds(5));
    when(runRepository.findByIdAndTenantId("run-1", "tenant-1")).thenReturn(Optional.of(run));
    when(runRepository.settleTerminalStatusIfLive(
            eq("run-1"), eq("tenant-1"), eq(AutonomousRunStatus.CANCELED), any(Instant.class)))
        .thenReturn(1);

    service.enforceDeadline("run-1", "tenant-1");

    verify(exerciseService).changeExerciseStatus(ExerciseStatus.CANCELED, "sim-1");
    verify(eventService)
        .appendTerminalStatusOnce(eq("run-1"), eq("sim-1"), eq("Run timed out"), anyString());
    verify(directiveRepository, never()).save(any());
  }

  @Test
  @DisplayName("the hard stop is claimed once: a lost race with cancel/reconcile stays silent")
  void enforceDeadlineHardStopClaimedOnce() {
    AutonomousRun run = liveRun(Instant.now().minusSeconds(5));
    when(runRepository.findByIdAndTenantId("run-1", "tenant-1")).thenReturn(Optional.of(run));
    // An operator Stop / restart or a read-path reconcile moved the run first: the conditional
    // UPDATE (restricted to the live statuses the sweep acts on) matches no row, so this watchdog
    // must not narrate a second terminal event nor cancel a freshly-restarted run.
    when(runRepository.settleTerminalStatusIfLive(
            eq("run-1"), eq("tenant-1"), eq(AutonomousRunStatus.CANCELED), any(Instant.class)))
        .thenReturn(0);

    service.enforceDeadline("run-1", "tenant-1");

    verifyNoInteractions(eventService, exerciseService, directiveRepository);
  }

  // endregion

  // region restart: an operator-triggered hard reset, valid from ANY status

  private AutonomousRun restartableRun(AutonomousRunStatus status) {
    AutonomousRun run = new AutonomousRun();
    run.setId("run-1");
    run.setScenarioId("scenario-1");
    run.setSimulationId("sim-old");
    run.setStatus(status);
    run.setPlanMode(false);
    return run;
  }

  /** Stubs the collaborators the restart teardown + re-provisioning path touches. */
  private void stubRestartCollaborators() {
    when(previewFeatureService.isAutonomousAttackPathEnabled()).thenReturn(true);
    Scenario scenario = new Scenario();
    when(scenarioService.scenario("scenario-1")).thenReturn(scenario);
    Exercise freshSimulation = new Exercise();
    freshSimulation.setId("sim-new");
    when(scenarioToExerciseService.toExercise(eq(scenario), any(Instant.class), eq(true)))
        .thenReturn(freshSimulation);
    when(runRepository.save(any(AutonomousRun.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  @DisplayName("restart from RUNNING is a hard reset: stop, teardown, fresh simulation, CREATED")
  void restartFromRunningIsAHardReset() throws Exception {
    AutonomousRun run = restartableRun(AutonomousRunStatus.RUNNING);
    // Leftovers from the live window that must not survive the reset.
    run.setLastError("previous failure");
    run.setXtmSessionId("xtm-session-1");
    run.setStartedAt(Instant.now().minusSeconds(600));
    run.setDeadlineAt(Instant.now().minusSeconds(5));
    run.setWinddownPhase("WINDDOWN_1M");
    when(runRepository.findById("run-1")).thenReturn(Optional.of(run));
    stubRestartCollaborators();

    AutonomousRun restarted = service.restart("run-1");

    // The previous orchestration is stopped (and its coordination state purged) and the previous
    // simulation torn down before the fresh one is provisioned from the SAME scenario.
    verify(xtmOneClient).cancelAutonomousRun(eq("run-1"), anyString(), eq(true));
    verify(exerciseService).deleteById("sim-old");
    verify(workflowService).startWorkflowByScenarioIdAndSimulation(eq("scenario-1"), any());
    // Keep-alive is applied to the freshly provisioned SIMULATION, never the reusable scenario
    // template, so the scenario keeps its own "Simulation time out" config.
    verify(workflowService).markSimulationWorkflowKeepAlive("sim-new");
    // Decision timeline + steering reset so the cockpit starts clean.
    verify(directiveRepository).deleteByRunId("run-1");
    verify(eventService).deleteByRun("run-1");
    assertThat(restarted.getStatus()).isEqualTo(AutonomousRunStatus.CREATED);
    assertThat(restarted.getSimulationId()).isEqualTo("sim-new");
    assertThat(restarted.getLastError()).isNull();
    assertThat(restarted.getXtmSessionId()).isNull();
    // The stale time budget is void: the follow-up start() stamps a fresh deadline, and the
    // watchdog must never see a CREATED run carrying the previous window's (past) deadline.
    assertThat(restarted.getStartedAt()).isNull();
    assertThat(restarted.getDeadlineAt()).isNull();
    assertThat(restarted.getWinddownPhase()).isNull();
  }

  @Test
  @DisplayName("restart from WAITING_INPUT no longer 409s: the parked run is reset like any other")
  void restartFromWaitingInputIsAllowed() throws Exception {
    AutonomousRun run = restartableRun(AutonomousRunStatus.WAITING_INPUT);
    when(runRepository.findById("run-1")).thenReturn(Optional.of(run));
    stubRestartCollaborators();

    AutonomousRun restarted = service.restart("run-1");

    verify(xtmOneClient).cancelAutonomousRun(eq("run-1"), anyString(), eq(true));
    verify(exerciseService).deleteById("sim-old");
    verify(workflowService).startWorkflowByScenarioIdAndSimulation(eq("scenario-1"), any());
    assertThat(restarted.getStatus()).isEqualTo(AutonomousRunStatus.CREATED);
    assertThat(restarted.getSimulationId()).isEqualTo("sim-new");
  }

  @Test
  @DisplayName("restart from a settled status keeps working exactly as before")
  void restartFromSettledStatusStillWorks() throws Exception {
    AutonomousRun run = restartableRun(AutonomousRunStatus.COMPLETED);
    when(runRepository.findById("run-1")).thenReturn(Optional.of(run));
    stubRestartCollaborators();

    AutonomousRun restarted = service.restart("run-1");

    verify(exerciseService).deleteById("sim-old");
    verify(workflowService).startWorkflowByScenarioIdAndSimulation(eq("scenario-1"), any());
    assertThat(restarted.getStatus()).isEqualTo(AutonomousRunStatus.CREATED);
    assertThat(restarted.getSimulationId()).isEqualTo("sim-new");
  }

  @Test
  @DisplayName("plan-mode restart re-provisions the TEMPLATE workflow only (nothing executes)")
  void restartOfPlanModeReprovisionsTemplateOnly() throws Exception {
    AutonomousRun run = restartableRun(AutonomousRunStatus.PLANNED);
    run.setPlanMode(true);
    when(runRepository.findById("run-1")).thenReturn(Optional.of(run));
    stubRestartCollaborators();

    AutonomousRun restarted = service.restart("run-1");

    verify(workflowService).provisionSimulationTemplateWorkflow(eq("scenario-1"), any());
    verify(workflowService, never()).startWorkflowByScenarioIdAndSimulation(anyString(), any());
    assertThat(restarted.getStatus()).isEqualTo(AutonomousRunStatus.CREATED);
  }

  // endregion

  // region convertToManual: turn an AI-driven scenario into a manual chained scenario

  private AutonomousRun convertibleRun(AutonomousRunStatus status) {
    AutonomousRun run = new AutonomousRun();
    run.setId("run-1");
    run.setScenarioId("scenario-1");
    run.setSimulationId("sim-1");
    run.setStatus(status);
    return run;
  }

  @Test
  @DisplayName("DUPLICATE copies the scenario as manual and leaves the AI run fully intact")
  void convertToManualDuplicateLeavesRunUntouched() throws Exception {
    when(previewFeatureService.isAutonomousAttackPathEnabled()).thenReturn(true);
    AutonomousRun run = convertibleRun(AutonomousRunStatus.RUNNING);
    when(runRepository.findById("run-1")).thenReturn(Optional.of(run));
    Scenario duplicate = new Scenario();
    duplicate.setId("scenario-copy");
    when(scenarioService.getDuplicateScenario("scenario-1")).thenReturn(duplicate);

    Scenario result = service.convertToManual("run-1", ConvertToManualMode.DUPLICATE);

    assertThat(result).isSameAs(duplicate);
    // The copy's chaining workflow is cloned with keep-alive forced off.
    verify(workflowService).copyScenarioChainingWorkflowAsManual("scenario-1", duplicate);
    // The original run, its scenario, simulation and timeline are all untouched.
    verifyNoInteractions(xtmOneClient, exerciseService, directiveRepository, eventService);
    verify(runRepository, never()).delete(any());
    verify(workflowService, never()).clearScenarioWorkflowKeepAlive(anyString());
  }

  @Test
  @DisplayName("DUPLICATE surfaces a copy failure as a 400 without leaking the internal cause")
  void convertToManualDuplicateWrapsChainingFailure() throws Exception {
    when(previewFeatureService.isAutonomousAttackPathEnabled()).thenReturn(true);
    AutonomousRun run = convertibleRun(AutonomousRunStatus.PLANNED);
    when(runRepository.findById("run-1")).thenReturn(Optional.of(run));
    Scenario duplicate = new Scenario();
    duplicate.setId("scenario-copy");
    when(scenarioService.getDuplicateScenario("scenario-1")).thenReturn(duplicate);
    when(workflowService.copyScenarioChainingWorkflowAsManual("scenario-1", duplicate))
        .thenThrow(new ChainingException("internal detail that must not reach the client"));

    assertThatThrownBy(() -> service.convertToManual("run-1", ConvertToManualMode.DUPLICATE))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            ex -> {
              assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
              assertThat(ex.getReason()).doesNotContain("internal detail");
            });
    // A failed duplicate never touches the original run.
    verify(runRepository, never()).delete(any());
  }

  @Test
  @DisplayName(
      "IN_PLACE on a live run halts the orchestrator, cancels the simulation, clears the AI run")
  void convertToManualInPlaceOnLiveRun() throws Exception {
    when(previewFeatureService.isAutonomousAttackPathEnabled()).thenReturn(true);
    AutonomousRun run = convertibleRun(AutonomousRunStatus.RUNNING);
    when(runRepository.findById("run-1")).thenReturn(Optional.of(run));
    Scenario scenario = new Scenario();
    scenario.setId("scenario-1");
    scenario.setAutonomous(true);
    when(scenarioService.scenario("scenario-1")).thenReturn(scenario);

    Scenario result = service.convertToManual("run-1", ConvertToManualMode.IN_PLACE);

    assertThat(result).isSameAs(scenario);
    assertThat(scenario.isAutonomous()).isFalse();
    // Orchestration halted + purged, simulation settled, keep-alive cleared, run + timeline
    // dropped.
    verify(xtmOneClient).cancelAutonomousRun(eq("run-1"), anyString(), eq(true));
    verify(exerciseService).changeExerciseStatus(ExerciseStatus.CANCELED, "sim-1");
    verify(scenarioService).updateScenario(scenario);
    verify(workflowService).clearScenarioWorkflowKeepAlive("scenario-1");
    verify(directiveRepository).deleteByRunId("run-1");
    verify(eventService).deleteByRun("run-1");
    verify(runRepository).delete(run);
    // No new scenario is ever created in place.
    verify(scenarioService, never()).getDuplicateScenario(anyString());
  }

  @Test
  @DisplayName("IN_PLACE on a settled run leaves the simulation state untouched")
  void convertToManualInPlaceOnSettledRunDoesNotTransitionSimulation() throws Exception {
    when(previewFeatureService.isAutonomousAttackPathEnabled()).thenReturn(true);
    AutonomousRun run = convertibleRun(AutonomousRunStatus.COMPLETED);
    when(runRepository.findById("run-1")).thenReturn(Optional.of(run));
    Scenario scenario = new Scenario();
    scenario.setId("scenario-1");
    scenario.setAutonomous(true);
    when(scenarioService.scenario("scenario-1")).thenReturn(scenario);

    service.convertToManual("run-1", ConvertToManualMode.IN_PLACE);

    // A settled run already left the simulation scheduled/terminal - never re-transition it.
    verifyNoInteractions(exerciseService);
    verify(runRepository).delete(run);
    assertThat(scenario.isAutonomous()).isFalse();
  }

  @Test
  @DisplayName("convertToManual 409s when the run has no scenario to convert")
  void convertToManualRejectsRunWithoutScenario() {
    when(previewFeatureService.isAutonomousAttackPathEnabled()).thenReturn(true);
    AutonomousRun run = new AutonomousRun();
    run.setId("run-1");
    run.setStatus(AutonomousRunStatus.RUNNING);
    when(runRepository.findById("run-1")).thenReturn(Optional.of(run));

    assertThatThrownBy(() -> service.convertToManual("run-1", ConvertToManualMode.IN_PLACE))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    // Nothing is halted or mutated when the run cannot be converted.
    verifyNoInteractions(xtmOneClient, exerciseService);
    verify(runRepository, never()).delete(any());
  }

  // endregion

  // region scenario-side entry points: launch-from-scenario (autonomous mode) + plan-with-AI

  @Test
  @DisplayName("launchFromScenario refuses a scenario that has no chaining (attack path) workflow")
  void launchFromScenarioRejectsNonChainedScenario() {
    when(previewFeatureService.isAutonomousAttackPathEnabled()).thenReturn(true);
    // A time-based (non-chained) scenario has no authored attack path to seed, so it cannot be
    // launched in autonomous mode - the entry point must 400 before creating any run.
    when(workflowService.isScenarioChaining("scenario-1")).thenReturn(false);

    assertThatThrownBy(() -> service.launchFromScenario("scenario-1", null))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    verify(runRepository, never()).save(any());
    verifyNoInteractions(xtmOneClient);
  }

  @Test
  @DisplayName("planScenario refuses a scenario that is not chained (nothing to author onto)")
  void planScenarioRejectsNonChainedScenario() {
    when(previewFeatureService.isAutonomousAttackPathEnabled()).thenReturn(true);
    when(workflowService.isScenarioChaining("scenario-1")).thenReturn(false);

    assertThatThrownBy(() -> service.planScenario("scenario-1", null))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    verify(runRepository, never()).save(any());
    verifyNoInteractions(xtmOneClient);
  }

  @Test
  @DisplayName(
      "planScenario authors onto the scenario template: plan-mode run with NO simulation, and the"
          + " orchestrator is engaged in author-scenario mode (scenario id, null simulation)")
  void planScenarioAuthorsOntoScenarioWithoutSimulation() throws Exception {
    when(previewFeatureService.isAutonomousAttackPathEnabled()).thenReturn(true);
    when(workflowService.isScenarioChaining("scenario-1")).thenReturn(true);
    Scenario scenario = new Scenario();
    scenario.setId("scenario-1");
    scenario.setName("Ransomware kill chain");
    when(scenarioService.scenario("scenario-1")).thenReturn(scenario);

    // save() assigns the generated id and the follow-up start() reloads it by that id.
    final AutonomousRun[] savedHolder = new AutonomousRun[1];
    when(runRepository.save(any(AutonomousRun.class)))
        .thenAnswer(
            invocation -> {
              AutonomousRun r = invocation.getArgument(0);
              if (r.getId() == null) {
                r.setId("plan-run-1");
              }
              savedHolder[0] = r;
              return r;
            });
    when(runRepository.findById("plan-run-1"))
        .thenAnswer(invocation -> Optional.ofNullable(savedHolder[0]));

    AutonomousRunCreateInput input = new AutonomousRunCreateInput();
    input.setObjective("Prove a path to the domain controller");
    // Explicit (empty) agent selection so the tenant-default settings lookup is never hit.
    input.setAgentIds(List.of());
    input.setAgentModes(Map.of());

    AutonomousRun run = service.planScenario("scenario-1", input);

    // Author-scenario mode is a dry-run bound to the SCENARIO, with no simulation ever provisioned.
    assertThat(run.isPlanMode()).isTrue();
    assertThat(run.getScenarioId()).isEqualTo("scenario-1");
    assertThat(run.getSimulationId()).isNull();
    assertThat(run.getStatus()).isEqualTo(AutonomousRunStatus.PLANNING);
    // Build always starts from a blank logic map: the full wipe (steps AND event/trigger
    // conditions) runs before the orchestrator is engaged.
    verify(workflowService).deleteAllScenarioSteps("scenario-1");
    // Author-scenario mode provisions NO simulation and runs nothing, so it must NEVER touch a
    // keep-alive / timeout flag: the built scenario keeps its own "Simulation time out" config
    // (default 1h) so it can later be launched in normal mode and run-and-end normally.
    verify(workflowService, never()).markSimulationWorkflowKeepAlive(anyString());
    // Scope is written onto the scenario workflow only (null simulation id), never a simulation.
    verify(workflowService).writeScopeRules(eq("scenario-1"), isNull(), anyList());
    // The orchestrator is engaged in AUTHOR-SCENARIO mode: author_scenario=true, the scenario id is
    // passed, and there is NO simulation to target.
    verify(xtmOneClient)
        .startAutonomousRun(
            any(),
            eq("Prove a path to the domain controller"),
            eq("plan-run-1"),
            isNull(),
            eq("scenario-1"),
            eq(true),
            any(),
            any(),
            anyList(),
            any(),
            eq(true),
            any(),
            any(),
            anyList(),
            anyMap());
  }

  @Test
  @DisplayName("planScenario refuses to rebuild while the scenario's previous run is still active")
  void planScenarioRefusesWhileActiveRun() {
    when(previewFeatureService.isAutonomousAttackPathEnabled()).thenReturn(true);
    when(workflowService.isScenarioChaining("scenario-1")).thenReturn(true);
    // A prior plan run is still being designed (active): rebuilding would orphan it, so the entry
    // point must 409 before wiping anything or engaging the orchestrator.
    AutonomousRun prior = new AutonomousRun();
    prior.setId("prior-run");
    prior.setPlanMode(true);
    prior.setStatus(AutonomousRunStatus.PLANNING);
    when(runRepository.findByScenarioId("scenario-1")).thenReturn(Optional.of(prior));

    assertThatThrownBy(() -> service.planScenario("scenario-1", null))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    verify(workflowService, never()).deleteAllScenarioSteps(anyString());
    verify(runRepository, never()).save(any());
    verifyNoInteractions(xtmOneClient);
  }

  @Test
  @DisplayName("planScenario supersedes a settled prior run before rebuilding the plan")
  void planScenarioSupersedesSettledPriorRun() {
    when(previewFeatureService.isAutonomousAttackPathEnabled()).thenReturn(true);
    when(workflowService.isScenarioChaining("scenario-1")).thenReturn(true);
    Scenario scenario = new Scenario();
    scenario.setId("scenario-1");
    scenario.setName("Ransomware kill chain");
    when(scenarioService.scenario("scenario-1")).thenReturn(scenario);

    // A settled dry-run (PLANNED, no simulation) already exists: it must be torn down so the fresh
    // plan run can bind, but its (absent) simulation is never touched.
    AutonomousRun prior = new AutonomousRun();
    prior.setId("prior-run");
    prior.setPlanMode(true);
    prior.setStatus(AutonomousRunStatus.PLANNED);
    when(runRepository.findByScenarioId("scenario-1")).thenReturn(Optional.of(prior));

    final AutonomousRun[] savedHolder = new AutonomousRun[1];
    when(runRepository.save(any(AutonomousRun.class)))
        .thenAnswer(
            invocation -> {
              AutonomousRun r = invocation.getArgument(0);
              if (r.getId() == null) {
                r.setId("plan-run-2");
              }
              savedHolder[0] = r;
              return r;
            });
    when(runRepository.findById("plan-run-2"))
        .thenAnswer(invocation -> Optional.ofNullable(savedHolder[0]));

    AutonomousRunCreateInput input = new AutonomousRunCreateInput();
    input.setObjective("Prove a path to the domain controller");
    input.setAgentIds(List.of());
    input.setAgentModes(Map.of());

    AutonomousRun run = service.planScenario("scenario-1", input);

    // The prior settled run row is removed and its coordination state purged (no simulation delete
    // for a plan-mode dry-run), then the fresh plan run is created and the logic map wiped.
    verify(runRepository).delete(prior);
    verify(xtmOneClient).cancelAutonomousRun(eq("prior-run"), anyString(), eq(true));
    verify(exerciseService, never()).deleteById(anyString());
    verify(workflowService).deleteAllScenarioSteps("scenario-1");
    assertThat(run.getStatus()).isEqualTo(AutonomousRunStatus.PLANNING);
  }

  @Test
  @DisplayName(
      "planScenario refine keeps the authored logic (no wipe) and REUSES the prior AI-built plan"
          + " run so its decision timeline (history) is preserved and reopened")
  void given_settledAiPlanRun_should_reusePriorRunAndKeepLogicOnRefine() throws Exception {
    when(previewFeatureService.isAutonomousAttackPathEnabled()).thenReturn(true);
    when(workflowService.isScenarioChaining("scenario-1")).thenReturn(true);
    Scenario scenario = new Scenario();
    scenario.setId("scenario-1");
    scenario.setName("Ransomware kill chain");
    when(scenarioService.scenario("scenario-1")).thenReturn(scenario);

    // The scenario was previously built by the AI: a settled PLAN-mode run (PLANNED, no simulation)
    // owns it. Refine must reuse THIS row so its timeline survives, never a fresh one.
    AutonomousRun prior = new AutonomousRun();
    prior.setId("prior-run");
    prior.setPlanMode(true);
    prior.setStatus(AutonomousRunStatus.PLANNED);
    when(runRepository.findByScenarioId("scenario-1")).thenReturn(Optional.of(prior));

    when(runRepository.save(any(AutonomousRun.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(runRepository.findById("prior-run")).thenReturn(Optional.of(prior));

    AutonomousRunCreateInput input = new AutonomousRunCreateInput();
    input.setRefine(true);
    input.setObjective("Add a phishing entry vector");
    input.setAgentIds(List.of());
    input.setAgentModes(Map.of());

    AutonomousRun run = service.planScenario("scenario-1", input);

    // Refine NEVER wipes the logic map and NEVER supersedes/deletes the prior run - the whole point
    // is to keep the existing steps AND the prior run's decision timeline (full history).
    verify(workflowService, never()).deleteAllScenarioSteps(anyString());
    verify(runRepository, never()).delete(any());
    verify(xtmOneClient, never()).cancelAutonomousRun(anyString(), anyString(), anyBoolean());
    // The prior row is reused (same id) and re-engaged: settled -> CREATED -> PLANNING.
    assertThat(run.getId()).isEqualTo("prior-run");
    assertThat(run.isPlanMode()).isTrue();
    assertThat(run.getStatus()).isEqualTo(AutonomousRunStatus.PLANNING);
    // The operator's instruction becomes a refinement objective that tells the orchestrator to keep
    // and extend the current logic, never to rebuild it.
    assertThat(run.getObjective())
        .contains("Do NOT rebuild it from scratch")
        .contains("Add a phishing entry vector");
    // A "Refinement requested" event is appended (the run's existing timeline is preserved, not
    // reset), and the orchestrator is engaged in author-scenario mode against the reused run.
    verify(eventService)
        .append(
            eq("prior-run"),
            isNull(),
            eq(AutonomousEventType.STATUS),
            eq("Refinement requested"),
            anyString(),
            isNull());
    verify(xtmOneClient)
        .startAutonomousRun(
            any(),
            anyString(),
            eq("prior-run"),
            isNull(),
            eq("scenario-1"),
            eq(true),
            any(),
            any(),
            anyList(),
            any(),
            eq(true),
            any(),
            any(),
            anyList(),
            anyMap());
  }

  @Test
  @DisplayName(
      "planScenario refine with no prior run keeps the (manually authored) logic and starts a fresh"
          + " plan run without wiping the steps")
  void given_manualScenarioWithoutPriorRun_should_keepLogicOnRefine() {
    when(previewFeatureService.isAutonomousAttackPathEnabled()).thenReturn(true);
    when(workflowService.isScenarioChaining("scenario-1")).thenReturn(true);
    Scenario scenario = new Scenario();
    scenario.setId("scenario-1");
    scenario.setName("Ransomware kill chain");
    when(scenarioService.scenario("scenario-1")).thenReturn(scenario);
    // A manually authored chained scenario: it has steps but no autonomous run.
    when(runRepository.findByScenarioId("scenario-1")).thenReturn(Optional.empty());

    final AutonomousRun[] savedHolder = new AutonomousRun[1];
    when(runRepository.save(any(AutonomousRun.class)))
        .thenAnswer(
            invocation -> {
              AutonomousRun r = invocation.getArgument(0);
              if (r.getId() == null) {
                r.setId("plan-run-refine");
              }
              savedHolder[0] = r;
              return r;
            });
    when(runRepository.findById("plan-run-refine"))
        .thenAnswer(invocation -> Optional.ofNullable(savedHolder[0]));

    AutonomousRunCreateInput input = new AutonomousRunCreateInput();
    input.setRefine(true);
    input.setAgentIds(List.of());
    input.setAgentModes(Map.of());

    AutonomousRun run = service.planScenario("scenario-1", input);

    // Refine keeps the authored steps in place (no wipe), even when there is no prior run.
    verify(workflowService, never()).deleteAllScenarioSteps(anyString());
    assertThat(run.getStatus()).isEqualTo(AutonomousRunStatus.PLANNING);
    // With no operator instruction, the objective is the review-and-improve refinement default.
    assertThat(run.getObjective())
        .contains("Do NOT rebuild it from scratch")
        .contains("fill obvious gaps");
  }

  @Test
  @DisplayName("planScenario refine refuses while the scenario's previous run is still active")
  void given_activePriorRun_should_refuseRefine() {
    when(previewFeatureService.isAutonomousAttackPathEnabled()).thenReturn(true);
    when(workflowService.isScenarioChaining("scenario-1")).thenReturn(true);
    Scenario scenario = new Scenario();
    scenario.setId("scenario-1");
    scenario.setName("Ransomware kill chain");
    when(scenarioService.scenario("scenario-1")).thenReturn(scenario);
    AutonomousRun prior = new AutonomousRun();
    prior.setId("prior-run");
    prior.setPlanMode(true);
    prior.setStatus(AutonomousRunStatus.PLANNING);
    when(runRepository.findByScenarioId("scenario-1")).thenReturn(Optional.of(prior));

    AutonomousRunCreateInput input = new AutonomousRunCreateInput();
    input.setRefine(true);

    assertThatThrownBy(() -> service.planScenario("scenario-1", input))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    verify(workflowService, never()).deleteAllScenarioSteps(anyString());
    verify(runRepository, never()).save(any());
    verifyNoInteractions(xtmOneClient);
  }

  @Test
  @DisplayName(
      "planScenario refine with NO scope supplied never writes scope rules, so the scenario's"
          + " existing scope is preserved (a rebuild would seed it verbatim)")
  void given_refineWithoutSuppliedScope_should_preserveExistingScenarioScope() {
    // Arrange
    when(previewFeatureService.isAutonomousAttackPathEnabled()).thenReturn(true);
    when(workflowService.isScenarioChaining("scenario-1")).thenReturn(true);
    Scenario scenario = new Scenario();
    scenario.setId("scenario-1");
    scenario.setName("Ransomware kill chain");
    when(scenarioService.scenario("scenario-1")).thenReturn(scenario);
    when(runRepository.findByScenarioId("scenario-1")).thenReturn(Optional.empty());

    final AutonomousRun[] savedHolder = new AutonomousRun[1];
    when(runRepository.save(any(AutonomousRun.class)))
        .thenAnswer(
            invocation -> {
              AutonomousRun r = invocation.getArgument(0);
              if (r.getId() == null) {
                r.setId("plan-run-refine");
              }
              savedHolder[0] = r;
              return r;
            });
    when(runRepository.findById("plan-run-refine"))
        .thenAnswer(invocation -> Optional.ofNullable(savedHolder[0]));

    // The drawer left scope untouched: no mixed scope list, no scope rules.
    AutonomousRunCreateInput input = new AutonomousRunCreateInput();
    input.setRefine(true);
    input.setAgentIds(List.of());
    input.setAgentModes(Map.of());

    // Act
    service.planScenario("scenario-1", input);

    // Assert - refine must never silently wipe/reset the scenario's existing scope: with nothing
    // supplied, the workflow scope is not touched at all.
    verify(workflowService, never()).writeScopeRules(anyString(), any(), anyList());
  }

  @Test
  @DisplayName(
      "planScenario refine WITH an explicit scope overwrites the scenario scope with the supplied"
          + " perimeter (operator intent wins over preservation)")
  void given_refineWithExplicitScope_should_overwriteScenarioScope() {
    // Arrange
    when(previewFeatureService.isAutonomousAttackPathEnabled()).thenReturn(true);
    when(workflowService.isScenarioChaining("scenario-1")).thenReturn(true);
    Scenario scenario = new Scenario();
    scenario.setId("scenario-1");
    scenario.setName("Ransomware kill chain");
    when(scenarioService.scenario("scenario-1")).thenReturn(scenario);
    when(runRepository.findByScenarioId("scenario-1")).thenReturn(Optional.empty());

    final AutonomousRun[] savedHolder = new AutonomousRun[1];
    when(runRepository.save(any(AutonomousRun.class)))
        .thenAnswer(
            invocation -> {
              AutonomousRun r = invocation.getArgument(0);
              if (r.getId() == null) {
                r.setId("plan-run-refine");
              }
              savedHolder[0] = r;
              return r;
            });
    when(runRepository.findById("plan-run-refine"))
        .thenAnswer(invocation -> Optional.ofNullable(savedHolder[0]));

    // The operator explicitly picked a perimeter in the drawer.
    AutonomousRunCreateInput input = new AutonomousRunCreateInput();
    input.setRefine(true);
    input.setAgentIds(List.of());
    input.setAgentModes(Map.of());
    input.setScope(List.of(new AutonomousScopeTarget("ASSETS_GROUPS", "asset-group-1")));

    // Act
    service.planScenario("scenario-1", input);

    // Assert - an explicitly supplied scope IS written onto the scenario workflow (null simulation:
    // plan mode has none), as an ALLOWLIST rule carrying the picked asset group.
    ArgumentCaptor<List<WorkflowScopeRuleInput>> rulesCaptor = ArgumentCaptor.forClass(List.class);
    verify(workflowService).writeScopeRules(eq("scenario-1"), isNull(), rulesCaptor.capture());
    assertThat(rulesCaptor.getValue())
        .singleElement()
        .satisfies(
            rule -> {
              assertThat(rule.getSelectedMode()).isEqualTo(ScopeRuleSelectedMode.ALLOWLIST);
              assertThat(rule.getRuleSource()).isEqualTo(ScopeRuleSource.ASSET_GROUP);
              assertThat(rule.getRuleValue()).isEqualTo("asset-group-1");
            });
  }

  @Test
  @DisplayName(
      "supersedeSettledRunOnManualLaunch tears down a settled plan run (and its throwaway"
          + " simulation) so a normal launch reverts the scenario to its non-AI overview")
  void supersedeSettledRunOnManualLaunchTearsDownSettledPlanRun() {
    when(previewFeatureService.isAutonomousAttackPathEnabled()).thenReturn(true);
    // A settled dry-run left an AI plan outcome on the scenario. Launching a normal simulation
    // makes it stale, so the run row + its plan-mode substrate simulation are removed.
    AutonomousRun prior = new AutonomousRun();
    prior.setId("prior-run");
    prior.setPlanMode(true);
    prior.setStatus(AutonomousRunStatus.CANCELED);
    prior.setSimulationId("plan-sim");
    when(runRepository.findByScenarioId("scenario-1")).thenReturn(Optional.of(prior));

    service.supersedeSettledRunOnManualLaunch("scenario-1");

    verify(exerciseService).deleteById("plan-sim");
    verify(directiveRepository).deleteByRunId("prior-run");
    verify(eventService).deleteByRun("prior-run");
    verify(runRepository).delete(prior);
    verify(xtmOneClient).cancelAutonomousRun(eq("prior-run"), anyString(), eq(true));
  }

  @Test
  @DisplayName(
      "supersedeSettledRunOnManualLaunch unbinds a finished LIVE run but keeps its simulation as"
          + " history")
  void supersedeSettledRunOnManualLaunchKeepsFinishedLiveSimulation() {
    when(previewFeatureService.isAutonomousAttackPathEnabled()).thenReturn(true);
    // A completed LIVE run: the run row is removed so the scenario reverts to the normal overview,
    // but its real simulation stays as a plain chained simulation (relaunch never destroys
    // results).
    AutonomousRun prior = new AutonomousRun();
    prior.setId("prior-run");
    prior.setPlanMode(false);
    prior.setStatus(AutonomousRunStatus.COMPLETED);
    prior.setSimulationId("live-sim");
    when(runRepository.findByScenarioId("scenario-1")).thenReturn(Optional.of(prior));

    service.supersedeSettledRunOnManualLaunch("scenario-1");

    verify(exerciseService, never()).deleteById(anyString());
    verify(runRepository).delete(prior);
  }

  @Test
  @DisplayName(
      "deleteForScenario tears down a finished LIVE run's coordination but KEEPS its simulation as"
          + " history (no legacy scenario<->simulation cascade delete)")
  void deleteForScenarioKeepsFinishedLiveSimulation() {
    when(previewFeatureService.isAutonomousAttackPathEnabled()).thenReturn(true);
    // Deleting a scenario must never destroy a real simulation: a completed LIVE run's simulation
    // is history and is left for the scenario delete to detach (scenarios_exercises
    // SET_REFERENCE_NULL), exactly like a manual chained simulation.
    AutonomousRun run = new AutonomousRun();
    run.setId("run-1");
    run.setScenarioId("scenario-1");
    run.setPlanMode(false);
    run.setStatus(AutonomousRunStatus.COMPLETED);
    run.setSimulationId("live-sim");
    when(runRepository.findByScenarioId("scenario-1")).thenReturn(Optional.of(run));

    service.deleteForScenario("scenario-1");

    verify(exerciseService, never()).deleteById(anyString());
    verify(directiveRepository).deleteByRunId("run-1");
    verify(eventService).deleteByRun("run-1");
    verify(runRepository).delete(run);
    verify(xtmOneClient).cancelAutonomousRun(eq("run-1"), anyString(), eq(true));
  }

  @Test
  @DisplayName(
      "deleteForScenario deletes only a plan-mode substrate simulation (a throwaway with no"
          + " results) with the run")
  void deleteForScenarioDeletesPlanSubstrate() {
    when(previewFeatureService.isAutonomousAttackPathEnabled()).thenReturn(true);
    AutonomousRun run = new AutonomousRun();
    run.setId("run-1");
    run.setScenarioId("scenario-1");
    run.setPlanMode(true);
    run.setStatus(AutonomousRunStatus.PLANNED);
    run.setSimulationId("plan-sim");
    when(runRepository.findByScenarioId("scenario-1")).thenReturn(Optional.of(run));

    service.deleteForScenario("scenario-1");

    verify(exerciseService).deleteById("plan-sim");
    verify(runRepository).delete(run);
  }

  @ParameterizedTest(name = "deleteForScenario refuses (409) a still-active {0} run")
  @EnumSource(
      value = AutonomousRunStatus.class,
      names = {"CREATED", "PLANNING", "RUNNING", "PAUSED", "WAITING_INPUT"})
  @DisplayName("deleteForScenario refuses (409) while the run is still active and touches nothing")
  void deleteForScenarioRefusesActiveRun(AutonomousRunStatus activeStatus) {
    when(previewFeatureService.isAutonomousAttackPathEnabled()).thenReturn(true);
    AutonomousRun run = new AutonomousRun();
    run.setId("run-1");
    run.setScenarioId("scenario-1");
    // PLANNING is the dry-run design phase, so it only ever occurs on a plan-mode run.
    run.setPlanMode(activeStatus == AutonomousRunStatus.PLANNING);
    run.setStatus(activeStatus);
    when(runRepository.findByScenarioId("scenario-1")).thenReturn(Optional.of(run));

    assertThatThrownBy(() -> service.deleteForScenario("scenario-1"))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    verify(runRepository, never()).delete(any());
    verify(exerciseService, never()).deleteById(anyString());
  }

  @Test
  @DisplayName(
      "supersedeSettledRunOnManualLaunch never tears down a still-active run (defensive no-op, no"
          + " 409)")
  void supersedeSettledRunOnManualLaunchLeavesActiveRunUntouched() {
    when(previewFeatureService.isAutonomousAttackPathEnabled()).thenReturn(true);
    AutonomousRun prior = new AutonomousRun();
    prior.setId("prior-run");
    prior.setPlanMode(false);
    prior.setStatus(AutonomousRunStatus.RUNNING);
    when(runRepository.findByScenarioId("scenario-1")).thenReturn(Optional.of(prior));

    service.supersedeSettledRunOnManualLaunch("scenario-1");

    verify(runRepository, never()).delete(any());
    verify(exerciseService, never()).deleteById(anyString());
    verifyNoInteractions(xtmOneClient);
  }

  @Test
  @DisplayName("supersedeSettledRunOnManualLaunch is a no-op when the scenario carries no run")
  void supersedeSettledRunOnManualLaunchNoOpWhenNoRun() {
    when(previewFeatureService.isAutonomousAttackPathEnabled()).thenReturn(true);
    when(runRepository.findByScenarioId("scenario-1")).thenReturn(Optional.empty());

    service.supersedeSettledRunOnManualLaunch("scenario-1");

    verify(runRepository, never()).delete(any());
    verifyNoInteractions(xtmOneClient);
  }

  // endregion
}
