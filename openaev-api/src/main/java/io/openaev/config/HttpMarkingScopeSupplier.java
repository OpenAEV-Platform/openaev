package io.openaev.config;

import io.openaev.config.cache.MarkingClearanceCacheManager;
import io.openaev.context.MarkingCtx;
import io.openaev.context.MarkingScopeSupplier;
import io.openaev.context.TxCtx;
import io.openaev.database.model.User;
import io.openaev.service.UserService;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Derives the HTTP caller's marking clearance for the scope aspect.
 *
 * <p>This is the API-side half of {@link MarkingScopeSupplier}: the aspect lives in {@code
 * openaev-model} and cannot reach the security context or the clearance cache, both of which live
 * here.
 *
 * <p><b>Fail-closed, and note what that means for marking.</b> No principal, an anonymous caller,
 * or a tenant scope that grants nothing all yield {@link MarkingCtx#none()} — which still admits
 * unmarked rows. The failure mode is a narrower result set, never a wider one. That asymmetry with
 * the tenant dimension (where fail-closed means zero rows) is deliberate: a marking is a
 * sensitivity label on a subset of rows, not a boundary around all of them.
 *
 * <p>{@link TxCtx.AllTenants} resolves to {@code none()} rather than to every marking on the
 * platform. It is an unresolved background intention that should never reach an HTTP transaction;
 * granting a platform-wide clearance for it would turn a plumbing mistake into a disclosure.
 */
@Component
@RequiredArgsConstructor
public class HttpMarkingScopeSupplier implements MarkingScopeSupplier {

  private final MarkingClearanceCacheManager clearanceCache;

  // @Lazy: UserService sits high in the service graph, and this component is pulled in by an aspect
  // that many of those services are themselves advised by.
  @Lazy private final UserService userService;

  @Override
  public MarkingCtx clearanceFor(TxCtx tenantScope) {
    if (!(tenantScope instanceof TxCtx.Restricted restricted)) {
      // Missing is already fail-closed on the tenant side; AllTenants is a background intention.
      return MarkingCtx.none();
    }
    User currentUser = userService.currentUserOrNull();
    if (currentUser == null) {
      return MarkingCtx.none();
    }
    boolean bypass = currentUser.isAdminOrBypass();

    // A marking definition belongs to exactly one tenant, so ids cannot collide across them and the
    // union is unambiguous: acting on N tenants means holding each one's clearance in that tenant.
    Set<String> markingIds = new LinkedHashSet<>();
    for (String tenantId : restricted.tenantIds()) {
      MarkingCtx perTenant = clearanceCache.findClearance(currentUser.getId(), tenantId, bypass);
      if (perTenant instanceof MarkingCtx.Restricted granted) {
        markingIds.addAll(granted.markingIds());
      }
    }
    return markingIds.isEmpty() ? MarkingCtx.none() : MarkingCtx.forMarkings(markingIds);
  }
}
