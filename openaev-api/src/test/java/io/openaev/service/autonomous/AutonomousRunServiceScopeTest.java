package io.openaev.service.autonomous;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.api.chaining.dto.WorkflowScopeRuleInput;
import io.openaev.config.OpenAEVConfig;
import io.openaev.config.TenantWriteScopeResolver;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.database.model.ScopeRuleSelectedMode;
import io.openaev.database.model.ScopeRuleSource;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.autonomous.AutonomousEventType;
import io.openaev.database.model.autonomous.AutonomousRun;
import io.openaev.database.model.autonomous.AutonomousScopeTarget;
import io.openaev.database.repository.AssetGroupRepository;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.FindingRepository;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.SettingRepository;
import io.openaev.database.repository.TeamRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.database.repository.autonomous.AutonomousDirectiveRepository;
import io.openaev.database.repository.autonomous.AutonomousRunRepository;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.service.EndpointService;
import io.openaev.service.ScenarioToExerciseService;
import io.openaev.service.chaining.WorkflowService;
import io.openaev.service.scenario.ScenarioService;
import io.openaev.xtmone.XtmOneClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the orchestrator scope callback ({@link AutonomousRunService#setRunScope}): the
 * resolved scope must be recorded on the run AUTHORITATIVELY (saved before any projection), and the
 * two secondary projections - the workflow allowlist mirror and the targeted-team enablement - must
 * be genuinely best-effort AND independent of each other. A projection failure previously
 * propagated out of the callback as a 500 and stalled the run (issue #7472).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AutonomousRunService scope callback")
class AutonomousRunServiceScopeTest {

  private static final String RUN_ID = "run-1";
  private static final String SCENARIO_ID = "scenario-1";
  private static final String SIMULATION_ID = "sim-1";

  @Mock private AutonomousRunRepository runRepository;
  @Mock private AutonomousDirectiveRepository directiveRepository;
  @Mock private AutonomousEventService eventService;
  @Mock private AutonomousObjectiveTemplateService templateService;
  @Mock private ScenarioService scenarioService;
  @Mock private ScenarioToExerciseService scenarioToExerciseService;
  @Mock private WorkflowService workflowService;
  @Mock private ExerciseService exerciseService;
  @Mock private XtmOneClient xtmOneClient;
  @Mock private OpenAEVConfig openAEVConfig;
  @Mock private ObjectMapper objectMapper;
  @Mock private InjectRepository injectRepository;
  @Mock private InjectExpectationRepository injectExpectationRepository;
  @Mock private FindingRepository findingRepository;
  @Mock private EndpointService endpointService;
  @Mock private TeamRepository teamRepository;
  @Mock private UserRepository userRepository;
  @Mock private AssetGroupRepository assetGroupRepository;
  @Mock private ExerciseRepository exerciseRepository;
  @Mock private SettingRepository settingRepository;
  @Mock private AutonomousRunReconciliationWriter reconciliationWriter;
  @Mock private TenantWriteScopeResolver writeScopeResolver;
  @Mock private TenantScopedTransaction tenantTx;
  @Mock private AutonomousRunAccessControl accessControl;

  @InjectMocks private AutonomousRunService runService;

  @Captor private ArgumentCaptor<List<WorkflowScopeRuleInput>> rulesCaptor;

  private AutonomousRun run;

  @BeforeEach
  void setUp() {
    run = new AutonomousRun();
    run.setTenant(new Tenant("tenant-1"));
    run.setScenarioId(SCENARIO_ID);
    run.setSimulationId(SIMULATION_ID);
    when(runRepository.findById(RUN_ID)).thenReturn(Optional.of(run));
    when(runRepository.save(any(AutonomousRun.class))).thenAnswer(inv -> inv.getArgument(0));
  }

  @AfterEach
  void cleanUpTenantContext() {
    TenantContext.clearCurrentTenant();
  }

  @Test
  @DisplayName("The run's scope is saved BEFORE the workflow mirror, with the mapped projections")
  void given_aResolvedScope_when_settingIt_then_runIsSavedBeforeTheMirror() {
    List<AutonomousScopeTarget> scope =
        List.of(
            new AutonomousScopeTarget("ASSETS_GROUPS", "group-1"),
            new AutonomousScopeTarget("TEAMS", "team-1"));

    AutonomousRun saved = runService.setRunScope(RUN_ID, scope);

    // The run row is the authoritative record: saved first, projections filled from the scope.
    InOrder inOrder = inOrder(runRepository, workflowService);
    inOrder.verify(runRepository).save(run);
    inOrder
        .verify(workflowService)
        .writeAllowlistScopeIsolated(eq(SCENARIO_ID), eq(SIMULATION_ID), anyList(), eq(true));
    assertThat(saved.getScope()).hasSize(2);
    assertThat(saved.getScopeAssetGroupId()).isEqualTo("group-1");
    assertThat(saved.getScopeTeamId()).isEqualTo("team-1");

    // The mirror receives the scope translated to ALLOWLIST rules (unknown kinds dropped).
    verify(workflowService)
        .writeAllowlistScopeIsolated(
            eq(SCENARIO_ID), eq(SIMULATION_ID), rulesCaptor.capture(), eq(true));
    assertThat(rulesCaptor.getValue())
        .extracting(
            WorkflowScopeRuleInput::getSelectedMode,
            WorkflowScopeRuleInput::getRuleSource,
            WorkflowScopeRuleInput::getRuleValue)
        .containsExactly(
            tuple(ScopeRuleSelectedMode.ALLOWLIST, ScopeRuleSource.ASSET_GROUP, "group-1"),
            tuple(ScopeRuleSelectedMode.ALLOWLIST, ScopeRuleSource.TEAM, "team-1"));
  }

  @Test
  @DisplayName("A workflow-mirror failure neither fails the callback nor skips team enablement")
  void given_theMirrorThrows_when_settingScope_then_scopeIsRecordedAndTeamsStillEnabled() {
    doThrow(new IllegalStateException("action-target realignment failed"))
        .when(workflowService)
        .writeAllowlistScopeIsolated(anyString(), anyString(), anyList(), anyBoolean());
    List<AutonomousScopeTarget> scope = List.of(new AutonomousScopeTarget("TEAMS", "team-1"));

    assertThatCode(() -> runService.setRunScope(RUN_ID, scope)).doesNotThrowAnyException();

    // The scope stays recorded, the OTHER best-effort projection still ran, and the DECISION
    // timeline entry is still appended - the mirror failure is contained to its own step.
    verify(runRepository).save(run);
    verify(exerciseService).enableTargetedTeamMembersIsolated(SIMULATION_ID, List.of("team-1"));
    verify(eventService)
        .append(
            eq(RUN_ID),
            eq("tenant-1"),
            eq(SIMULATION_ID),
            eq(AutonomousEventType.DECISION),
            eq("Scope set"),
            anyString(),
            isNull());
  }

  @Test
  @DisplayName("A team-enablement failure does not fail the callback either")
  void given_teamEnablementThrows_when_settingScope_then_callbackStillSucceeds() {
    doThrow(new IllegalStateException("exercise_teams_users insert race"))
        .when(exerciseService)
        .enableTargetedTeamMembersIsolated(anyString(), anyList());
    List<AutonomousScopeTarget> scope = List.of(new AutonomousScopeTarget("TEAMS", "team-1"));

    AutonomousRun saved = runService.setRunScope(RUN_ID, scope);

    assertThat(saved.getScopeTeamId()).isEqualTo("team-1");
    verify(eventService)
        .append(
            eq(RUN_ID),
            eq("tenant-1"),
            eq(SIMULATION_ID),
            eq(AutonomousEventType.DECISION),
            eq("Scope set"),
            anyString(),
            isNull());
  }

  @Test
  @DisplayName("A plan/author-scenario run (no simulation) skips team enablement entirely")
  void given_aRunWithoutSimulation_when_settingScope_then_teamEnablementIsSkipped() {
    run.setSimulationId(null);
    List<AutonomousScopeTarget> scope = List.of(new AutonomousScopeTarget("TEAMS", "team-1"));

    runService.setRunScope(RUN_ID, scope);

    verify(exerciseService, never()).enableTargetedTeamMembersIsolated(anyString(), anyList());
  }

  @Test
  @DisplayName("Projections run under the run's v1 tenant scope, cleared again afterwards")
  void given_theLegacyCallbackRoute_when_settingScope_then_projectionsRunUnderTheRunTenant() {
    // The legacy non-prefixed orchestrator route never sets the v1 TenantContext, so the
    // v1-filtered projection reads would otherwise fall back to the default tenant and silently
    // match nothing for a run owned by another tenant. Clear the context the auto-registered
    // DefaultTenantExtension seeds for every test to reproduce that route's no-tenant thread.
    TenantContext.clearCurrentTenant();
    List<String> seenTenants = new ArrayList<>();
    doAnswer(
            inv -> {
              seenTenants.add(TenantContext.getCurrentTenant());
              return null;
            })
        .when(workflowService)
        .writeAllowlistScopeIsolated(anyString(), anyString(), anyList(), anyBoolean());
    doAnswer(
            inv -> {
              seenTenants.add(TenantContext.getCurrentTenant());
              return null;
            })
        .when(exerciseService)
        .enableTargetedTeamMembersIsolated(anyString(), anyList());

    runService.setRunScope(RUN_ID, List.of(new AutonomousScopeTarget("TEAMS", "team-1")));

    assertThat(seenTenants).containsExactly("tenant-1", "tenant-1");
    // The thread carried no tenant before the callback - it must not keep one after it.
    assertThat(TenantContext.hasCurrentTenant()).isFalse();
  }

  @Test
  @DisplayName("The operator route's caller tenant is restored after the projections")
  void given_aCallerTenantOnTheThread_when_settingScope_then_itIsRestored() {
    TenantContext.setCurrentTenant("caller-tenant");

    runService.setRunScope(RUN_ID, List.of(new AutonomousScopeTarget("TEAMS", "team-1")));

    assertThat(TenantContext.getCurrentTenant()).isEqualTo("caller-tenant");
  }
}
