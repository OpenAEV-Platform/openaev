package io.openaev.utils;

import io.openaev.context.TenantContext;
import io.openaev.context.TxCtx;
import io.openaev.rest.exception.TenantSelectorRequiredException;
import io.openaev.rest.exception.TenantWriteScopeException;
import java.util.Objects;
import java.util.Set;

/** Utility helpers to extract tenant ids from a request-scoped {@link TxCtx}. */
public final class TxCtxScopeUtils {

  private TxCtxScopeUtils() {}

  /**
   * Returns the explicit tenant ids for an HTTP request scope.
   *
   * <p>{@link TxCtx.Missing} is fail-closed and returns an empty list. {@link TxCtx.AllTenants} is
   * a background-only intention and must not reach HTTP read paths.
   */
  public static Set<String> tenantIdsFromHTTPCtx(TxCtx ctx) {
    Objects.requireNonNull(ctx, "ctx must not be null");
    return switch (ctx) {
      case TxCtx.Missing ignored -> Set.of();
      case TxCtx.Restricted restricted -> Set.copyOf(restricted.tenantIds());
      case TxCtx.AllTenants ignored -> {
        throw new TenantWriteScopeException(
            "AllTenants is not valid - provide a single-tenant scope or an explicit tenant selector.");
      }
    };
  }

  /**
   * The one tenant a request reads from. A read of a single tenant's configuration is refused, as a
   * missing tenant selector, when the scope holds none or several tenants: it never picks one for
   * the caller.
   */
  public static String requireSingleTenant(TxCtx ctx) {
    Set<String> tenantIds = tenantIdsFromHTTPCtx(ctx);
    if (tenantIds.size() != 1) {
      throw new TenantSelectorRequiredException();
    }
    return tenantIds.iterator().next();
  }

  /**
   * The tenants a request reads a row by id from: its resolved scope, or the v1 ambient tenant when
   * the request resolves none (an anonymous or membership-less caller on the non-prefixed route).
   *
   * <p>Used for simulations and scenarios by id, for injects by id (the lookup of an inject and the
   * tenant gate of the implant endpoints), for the custom dashboard of a resource, for the parent
   * an {@code @AccessControl} check loads and for the tenant a user is read in.
   *
   * <p>The fallback goes once exercises, scenarios and injects are tenant-isolated: an empty scope
   * then reads nothing on its own and no caller needs the ambient tenant to stand in for it.
   */
  public static Set<String> requestReadScope(TxCtx ctx) {
    Set<String> tenantIds = tenantIdsFromHTTPCtx(ctx);
    return tenantIds.isEmpty() ? Set.of(TenantContext.getCurrentTenant()) : tenantIds;
  }
}
