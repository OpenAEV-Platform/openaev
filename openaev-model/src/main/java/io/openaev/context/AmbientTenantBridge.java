package io.openaev.context;

import java.util.concurrent.Callable;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Runs a unit of work with the ambient {@link TenantContext} aligned on a resolved write tenant.
 *
 * <p>Code that has not moved to an explicit tenant argument takes its tenant from the ambient
 * context: {@code TenantBaseListener} stamps new rows from it and tenant-bound lookups pass it
 * explicitly. {@code TenantInterceptor} only sets it on the {@code /api/tenants/{tenantId}/} route,
 * so on the {@code X-Tenant-Ids} route it falls back to the default tenant while the rows
 * attributed explicitly follow the request scope, and one request writes to two tenants. Running
 * the work here holds the whole unit in the write tenant.
 *
 * <p>It does nothing when an ambient tenant is set and already is the write tenant. With none set,
 * {@link TenantContext#getCurrentTenant()} answers the default tenant, which does not count: the
 * listener that stamps new rows requires {@link TenantContext#hasCurrentTenant()}. On return the
 * ambient tenant goes back to exactly the state found on entry: the same tenant, or none. It goes
 * away with the ambient tenant layer.
 */
@Component
public class AmbientTenantBridge {

  public <T> T callInTenant(String tenantId, Supplier<T> work) {
    try {
      return callInTenantChecked(tenantId, work::get);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      // Unreachable: a Supplier declares no checked exception.
      throw new IllegalStateException(e);
    }
  }

  /** Same as {@link #callInTenant} for work that declares a checked exception. */
  public <T> T callInTenantChecked(String tenantId, Callable<T> work) throws Exception {
    boolean ambientSet = TenantContext.hasCurrentTenant();
    if (tenantId == null || (ambientSet && tenantId.equals(TenantContext.getCurrentTenant()))) {
      return work.call();
    }
    String previousTenant = ambientSet ? TenantContext.getCurrentTenant() : null;
    TenantContext.setCurrentTenant(tenantId);
    try {
      return work.call();
    } finally {
      if (previousTenant == null) {
        TenantContext.clearCurrentTenant();
      } else {
        TenantContext.setCurrentTenant(previousTenant);
      }
    }
  }
}
