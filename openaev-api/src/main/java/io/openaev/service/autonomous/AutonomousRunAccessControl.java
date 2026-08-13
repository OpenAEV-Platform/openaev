package io.openaev.service.autonomous;

import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.User;
import io.openaev.database.model.autonomous.AutonomousRun;
import io.openaev.service.PermissionService;
import io.openaev.service.UserService;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * Resource-level RBAC for autonomous (AI-driven) attack-path runs.
 *
 * <p>The {@link io.openaev.api.autonomous.AutonomousRunApi} endpoints keep
 * {@code @AccessControl(skipRBAC = true)} because a run's authority is not a resource the
 * declarative aspect can name: it DERIVES from the run's bound simulation (a live run) or, for a
 * plan / author-scenario run that provisions no simulation, its scenario. This guard is the real
 * gate - it maps a run operation onto the SIMULATION / SCENARIO permission the operator already
 * holds:
 *
 * <ul>
 *   <li>a READ operation (open the cockpit, read the timeline / directives) requires {@code READ}
 *       on the bound simulation (or scenario);
 *   <li>a MANAGE operation (start / pause / resume / cancel / restart / promote / convert / steer /
 *       edit scope) requires {@code LAUNCH} - controlling an autonomous run is a launch-class
 *       action, the same capability that authorizes running the underlying simulation.
 * </ul>
 *
 * <p>Without this, every Enterprise-Edition user could drive ANY tenant run through the
 * skipRBAC-annotated controller (and {@link AutonomousRunService#list()} leaked every run's
 * objective + status tenant-wide). Enforcement mirrors {@link
 * io.openaev.service.attackpath.AttackPathAccessControl}: the same current-user source as {@link
 * io.openaev.aop.AccessControlAspect} ({@link UserService#currentUser()}), and admins / bypass
 * short-circuit inside {@link PermissionService#hasPermission}.
 *
 * <p>The orchestrator CALLBACK endpoints (events / status / directive consumption / attack-path
 * authoring / scope) are deliberately NOT gated here. They are authenticated with the tenant's
 * single configured XTM One service token - one identity for every run, never the operator's - so a
 * resource check would break live orchestration for any run whose operator differs from the token
 * owner. They stay behind the Enterprise-Edition license + the {@code INJECT_CHAINING} preview
 * feature. Restricting them to the actual service identity (today any authenticated EE user can
 * reach them) and activating tenant isolation on the {@code autonomous_*} tables are tracked as
 * follow-up hardening in issue #7396 - both need infrastructure work (a callback service identity;
 * the v2 tenant-table activation procedure) beyond this gate.
 */
@org.springframework.stereotype.Component
@RequiredArgsConstructor
public class AutonomousRunAccessControl {

  private final UserService userService;
  private final PermissionService permissionService;

  /** Throws 403 unless the caller can READ the run's bound simulation / scenario. */
  public void assertCanRead(AutonomousRun run) {
    if (!granted(userService.currentUser(), run, Action.READ)) {
      throw denied(run);
    }
  }

  /**
   * Throws 403 unless the caller can control (LAUNCH) the run's bound simulation / scenario. Guards
   * every lifecycle + steering mutation (start / pause / resume / cancel / restart / promote /
   * convert / directive / live-configuration / scope).
   */
  public void assertCanManage(AutonomousRun run) {
    if (!granted(userService.currentUser(), run, Action.LAUNCH)) {
      throw denied(run);
    }
  }

  /**
   * Keeps only the runs the caller can READ. Resolves the current user ONCE (it is DB-backed for
   * non-admins) rather than per row, mirroring {@link
   * io.openaev.service.attackpath.AttackPathAccessControl#retainReadable}.
   */
  public List<AutonomousRun> retainReadable(List<AutonomousRun> runs) {
    User user = userService.currentUser();
    return runs.stream().filter(run -> granted(user, run, Action.READ)).toList();
  }

  /** Requires READ on a scenario before an operator reads an AI-run configuration through it. */
  public void assertCanReadScenario(String scenarioId) {
    assertScenario(scenarioId, Action.READ);
  }

  /**
   * Requires LAUNCH on a scenario before an operator launches / plans / configures an autonomous
   * run on it (the scenario the operator already sees in the UI and holds a grant or capability
   * for).
   */
  public void assertCanManageScenario(String scenarioId) {
    assertScenario(scenarioId, Action.LAUNCH);
  }

  /**
   * Requires the launch-assessment capability before provisioning a brand-new autonomous run + its
   * auto-created scenario (there is no pre-existing resource to grant-check against). A user who
   * cannot launch assessments must not be able to spin up an autonomous run they could then drive.
   */
  public void assertCanCreate() {
    if (!permissionService.hasCapabilityPermission(
        userService.currentUser(), ResourceType.SIMULATION, Action.LAUNCH)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Missing capability to launch an autonomous run");
    }
  }

  /**
   * Throws 403 unless the caller is a platform administrator. Gates the tenant-global default-agent
   * writes ({@code PUT /autonomous-runs/default-agents}), which persist a {@code tenant IS NULL}
   * setting and must not be writable by every Enterprise-Edition user.
   */
  public void assertAdmin() {
    if (!userService.currentUser().isAdmin()) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Only an administrator can change the default agents");
    }
  }

  private void assertScenario(String scenarioId, Action action) {
    if (!StringUtils.hasText(scenarioId)) {
      // The caller validates presence and returns 400 itself; nothing to gate here.
      return;
    }
    if (!permissionService.hasPermission(
        userService.currentUser(), Optional.empty(), scenarioId, ResourceType.SCENARIO, action)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied on scenario");
    }
  }

  /**
   * The single definition of "this user may act on this run at this level": checked against the
   * run's simulation when it has one, else its scenario (plan / author-scenario runs), else - for a
   * malformed run bound to neither - a capability-only check, so it is never a silent open door.
   */
  private boolean granted(User user, AutonomousRun run, Action action) {
    String simulationId = run.getSimulationId();
    if (StringUtils.hasText(simulationId)) {
      return permissionService.hasPermission(
          user, Optional.empty(), simulationId, ResourceType.SIMULATION, action);
    }
    String scenarioId = run.getScenarioId();
    if (StringUtils.hasText(scenarioId)) {
      return permissionService.hasPermission(
          user, Optional.empty(), scenarioId, ResourceType.SCENARIO, action);
    }
    return permissionService.hasCapabilityPermission(user, ResourceType.SIMULATION, action);
  }

  private static ResponseStatusException denied(AutonomousRun run) {
    return new ResponseStatusException(
        HttpStatus.FORBIDDEN, "Access denied on autonomous run " + run.getId());
  }
}
