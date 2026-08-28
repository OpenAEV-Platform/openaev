package io.openaev.service.attackpath;

import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.User;
import io.openaev.database.model.attackpath.projection.AttackPathSimSummaryRow;
import io.openaev.service.PermissionService;
import io.openaev.service.UserService;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Resource-level RBAC for the attack-path read endpoints (#6647 hardening), seed-tolerant.
 *
 * <p>The endpoints keep {@code @AccessControl(skipRBAC = true)} because the declarative aspect
 * cannot express the seed exception: a synthetic seed simulation ({@code ap-seed-…}) is not a real
 * {@code exercise}, so a grant check would 403 it. This guard is the real gate — it enforces {@code
 * SIMULATION READ} on real simulations and lets seed ids through. The same current-user source as
 * {@link io.openaev.aop.AccessControlAspect} ({@link UserService#currentUser()}); {@code
 * SIMULATION} is grant-managed so {@link PermissionService#hasPermission} ignores the (empty)
 * mapping info.
 */
@Component
@RequiredArgsConstructor
public class AttackPathAccessControl {

  private final UserService userService;
  private final PermissionService permissionService;

  /** Throws 403 unless the caller can READ the simulation; a seed id is always allowed. */
  public void assertCanReadSimulation(String simulationId) {
    if (!canRead(userService.currentUser(), simulationId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied on simulation");
    }
  }

  /**
   * Keeps only the picker rows the caller can READ (seed rows always kept). Resolves the current
   * user ONCE, not per row: {@link UserService#currentUser()} is DB-backed for non-admins, so a
   * per-row call would be an N+1 on tenants with many simulations. The remaining per-row check is
   * capability-first (in memory on the user) and only falls back to a grant lookup for a user that
   * relies on resource grants.
   */
  public List<AttackPathSimSummaryRow> retainReadable(List<AttackPathSimSummaryRow> rows) {
    User user = userService.currentUser();
    return rows.stream().filter(row -> canRead(user, row.simulationId())).toList();
  }

  /**
   * The single definition of "this user can read this simulation's attack path": {@code SIMULATION
   * READ}, with seed ids always allowed.
   *
   * <p>Public because the real-time nudge must gate on the very same predicate as the read it
   * announces (#6647, spec 003, FR3). A copy in the stream listener would drift, and a bare {@code
   * hasPermission} would never deliver a nudge on a seeded simulation whose delta read returns 200
   * — realtime would silently die on the POC path. Takes the {@link User} explicitly so an
   * off-request caller (the stream's async broadcast, where {@link UserService#currentUser()} does
   * not work) can pass the consumer's resolved user.
   */
  public boolean canRead(User user, String simulationId) {
    return AttackPathIds.isSeedId(simulationId)
        || permissionService.hasPermission(
            user, Optional.empty(), simulationId, ResourceType.SIMULATION, Action.READ);
  }
}
