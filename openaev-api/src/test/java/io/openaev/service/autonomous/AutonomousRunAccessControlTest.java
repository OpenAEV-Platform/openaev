package io.openaev.service.autonomous;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.User;
import io.openaev.database.model.autonomous.AutonomousRun;
import io.openaev.service.PermissionService;
import io.openaev.service.UserService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Unit tests for {@link AutonomousRunAccessControl}: the resource-level gate that replaces the
 * controller's {@code skipRBAC} for the operator surface. Verifies a run's authority is derived
 * from its bound simulation (preferred) or scenario, that read maps to {@code READ} and manage to
 * {@code LAUNCH}, that a run bound to neither degrades to a capability-only check (never a silent
 * open door), and that the tenant-global default-agent write is admin-only.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AutonomousRunAccessControl")
class AutonomousRunAccessControlTest {

  @Mock private UserService userService;
  @Mock private PermissionService permissionService;
  @InjectMocks private AutonomousRunAccessControl accessControl;

  private User user;

  @BeforeEach
  void setUp() {
    user = new User();
    lenient().when(userService.currentUser()).thenReturn(user);
  }

  private static AutonomousRun run(String simulationId, String scenarioId) {
    AutonomousRun run = new AutonomousRun();
    run.setId("run-1");
    run.setSimulationId(simulationId);
    run.setScenarioId(scenarioId);
    return run;
  }

  @Nested
  @DisplayName("Run authority derivation (simulation first, then scenario, then capability)")
  class RunAuthorityDerivation {

    @Test
    @DisplayName("Read on a live run checks READ on its bound simulation, never the scenario")
    void given_runBoundToSimulation_when_read_then_checksSimulationReadOnly() {
      when(permissionService.hasPermission(
              any(), any(), eq("sim-1"), eq(ResourceType.SIMULATION), eq(Action.READ)))
          .thenReturn(true);

      assertThatCode(() -> accessControl.assertCanRead(run("sim-1", "scenario-1")))
          .doesNotThrowAnyException();

      verify(permissionService, never())
          .hasPermission(any(), any(), eq("scenario-1"), any(), any());
    }

    @Test
    @DisplayName("Read is denied (403) when the caller lacks READ on the bound simulation")
    void given_noSimulationRead_when_read_then_denied() {
      when(permissionService.hasPermission(
              any(), any(), eq("sim-1"), eq(ResourceType.SIMULATION), eq(Action.READ)))
          .thenReturn(false);

      assertThatThrownBy(() -> accessControl.assertCanRead(run("sim-1", "scenario-1")))
          .isInstanceOfSatisfying(
              ResponseStatusException.class,
              ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("Manage on a live run checks LAUNCH on its bound simulation")
    void given_runBoundToSimulation_when_manage_then_checksSimulationLaunch() {
      when(permissionService.hasPermission(
              any(), any(), eq("sim-1"), eq(ResourceType.SIMULATION), eq(Action.LAUNCH)))
          .thenReturn(true);

      assertThatCode(() -> accessControl.assertCanManage(run("sim-1", null)))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("A plan run with no simulation derives its authority from the scenario (LAUNCH)")
    void given_planRunWithoutSimulation_when_manage_then_fallsBackToScenarioLaunch() {
      when(permissionService.hasPermission(
              any(), any(), eq("scenario-1"), eq(ResourceType.SCENARIO), eq(Action.LAUNCH)))
          .thenReturn(true);

      assertThatCode(() -> accessControl.assertCanManage(run(null, "scenario-1")))
          .doesNotThrowAnyException();

      verify(permissionService, never())
          .hasPermission(any(), any(), any(), eq(ResourceType.SIMULATION), any());
    }

    @Test
    @DisplayName("A malformed run bound to neither sim nor scenario degrades to a capability check")
    void given_runBoundToNothing_when_manage_then_fallsBackToCapabilityCheck() {
      when(permissionService.hasCapabilityPermission(
              any(), eq(ResourceType.SIMULATION), eq(Action.LAUNCH)))
          .thenReturn(false);

      assertThatThrownBy(() -> accessControl.assertCanManage(run(null, null)))
          .isInstanceOf(ResponseStatusException.class);

      // Never a silent open door: it consulted the capability, not returned true by default.
      verify(permissionService)
          .hasCapabilityPermission(any(), eq(ResourceType.SIMULATION), eq(Action.LAUNCH));
    }
  }

  @Nested
  @DisplayName("List filtering")
  class ListFiltering {

    @Test
    @DisplayName("retainReadable keeps only the runs the caller can READ")
    void given_mixedReadability_when_retainReadable_then_keepsOnlyReadableRuns() {
      AutonomousRun readable = run("sim-ok", null);
      AutonomousRun hidden = run("sim-nope", null);
      when(permissionService.hasPermission(
              any(), any(), eq("sim-ok"), eq(ResourceType.SIMULATION), eq(Action.READ)))
          .thenReturn(true);
      when(permissionService.hasPermission(
              any(), any(), eq("sim-nope"), eq(ResourceType.SIMULATION), eq(Action.READ)))
          .thenReturn(false);

      List<AutonomousRun> kept = accessControl.retainReadable(List.of(readable, hidden));

      assertThat(kept).containsExactly(readable);
    }
  }

  @Nested
  @DisplayName("Creation and scenario gates")
  class CreationAndScenarioGates {

    @Test
    @DisplayName("assertCanCreate requires the launch-assessment capability floor")
    void given_noLaunchCapability_when_create_then_denied() {
      when(permissionService.hasCapabilityPermission(
              any(), eq(ResourceType.SIMULATION), eq(Action.LAUNCH)))
          .thenReturn(false);

      assertThatThrownBy(() -> accessControl.assertCanCreate())
          .isInstanceOfSatisfying(
              ResponseStatusException.class,
              ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("assertCanManageScenario is a no-op for a blank scenario id (caller validates)")
    void given_blankScenarioId_when_manageScenario_then_noPermissionCheck() {
      assertThatCode(() -> accessControl.assertCanManageScenario(" ")).doesNotThrowAnyException();

      verify(permissionService, never()).hasPermission(any(), any(), any(), any(), any());
    }
  }

  @Nested
  @DisplayName("Admin gate")
  class AdminGate {

    @Test
    @DisplayName("Default-agents write is denied for a non-admin and allowed for an admin")
    void given_nonAdminThenAdmin_when_assertAdmin_then_deniedThenAllowed() {
      user.setAdmin(false);
      assertThatThrownBy(() -> accessControl.assertAdmin())
          .isInstanceOfSatisfying(
              ResponseStatusException.class,
              ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

      user.setAdmin(true);
      assertThatCode(() -> accessControl.assertAdmin()).doesNotThrowAnyException();
    }
  }
}
