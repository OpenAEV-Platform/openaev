package io.openaev.utils;

import io.openaev.context.TxCtx;
import java.util.Objects;
import java.util.Set;

/** Utility helpers to extract tenant ids from a request-scoped {@link TxCtx}. */
public final class TxCtxScopeUtils {

  private TxCtxScopeUtils() {}

  public static Set<String> tenantIdsFromHTTPCtx(TxCtx ctx) {
    Objects.requireNonNull(ctx, "ctx must not be null");
    return switch (ctx) {
      case TxCtx.Missing ignored -> Set.of();
      case TxCtx.Restricted restricted -> Set.copyOf(restricted.tenantIds());
      case TxCtx.AllTenants ignored -> {
        throw new IllegalArgumentException("AllTenants is not valid on the HTTP API path");
      }
    };
  }
}
