package io.openaev.service.attackpath;

import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.User;
import io.openaev.service.PermissionService;
import io.openaev.service.UserService;
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
    if (!canReadSimulation(simulationId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied on simulation");
    }
  }

  /** Whether the caller can READ the simulation; seed ids are always readable. */
  public boolean canReadSimulation(String simulationId) {
    if (AttackPathIds.isSeedId(simulationId)) {
      return true;
    }
    User user = userService.currentUser();
    return permissionService.hasPermission(
        user, Optional.empty(), simulationId, ResourceType.SIMULATION, Action.READ);
  }
}
