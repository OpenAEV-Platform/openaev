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

  @Test
  @DisplayName("read on a live run checks READ on its bound simulation")
  void readChecksSimulationRead() {
    when(permissionService.hasPermission(
            any(), any(), eq("sim-1"), eq(ResourceType.SIMULATION), eq(Action.READ)))
        .thenReturn(true);

    assertThatCode(() -> accessControl.assertCanRead(run("sim-1", "scenario-1")))
        .doesNotThrowAnyException();
    // The scenario is never consulted when a simulation is bound.
    verify(permissionService, never()).hasPermission(any(), any(), eq("scenario-1"), any(), any());
  }

  @Test
  @DisplayName("read is denied (403) when the caller lacks READ on the bound simulation")
  void readDeniedWithoutSimulationRead() {
    when(permissionService.hasPermission(
            any(), any(), eq("sim-1"), eq(ResourceType.SIMULATION), eq(Action.READ)))
        .thenReturn(false);

    assertThatThrownBy(() -> accessControl.assertCanRead(run("sim-1", "scenario-1")))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
  }

  @Test
  @DisplayName("manage on a live run checks LAUNCH on its bound simulation")
  void manageChecksSimulationLaunch() {
    when(permissionService.hasPermission(
            any(), any(), eq("sim-1"), eq(ResourceType.SIMULATION), eq(Action.LAUNCH)))
        .thenReturn(true);

    assertThatCode(() -> accessControl.assertCanManage(run("sim-1", null)))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("a plan run with no simulation derives its authority from the scenario (LAUNCH)")
  void manageFallsBackToScenarioForPlanRun() {
    when(permissionService.hasPermission(
            any(), any(), eq("scenario-1"), eq(ResourceType.SCENARIO), eq(Action.LAUNCH)))
        .thenReturn(true);

    assertThatCode(() -> accessControl.assertCanManage(run(null, "scenario-1")))
        .doesNotThrowAnyException();
    verify(permissionService, never())
        .hasPermission(any(), any(), any(), eq(ResourceType.SIMULATION), any());
  }

  @Test
  @DisplayName("a malformed run bound to neither sim nor scenario degrades to a capability check")
  void managerFallsBackToCapabilityWhenUnbound() {
    when(permissionService.hasCapabilityPermission(
            any(), eq(ResourceType.SIMULATION), eq(Action.LAUNCH)))
        .thenReturn(false);

    assertThatThrownBy(() -> accessControl.assertCanManage(run(null, null)))
        .isInstanceOf(ResponseStatusException.class);
    // Never a silent open door: it consulted the capability, not returned true by default.
    verify(permissionService)
        .hasCapabilityPermission(any(), eq(ResourceType.SIMULATION), eq(Action.LAUNCH));
  }

  @Test
  @DisplayName("retainReadable keeps only the runs the caller can READ")
  void retainReadableFiltersUnreadableRuns() {
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

  @Test
  @DisplayName("assertCanCreate requires the launch-assessment capability floor")
  void createRequiresLaunchCapability() {
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
  void manageScenarioNoOpForBlankId() {
    assertThatCode(() -> accessControl.assertCanManageScenario(" ")).doesNotThrowAnyException();
    verify(permissionService, never()).hasPermission(any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("default-agents write is admin-only")
  void adminGateForDefaultAgents() {
    user.setAdmin(false);
    assertThatThrownBy(() -> accessControl.assertAdmin())
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

    user.setAdmin(true);
    assertThatCode(() -> accessControl.assertAdmin()).doesNotThrowAnyException();
  }
}
