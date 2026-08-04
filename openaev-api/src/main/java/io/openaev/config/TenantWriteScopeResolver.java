package io.openaev.config;

import io.openaev.context.TxCtx;
import io.openaev.rest.exception.TenantWriteScopeException;
import io.openaev.utils.TxCtxScopeUtils;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Decides which tenant a newly written row belongs to, the write-side counterpart of {@link
 * TenantScopeResolver}. The query inspector filters reads and validates the rows an UPDATE or
 * DELETE touches, but it cannot attribute the tenant of an {@code INSERT ... VALUES}; that is an
 * application-layer decision (B3).
 *
 * <p>If the caller supplies a tenant explicitly, it must lie within the request scope, otherwise
 * the write is refused. If the caller supplies nothing, the scope must pin exactly one tenant: a
 * missing scope is fail-closed and a multi-tenant scope is ambiguous, so both are refused (the
 * endpoint must carry an explicit single-tenant selector). Refusals are {@link
 * TenantWriteScopeException}, mapped to 400.
 */
@Component
public class TenantWriteScopeResolver {

  public String tenantForWrite(TxCtx scope, String suppliedTenant) {
    Set<String> scoped = TxCtxScopeUtils.tenantIdsFromCtx(scope);

    if (suppliedTenant != null && !suppliedTenant.isBlank()) {
      if (!scoped.contains(suppliedTenant)) {
        throw new TenantWriteScopeException(
            "Cannot write to tenant " + suppliedTenant + ", which is outside the request scope");
      }
      return suppliedTenant;
    }

    if (scoped.size() != 1) {
      throw new TenantWriteScopeException(
          "A create needs a single-tenant scope to attribute the row; the request scope holds "
              + scoped.size()
              + " tenants. Provide an explicit tenant selector.");
    }
    return scoped.iterator().next();
  }
}
