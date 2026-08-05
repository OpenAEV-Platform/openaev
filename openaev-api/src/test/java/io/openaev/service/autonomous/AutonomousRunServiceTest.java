package io.openaev.service.autonomous;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.openaev.database.model.ExerciseStatus;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.autonomous.AutonomousDirective;
import io.openaev.database.model.autonomous.AutonomousEventType;
import io.openaev.database.model.autonomous.AutonomousRun;
import io.openaev.database.model.autonomous.AutonomousRunStatus;
import io.openaev.database.repository.autonomous.AutonomousDirectiveRepository;
import io.openaev.database.repository.autonomous.AutonomousRunRepository;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.service.PreviewFeatureService;
import io.openaev.service.chaining.WorkflowService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

/**
 * Focused unit tests for the dry-run ("plan mode") branches and the OpenAEV-owned timeout policy
 * (deadline stamping, winddown nudges, hard stop) of {@link AutonomousRunService}. Kept as a plain
 * Mockito unit test (no Spring context / Docker) so it verifies the suppression + guard logic in
 * isolation: a plan run never dispatches, only a settled plan can be promoted, each winddown phase
 * fires at most once, and the hard stop is claimed exactly once.
 */
@ExtendWith(MockitoExtension.class)
class AutonomousRunServiceTest {

  @Mock private AutonomousRunRepository runRepository;
  @Mock private AutonomousDirectiveRepository directiveRepository;
  @Mock private AutonomousEventService eventService;
  @Mock private WorkflowService workflowService;
  @Mock private ExerciseService exerciseService;
  @Mock private PreviewFeatureService previewFeatureService;

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
    when(runRepository.settleTerminalStatusIfActive(
            eq("run-1"), eq("tenant-1"), eq(AutonomousRunStatus.CANCELED), any(Instant.class)))
        .thenReturn(1);

    service.enforceDeadline("run-1", "tenant-1");

    verify(exerciseService).changeExerciseStatus(ExerciseStatus.CANCELED, "sim-1");
    verify(eventService)
        .append(
            eq("run-1"),
            eq("sim-1"),
            eq(AutonomousEventType.STATUS),
            eq("Run timed out"),
            anyString(),
            eq(null));
    verify(directiveRepository, never()).save(any());
  }

  @Test
  @DisplayName("the hard stop is claimed once: a lost race with cancel/reconcile stays silent")
  void enforceDeadlineHardStopClaimedOnce() {
    AutonomousRun run = liveRun(Instant.now().minusSeconds(5));
    when(runRepository.findByIdAndTenantId("run-1", "tenant-1")).thenReturn(Optional.of(run));
    // An operator Stop or a read-path reconcile settled the run first: the conditional UPDATE
    // matches no row, so this watchdog must not narrate a second terminal event.
    when(runRepository.settleTerminalStatusIfActive(
            eq("run-1"), eq("tenant-1"), eq(AutonomousRunStatus.CANCELED), any(Instant.class)))
        .thenReturn(0);

    service.enforceDeadline("run-1", "tenant-1");

    verifyNoInteractions(eventService, exerciseService, directiveRepository);
  }

  // endregion
}
