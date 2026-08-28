package io.openaev.config;

import io.openaev.context.TxCtx;
import io.openaev.rest.exception.TenantAccessDeniedException;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Component;

/**
 * Turns a per-request tenant selector into a {@link TxCtx}, applying the rule that rights are the
 * boundary and the request is only a selector (B2). A selected tenant the caller is not authorized
 * for is refused, never silently dropped; no selection means the caller's full authorized set; an
 * empty result is the missing scope (fail-closed). The resolved scope is sorted, so it is
 * deterministic (a stable GUC value and HTTP cache key).
 */
@Component
public class TenantScopeResolver {

  /**
   * @param selector the tenants the request asks for, empty for "no narrowing" (never null)
   * @param authorized the tenants the caller is a member of (never null)
   * @return the scope to run the transaction under
   * @throws TenantAccessDeniedException if the selector names a tenant outside the caller's rights
   */
  public TxCtx resolve(Set<String> selector, Set<String> authorized) {
    Objects.requireNonNull(selector, "selector must not be null");
    Objects.requireNonNull(authorized, "authorized must not be null");
    for (String tenant : selector) {
      if (!authorized.contains(tenant)) {
        throw new TenantAccessDeniedException(tenant);
      }
    }
    Set<String> effective = selector.isEmpty() ? authorized : selector;
    return effective.isEmpty() ? TxCtx.missing() : TxCtx.forTenants(new TreeSet<>(effective));
  }
}
