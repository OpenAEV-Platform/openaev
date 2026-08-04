package io.openaev.utils;

import io.openaev.context.TxCtx;
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
}
