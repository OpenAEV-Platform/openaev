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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.openaev.api.autonomous.dto.AutonomousRunCreateInput;
import io.openaev.api.autonomous.dto.ConvertToManualMode;
import io.openaev.api.chaining.dto.WorkflowScopeRuleInput;
import io.openaev.config.OpenAEVConfig;
import io.openaev.config.TenantWriteScopeResolver;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
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
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.autonomous.AutonomousDirectiveRepository;
import io.openaev.database.repository.autonomous.AutonomousRunRepository;
import io.openaev.rest.exception.ChainingException;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.service.ScenarioToExerciseService;
import io.openaev.service.chaining.WorkflowService;
import io.openaev.service.scenario.ScenarioService;
import io.openaev.xtmone.XtmOneClient;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
  @Mock private InjectRepository injectRepository;
  @Mock private InjectExpectationRepository injectExpectationRepository;
  @Mock private ScenarioService scenarioService;
  @Mock private ScenarioToExerciseService scenarioToExerciseService;
  @Mock private XtmOneClient xtmOneClient;
  @Mock private OpenAEVConfig openAEVConfig;
  @Mock private TenantWriteScopeResolver writeScopeResolver;
  @Mock private TenantScopedTransaction tenantTx;
  // Lenient by default (void asserts are no-ops): these unit tests exercise lifecycle logic, not
  // authorization. The deny paths are covered by AutonomousRunAccessControlTest.
  @Mock private AutonomousRunAccessControl accessControl;

  @InjectMocks private AutonomousRunService service;

  private static final TxCtx TX = TxCtx.forTenant("tenant-1");

  @BeforeEach
  void stubTenantWriteScope() {
    lenient().when(writeScopeResolver.tenantForWrite(any(), any())).thenReturn("tenant-1");
  }

  @Test
  @DisplayName("evaluateAttackPath is a no-op in plan mode (never touches the run workflow)")
  void evaluateAttackPathIsNoOpInPlanMode() {
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
    AutonomousRun run = new AutonomousRun();
    run.setPlanMode(false);
    run.setStatus(AutonomousRunStatus.RUNNING);
    when(runRepository.findByIdForUpdate("run-1")).thenReturn(Optional.of(run));

    assertThatThrownBy(() -> service.promoteToRealRun("run-1"))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  @DisplayName("promoteToRealRun refuses a plan that is still being designed (PLANNING)")
  void promoteRejectsUnsettledPlan() {
    AutonomousRun run = new AutonomousRun();
    run.setPlanMode(true);
    run.setStatus(AutonomousRunStatus.PLANNING);
    when(runRepository.findByIdForUpdate("run-1")).thenReturn(Optional.of(run));

    assertThatThrownBy(() -> service.promoteToRealRun("run-1"))
        .isInstanceOf(ResponseStatusException.class);
  }

  // region post-commit session-handle persistence

  /**
   * Pins the transaction shape of the post-commit session-handle write ({@code autonomous_runs} is
   * tenant-active, so it needs its own scoped transaction). Inside Spring's {@code afterCommit}
   * callback the committed transaction's resources are STILL bound to the thread (Spring clears
   * them only after the callbacks have run), so the write must open a {@code REQUIRES_NEW} scope
   * ({@code executeNew}); the plain top-level {@code execute()} would be refused by its own guard
   * there and fail every start/resume/launch post-commit. The no-transaction fallback path keeps
   * the plain top-level scope.
   */
  @Nested
  @DisplayName("Post-commit orchestrator session-handle persistence")
  class PersistSessionHandle {

    private AutonomousRun startableCreatedRun() {
      AutonomousRun run = new AutonomousRun();
      run.setId("run-1");
      run.setStatus(AutonomousRunStatus.CREATED);
      run.setPlanMode(false);
      run.setTenant(new Tenant("tenant-1"));
      return run;
    }

    private void stubStart(AutonomousRun run) {
      when(runRepository.findById("run-1")).thenReturn(Optional.of(run));
      when(runRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
      when(xtmOneClient.startAutonomousRun(
              any(),
              any(),
              any(),
              any(),
              any(),
              anyBoolean(),
              any(),
              any(),
              anyList(),
              any(),
              anyBoolean(),
              any(),
              any(),
              anyList(),
              anyMap()))
          .thenReturn(Map.of("session_id", "sess-1"));
    }

    @Test
    @DisplayName("without an active transaction, the handle write opens a plain top-level scope")
    void given_noTransaction_when_engaging_then_handlePersistsThroughTopLevelScope() {
      AutonomousRun run = startableCreatedRun();
      stubStart(run);
      doAnswer(
              invocation -> {
                ((Runnable) invocation.getArgument(1)).run();
                return null;
              })
          .when(tenantTx)
          .execute(eq(TX), any(Runnable.class));

      service.start("run-1");

      // The fallback path (no synchronization, no transaction) opens a plain scoped transaction
      // and the session id actually reaches the run row under the run's own tenant.
      verify(tenantTx).execute(eq(TX), any(Runnable.class));
      verify(tenantTx, never()).executeNew(any(TxCtx.class), any(Runnable.class));
      assertThat(run.getXtmSessionId()).isEqualTo("sess-1");
    }

    @Test
    @DisplayName("inside afterCommit (resources still bound), the handle write is REQUIRES_NEW")
    void given_afterCommitCallback_when_engaging_then_handlePersistsThroughRequiresNew() {
      AutonomousRun run = startableCreatedRun();
      stubStart(run);
      doAnswer(
              invocation -> {
                ((Runnable) invocation.getArgument(1)).run();
                return null;
              })
          .when(tenantTx)
          .executeNew(eq(TX), any(Runnable.class));

      TransactionSynchronizationManager.initSynchronization();
      TransactionSynchronizationManager.setActualTransactionActive(true);
      try {
        service.start("run-1");
        // start() only registered the engagement; nothing has been engaged or persisted yet.
        assertThat(run.getXtmSessionId()).isNull();
        // Fire the registered callback exactly like Spring does: after doCommit, with the
        // transaction thread-locals still bound (they are cleared only after the callbacks).
        TransactionSynchronizationManager.getSynchronizations()
            .forEach(TransactionSynchronization::afterCommit);
      } finally {
        TransactionSynchronizationManager.clear();
      }

      verify(tenantTx).executeNew(eq(TX), any(Runnable.class));
      verify(tenantTx, never()).execute(any(TxCtx.class), any(Runnable.class));
      assertThat(run.getXtmSessionId()).isEqualTo("sess-1");
    }
  }

  // endregion

  // region orchestrator status callback validation

  /**
   * Regression tests for the orchestrator status-callback transition matrix: a stale callback must
   * never resurrect an operator-owned state (CREATED pre-handoff / post-restart, PAUSED) or cross
   * the plan/live divide, and every rejected push is an idempotent no-op (never a save).
   */
  @Nested
  @DisplayName("Orchestrator status callback validation")
  class OrchestratorStatusValidation {

    private AutonomousRun lockedRun(AutonomousRunStatus status, boolean planMode) {
      AutonomousRun run = new AutonomousRun();
      run.setId("run-1");
      run.setSimulationId("sim-1");
      run.setStatus(status);
      run.setPlanMode(planMode);
      run.setTenant(new Tenant("tenant-1"));
      // updateStatus reads through the row-locking lookup so its check-then-write serialises with
      // the operator lifecycle writers.
      when(runRepository.findByIdForUpdate("run-1")).thenReturn(Optional.of(run));
      return run;
    }

    @Test
    @DisplayName("A stale callback cannot resurrect an operator pause (PAUSED source is a no-op)")
    void given_pausedRun_when_orchestratorPushesAnyStatus_then_noOp() {
      AutonomousRun run = lockedRun(AutonomousRunStatus.PAUSED, false);

      AutonomousRun result =
          service.updateStatus("run-1", AutonomousRunStatus.RUNNING, null, null, null);

      assertThat(result.getStatus()).isEqualTo(AutonomousRunStatus.PAUSED);
      assertThat(run.getStatus()).isEqualTo(AutonomousRunStatus.PAUSED);
      verify(runRepository, never()).save(any());
      verifyNoInteractions(eventService);
    }

    @Test
    @DisplayName("A stale callback cannot settle a paused run to a terminal state")
    void given_pausedRun_when_orchestratorPushesCompleted_then_noOp() {
      AutonomousRun run = lockedRun(AutonomousRunStatus.PAUSED, false);

      service.updateStatus("run-1", AutonomousRunStatus.COMPLETED, null, null, null);

      assertThat(run.getStatus()).isEqualTo(AutonomousRunStatus.PAUSED);
      verify(runRepository, never()).save(any());
    }

    @Test
    @DisplayName("A stale callback cannot drive a not-yet-started (CREATED) run")
    void given_createdRun_when_orchestratorPushesRunning_then_noOp() {
      AutonomousRun run = lockedRun(AutonomousRunStatus.CREATED, false);

      service.updateStatus("run-1", AutonomousRunStatus.RUNNING, null, null, null);

      assertThat(run.getStatus()).isEqualTo(AutonomousRunStatus.CREATED);
      verify(runRepository, never()).save(any());
    }

    @Test
    @DisplayName("A settled plan (PLANNED) cannot be reopened or overwritten by a late callback")
    void given_plannedPlanRun_when_orchestratorPushesAnyStatus_then_noOp() {
      AutonomousRun run = lockedRun(AutonomousRunStatus.PLANNED, true);

      service.updateStatus("run-1", AutonomousRunStatus.PLANNING, null, null, null);
      service.updateStatus("run-1", AutonomousRunStatus.FAILED, null, null, null);

      assertThat(run.getStatus()).isEqualTo(AutonomousRunStatus.PLANNED);
      verify(runRepository, never()).save(any());
      verifyNoInteractions(eventService);
    }

    @Test
    @DisplayName("A live run cannot be flipped across the plan/live divide (PLANNED target)")
    void given_liveRun_when_orchestratorPushesPlanned_then_noOp() {
      AutonomousRun run = lockedRun(AutonomousRunStatus.RUNNING, false);

      service.updateStatus("run-1", AutonomousRunStatus.PLANNED, null, null, null);

      assertThat(run.getStatus()).isEqualTo(AutonomousRunStatus.RUNNING);
      verify(runRepository, never()).save(any());
    }

    @Test
    @DisplayName("A plan run cannot be driven to the execute-only RUNNING state")
    void given_planRun_when_orchestratorPushesRunning_then_noOp() {
      AutonomousRun run = lockedRun(AutonomousRunStatus.PLANNING, true);

      service.updateStatus("run-1", AutonomousRunStatus.RUNNING, null, null, null);

      assertThat(run.getStatus()).isEqualTo(AutonomousRunStatus.PLANNING);
      verify(runRepository, never()).save(any());
    }

    @Test
    @DisplayName("A legitimate live-run push (RUNNING -> COMPLETED) is applied and narrated")
    void given_liveRun_when_orchestratorPushesCompleted_then_applied() {
      AutonomousRun run = lockedRun(AutonomousRunStatus.RUNNING, false);
      when(runRepository.save(run)).thenReturn(run);

      AutonomousRun result =
          service.updateStatus("run-1", AutonomousRunStatus.COMPLETED, null, null, null);

      assertThat(result.getStatus()).isEqualTo(AutonomousRunStatus.COMPLETED);
      verify(runRepository).save(run);
      verify(eventService)
          .append(
              eq("run-1"),
              eq("tenant-1"),
              eq("sim-1"),
              eq(AutonomousEventType.STATUS),
              anyString(),
              isNull(),
              isNull());
    }
  }

  // endregion

  // region network scope-rule matching (dual-stack CIDR)

  /**
   * Direct tests of the network scope-rule matcher: dual-stack CIDR containment must work for IPv6
   * (the original bug), never resolve a hostname through DNS, and never let a malformed rule (an
   * out-of-range prefix) match everything.
   */
  @Nested
  @DisplayName("Network scope-rule matching")
  class NetworkScopeRuleMatching {

    @Test
    @DisplayName("An IPv4 host matches an IPv4 CIDR rule")
    void given_ipv4Candidate_when_insideIpv4Cidr_then_matches() {
      assertThat(service.networkValueMatches("10.0.0.0/24", "10.0.0.42")).isTrue();
      assertThat(service.networkValueMatches("10.0.0.0/24", "10.0.1.42")).isFalse();
    }

    @Test
    @DisplayName("An IPv6 host matches an IPv6 CIDR rule (the original silent fall-through)")
    void given_ipv6Candidate_when_insideIpv6Cidr_then_matches() {
      assertThat(service.networkValueMatches("2001:db8::/32", "2001:db8::1")).isTrue();
      assertThat(service.networkValueMatches("2001:db8::/32", "2001:db9::1")).isFalse();
    }

    @Test
    @DisplayName("Mismatched address families never match")
    void given_familyMismatch_when_matching_then_false() {
      assertThat(service.networkValueMatches("10.0.0.0/8", "2001:db8::1")).isFalse();
      assertThat(service.networkValueMatches("2001:db8::/32", "10.0.0.1")).isFalse();
    }

    @Test
    @DisplayName("A hostname candidate is never evaluated against a CIDR rule (no DNS lookup)")
    void given_hostnameCandidate_when_ruleIsCidr_then_falseWithoutResolution() {
      assertThat(service.networkValueMatches("10.0.0.0/8", "intranet.example.org")).isFalse();
    }

    @Test
    @DisplayName("A malformed rule prefix (out of range) never matches anything")
    void given_outOfRangePrefix_when_matching_then_false() {
      assertThat(service.networkValueMatches("10.0.0.0/-1", "10.0.0.1")).isFalse();
      assertThat(service.networkValueMatches("10.0.0.0/33", "10.0.0.1")).isFalse();
      assertThat(service.networkValueMatches("2001:db8::/-1", "2001:db8::1")).isFalse();
      assertThat(service.networkValueMatches("2001:db8::/129", "2001:db8::1")).isFalse();
    }

    @Test
    @DisplayName("A non-CIDR rule still matches case-insensitively on the exact value")
    void given_exactRule_when_candidateDiffersOnlyByCase_then_matches() {
      assertThat(service.networkValueMatches("HOST.example.org", "host.example.org")).isTrue();
      assertThat(service.networkValueMatches("10.0.0.1", "10.0.0.1")).isTrue();
      assertThat(service.networkValueMatches("10.0.0.1", "10.0.0.2")).isFalse();
    }
  }

  // endregion

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
            eq("tenant-1"),
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
        .appendTerminalStatusOnce(
            eq("run-1"), eq("tenant-1"), eq("sim-1"), eq("Run timed out"), anyString());
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

  // region OpenAEV-owned liveness: short idle/stall watchdog (independent of the 24h deadline)

  /**
   * Tests for the stall watchdog {@link AutonomousRunService#enforceLiveness}. A live run that
   * stops posting timeline heartbeats for {@link AutonomousRunService#STALL_IDLE_SECONDS} is
   * settled to FAILED and narrated "Run stalled" - UNLESS the chained simulation still has work in
   * flight (an executing inject or an open expectation), which is a genuine {@code await_finding}
   * park and must be left alone. The flip is claimed exactly once, like the deadline hard stop.
   */
  private AutonomousRun stallableRun() {
    AutonomousRun run = new AutonomousRun();
    run.setId("run-1");
    run.setSimulationId("sim-1");
    run.setStatus(AutonomousRunStatus.RUNNING);
    run.setPlanMode(false);
    // A real start instant well outside the idle window so the fallback clock never masks the test.
    run.setStartedAt(Instant.now().minusSeconds(3600));
    Tenant tenant = new Tenant();
    tenant.setId("tenant-1");
    run.setTenant(tenant);
    return run;
  }

  @Test
  @DisplayName(
      "enforceLiveness settles a silent run with nothing in flight to FAILED (Run stalled)")
  void enforceLivenessSettlesSilentRun() throws Exception {
    AutonomousRun run = stallableRun();
    when(runRepository.findByIdForUpdate("run-1")).thenReturn(Optional.of(run));
    when(eventService.lastActivityAt("run-1")).thenReturn(Instant.now().minusSeconds(20 * 60));
    when(injectRepository.countByExerciseIdAndStatusNameIn(eq("sim-1"), any())).thenReturn(0L);
    when(injectExpectationRepository.countOpenByExerciseId("sim-1")).thenReturn(0L);
    when(runRepository.settleTerminalStatusIfRunning(
            eq("run-1"), eq("tenant-1"), eq(AutonomousRunStatus.FAILED), any(Instant.class)))
        .thenReturn(1);

    service.enforceLiveness("run-1", "tenant-1");

    // Torn down like an operator Stop, and the end is narrated once with the distinct stall title.
    verify(exerciseService).changeExerciseStatus(ExerciseStatus.CANCELED, "sim-1");
    verify(eventService)
        .appendTerminalStatusOnce(
            eq("run-1"), eq("tenant-1"), eq("sim-1"), eq("Run stalled"), anyString());
    // The read + flip are serialized against timeline appends in row -> advisory order: the run is
    // row-locked, THEN the per-run timeline advisory lock is held, THEN the liveness clock is read,
    // THEN the terminal flip runs - so a heartbeat racing the decision cannot slip in between.
    InOrder inOrder = inOrder(runRepository, eventService);
    inOrder.verify(runRepository).findByIdForUpdate("run-1");
    inOrder.verify(eventService).lockRunTimeline("run-1");
    inOrder.verify(eventService).lastActivityAt("run-1");
    inOrder
        .verify(runRepository)
        .settleTerminalStatusIfRunning(
            eq("run-1"), eq("tenant-1"), eq(AutonomousRunStatus.FAILED), any(Instant.class));
  }

  @Test
  @DisplayName(
      "enforceLiveness exempts a silent run with an inject still in flight (await_finding)")
  void enforceLivenessExemptsInFlightInject() {
    AutonomousRun run = stallableRun();
    when(runRepository.findByIdForUpdate("run-1")).thenReturn(Optional.of(run));
    when(eventService.lastActivityAt("run-1")).thenReturn(Instant.now().minusSeconds(20 * 60));
    // A step is still executing: the orchestrator is legitimately awaiting its result.
    when(injectRepository.countByExerciseIdAndStatusNameIn(eq("sim-1"), any())).thenReturn(1L);

    service.enforceLiveness("run-1", "tenant-1");

    verify(runRepository, never())
        .settleTerminalStatusIfRunning(anyString(), anyString(), any(), any(Instant.class));
    verifyNoInteractions(exerciseService);
  }

  @Test
  @DisplayName(
      "enforceLiveness exempts a silent run with an open expectation (no inject in flight)")
  void enforceLivenessExemptsOpenExpectation() {
    AutonomousRun run = stallableRun();
    when(runRepository.findByIdForUpdate("run-1")).thenReturn(Optional.of(run));
    when(eventService.lastActivityAt("run-1")).thenReturn(Instant.now().minusSeconds(20 * 60));
    when(injectRepository.countByExerciseIdAndStatusNameIn(eq("sim-1"), any())).thenReturn(0L);
    // e.g. a phishing lure whose inject already EXECUTED but whose click/detection is still
    // awaited.
    when(injectExpectationRepository.countOpenByExerciseId("sim-1")).thenReturn(3L);

    service.enforceLiveness("run-1", "tenant-1");

    verify(runRepository, never())
        .settleTerminalStatusIfRunning(anyString(), anyString(), any(), any(Instant.class));
    verifyNoInteractions(exerciseService);
  }

  @Test
  @DisplayName("enforceLiveness leaves a run active within the idle window untouched")
  void enforceLivenessIgnoresRecentlyActiveRun() {
    AutonomousRun run = stallableRun();
    when(runRepository.findByIdForUpdate("run-1")).thenReturn(Optional.of(run));
    // Heartbeat two minutes ago - well inside the idle window; nothing else is even consulted.
    when(eventService.lastActivityAt("run-1")).thenReturn(Instant.now().minusSeconds(2 * 60));

    service.enforceLiveness("run-1", "tenant-1");

    verifyNoInteractions(injectRepository, injectExpectationRepository, exerciseService);
    verify(runRepository, never())
        .settleTerminalStatusIfRunning(anyString(), anyString(), any(), any(Instant.class));
  }

  @Test
  @DisplayName(
      "enforceLiveness re-asserts RUNNING: a run parked for input since the sweep is spared")
  void enforceLivenessSkipsNonRunningRun() {
    AutonomousRun run = stallableRun();
    run.setStatus(AutonomousRunStatus.WAITING_INPUT);
    when(runRepository.findByIdForUpdate("run-1")).thenReturn(Optional.of(run));

    service.enforceLiveness("run-1", "tenant-1");

    // A WAITING_INPUT park is an open-ended operator wait; the watchdog must not even read its
    // clock.
    verifyNoInteractions(
        eventService, injectRepository, injectExpectationRepository, exerciseService);
    verify(runRepository, never())
        .settleTerminalStatusIfRunning(anyString(), anyString(), any(), any(Instant.class));
  }

  @Test
  @DisplayName("enforceLiveness stalls a run the worker never picked up (no timeline, stale start)")
  void enforceLivenessStallsNeverPickedUpRun() {
    AutonomousRun run = stallableRun();
    // No decision timeline yet and the live window opened 20 minutes ago: the orchestrator never
    // started. The fallback clock (startedAt) drives the stall decision.
    run.setStartedAt(Instant.now().minusSeconds(20 * 60));
    when(runRepository.findByIdForUpdate("run-1")).thenReturn(Optional.of(run));
    when(eventService.lastActivityAt("run-1")).thenReturn(null);
    when(injectRepository.countByExerciseIdAndStatusNameIn(eq("sim-1"), any())).thenReturn(0L);
    when(injectExpectationRepository.countOpenByExerciseId("sim-1")).thenReturn(0L);
    when(runRepository.settleTerminalStatusIfRunning(
            eq("run-1"), eq("tenant-1"), eq(AutonomousRunStatus.FAILED), any(Instant.class)))
        .thenReturn(1);

    service.enforceLiveness("run-1", "tenant-1");

    verify(eventService)
        .appendTerminalStatusOnce(
            eq("run-1"), eq("tenant-1"), eq("sim-1"), eq("Run stalled"), anyString());
  }

  @Test
  @DisplayName(
      "the stall flip is claimed once: a lost race with a concurrent transition stays silent")
  void enforceLivenessStallClaimedOnce() throws Exception {
    AutonomousRun run = stallableRun();
    when(runRepository.findByIdForUpdate("run-1")).thenReturn(Optional.of(run));
    when(eventService.lastActivityAt("run-1")).thenReturn(Instant.now().minusSeconds(20 * 60));
    when(injectRepository.countByExerciseIdAndStatusNameIn(eq("sim-1"), any())).thenReturn(0L);
    when(injectExpectationRepository.countOpenByExerciseId("sim-1")).thenReturn(0L);
    // Someone else settled / paused / parked the run between the idle read and this flip: the
    // RUNNING-guarded UPDATE matches no row, so nothing is narrated or torn down.
    when(runRepository.settleTerminalStatusIfRunning(
            eq("run-1"), eq("tenant-1"), eq(AutonomousRunStatus.FAILED), any(Instant.class)))
        .thenReturn(0);

    service.enforceLiveness("run-1", "tenant-1");

    verify(exerciseService, never()).changeExerciseStatus(any(), anyString());
    verify(eventService, never())
        .appendTerminalStatusOnce(anyString(), anyString(), anyString(), anyString(), anyString());
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
    run.setTenant(new Tenant("tenant-1"));
    return run;
  }

  /** Stubs the collaborators the restart teardown + re-provisioning path touches. */
  private void stubRestartCollaborators() {
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
    when(runRepository.findByIdForUpdate("run-1")).thenReturn(Optional.of(run));
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
    when(runRepository.findByIdForUpdate("run-1")).thenReturn(Optional.of(run));
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
    when(runRepository.findByIdForUpdate("run-1")).thenReturn(Optional.of(run));
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
    when(runRepository.findByIdForUpdate("run-1")).thenReturn(Optional.of(run));
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
    // A time-based (non-chained) scenario has no authored attack path to seed, so it cannot be
    // launched in autonomous mode - the entry point must 400 before creating any run.
    when(workflowService.isScenarioChaining("scenario-1")).thenReturn(false);

    assertThatThrownBy(() -> service.launchFromScenario(TX, "scenario-1", null))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    verify(runRepository, never()).save(any());
    verifyNoInteractions(xtmOneClient);
  }

  @Test
  @DisplayName("planScenario refuses a scenario that is not chained (nothing to author onto)")
  void planScenarioRejectsNonChainedScenario() {
    when(workflowService.isScenarioChaining("scenario-1")).thenReturn(false);

    assertThatThrownBy(() -> service.planScenario(TX, "scenario-1", null))
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
    when(workflowService.isScenarioChaining("scenario-1")).thenReturn(true);
    Scenario scenario = new Scenario();
    scenario.setId("scenario-1");
    scenario.setName("Ransomware kill chain");
    scenario.setTenant(new Tenant("tenant-1"));
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

    AutonomousRun run = service.planScenario(TX, "scenario-1", input);

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
    when(workflowService.isScenarioChaining("scenario-1")).thenReturn(true);
    // A prior plan run is still being designed (active): rebuilding would orphan it, so the entry
    // point must 409 before wiping anything or engaging the orchestrator.
    AutonomousRun prior = new AutonomousRun();
    prior.setId("prior-run");
    prior.setPlanMode(true);
    prior.setStatus(AutonomousRunStatus.PLANNING);
    when(runRepository.findByScenarioId("scenario-1")).thenReturn(Optional.of(prior));

    assertThatThrownBy(() -> service.planScenario(TX, "scenario-1", null))
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
    when(workflowService.isScenarioChaining("scenario-1")).thenReturn(true);
    Scenario scenario = new Scenario();
    scenario.setId("scenario-1");
    scenario.setName("Ransomware kill chain");
    scenario.setTenant(new Tenant("tenant-1"));
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

    AutonomousRun run = service.planScenario(TX, "scenario-1", input);

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
    when(workflowService.isScenarioChaining("scenario-1")).thenReturn(true);
    Scenario scenario = new Scenario();
    scenario.setId("scenario-1");
    scenario.setName("Ransomware kill chain");
    scenario.setTenant(new Tenant("tenant-1"));
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

    AutonomousRun run = service.planScenario(TX, "scenario-1", input);

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
            eq("tenant-1"),
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
    when(workflowService.isScenarioChaining("scenario-1")).thenReturn(true);
    Scenario scenario = new Scenario();
    scenario.setId("scenario-1");
    scenario.setName("Ransomware kill chain");
    scenario.setTenant(new Tenant("tenant-1"));
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

    AutonomousRun run = service.planScenario(TX, "scenario-1", input);

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
    when(workflowService.isScenarioChaining("scenario-1")).thenReturn(true);
    Scenario scenario = new Scenario();
    scenario.setId("scenario-1");
    scenario.setName("Ransomware kill chain");
    scenario.setTenant(new Tenant("tenant-1"));
    when(scenarioService.scenario("scenario-1")).thenReturn(scenario);
    AutonomousRun prior = new AutonomousRun();
    prior.setId("prior-run");
    prior.setPlanMode(true);
    prior.setStatus(AutonomousRunStatus.PLANNING);
    when(runRepository.findByScenarioId("scenario-1")).thenReturn(Optional.of(prior));

    AutonomousRunCreateInput input = new AutonomousRunCreateInput();
    input.setRefine(true);

    assertThatThrownBy(() -> service.planScenario(TX, "scenario-1", input))
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
    when(workflowService.isScenarioChaining("scenario-1")).thenReturn(true);
    Scenario scenario = new Scenario();
    scenario.setId("scenario-1");
    scenario.setName("Ransomware kill chain");
    scenario.setTenant(new Tenant("tenant-1"));
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
    service.planScenario(TX, "scenario-1", input);

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
    when(workflowService.isScenarioChaining("scenario-1")).thenReturn(true);
    Scenario scenario = new Scenario();
    scenario.setId("scenario-1");
    scenario.setName("Ransomware kill chain");
    scenario.setTenant(new Tenant("tenant-1"));
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
    service.planScenario(TX, "scenario-1", input);

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
    when(runRepository.findByScenarioId("scenario-1")).thenReturn(Optional.empty());

    service.supersedeSettledRunOnManualLaunch("scenario-1");

    verify(runRepository, never()).delete(any());
    verifyNoInteractions(xtmOneClient);
  }

  // endregion
}
