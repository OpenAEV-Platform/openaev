package io.openaev.service.attackpath;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toSet;

import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.User;
import io.openaev.database.model.attackpath.projection.AttackPathSimSummaryRow;
import io.openaev.service.PermissionService;
import io.openaev.service.UserService;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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

  /**
   * Throws 403 unless the caller can READ the simulation; a seed id is always allowed. The attack
   * path rows the endpoint then reads can come from any tenant of {@code ctx}, so the caller must
   * be able to read it in each of them.
   */
  public void assertCanReadSimulation(TxCtx ctx, String simulationId) {
    User user = userService.currentUser();
    if (!permissionService.holdsInScope(ctx, () -> canRead(user, simulationId))) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied on simulation");
    }
  }

  /**
   * Keeps only the picker rows the caller can READ (seed rows always kept), each row decided in the
   * tenant it was read in. The request scope can name several tenants and the rows then come from
   * all of them, so a caller holding the right in one of them keeps that tenant's simulations and
   * loses only the others.
   *
   * <p>Resolves the current user ONCE, not per row: {@link UserService#currentUser()} is DB-backed
   * for non-admins, so a per-row call would be an N+1 on tenants with many simulations. The rows
   * are then grouped by tenant and that tenant is entered once for the whole group, the way {@code
   * NotifierApi} evaluates its capability once per tenant of the rows it returns: the cost is one
   * tenant entry per tenant of the scope, plus the per-row check itself, which is capability-first
   * (in memory on the user) and only falls back to a grant lookup, one per row, for a caller that
   * relies on resource grants.
   */
  public List<AttackPathSimSummaryRow> retainReadable(List<AttackPathSimSummaryRow> rows) {
    User user = userService.currentUser();
    Set<AttackPathSimSummaryRow> readable =
        rows.stream()
            .collect(
                groupingBy(
                    row ->
                        Objects.requireNonNull(
                            row.tenantId(), "a picker row must carry the tenant it was read in")))
            .entrySet()
            .stream()
            .flatMap(
                tenantRows ->
                    permissionService
                        .inTenant(
                            tenantRows.getKey(),
                            () ->
                                tenantRows.getValue().stream()
                                    .filter(row -> canRead(user, row.simulationId()))
                                    .toList())
                        .stream())
            .collect(toSet());
    // Reads the kept rows back from the query's order (biggest simulations first), which the
    // grouping above loses.
    return rows.stream().filter(readable::contains).toList();
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
