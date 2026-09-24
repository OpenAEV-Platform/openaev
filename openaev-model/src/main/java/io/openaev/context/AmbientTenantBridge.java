package io.openaev.context;

import jakarta.persistence.EntityManager;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.hibernate.Session;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Runs a unit of work with the ambient {@link TenantContext} aligned on a resolved write tenant.
 *
 * <p>Tables that are not tenant-active yet still take their tenant from the ambient context: {@code
 * TenantBaseListener} stamps new rows from it, the Hibernate {@code tenantFilter} scopes their
 * reads with it and tenant-bound lookups pass it explicitly. {@code TenantInterceptor} only sets it
 * on the {@code /api/tenants/{tenantId}/} route, so on the {@code X-Tenant-Ids} route it falls back
 * to the default tenant while the rows attributed explicitly follow the request scope, and one
 * request writes to two tenants. Running the work here keeps the whole unit in the write tenant.
 *
 * <p>It does nothing when the ambient tenant already is the write tenant, which is always the case
 * on the prefixed route. It goes away with the ambient tenant layer.
 *
 * <p>Never call it from inside a deliberately cross-tenant sweep, one that disabled {@code
 * tenantFilter} on its session to walk every tenant: the filter is re-armed on the ambient tenant
 * when the call returns, and the rest of that sweep would silently read one tenant only.
 */
@Component
@RequiredArgsConstructor
public class AmbientTenantBridge {

  private static final String TENANT_FILTER = "tenantFilter";

  private final EntityManager entityManager;

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
    if (tenantId == null || tenantId.equals(TenantContext.getCurrentTenant())) {
      return work.call();
    }
    String previousTenant =
        TenantContext.hasCurrentTenant() ? TenantContext.getCurrentTenant() : null;
    TenantContext.setCurrentTenant(tenantId);
    armTenantFilter();
    try {
      return work.call();
    } finally {
      if (previousTenant == null) {
        TenantContext.clearCurrentTenant();
      } else {
        TenantContext.setCurrentTenant(previousTenant);
      }
      armTenantFilter();
    }
  }

  /**
   * The filter was armed with the ambient tenant when the surrounding transactional method was
   * entered, so it has to follow the ambient tenant when that changes inside the transaction.
   */
  private void armTenantFilter() {
    if (TransactionSynchronizationManager.isActualTransactionActive()) {
      entityManager
          .unwrap(Session.class)
          .enableFilter(TENANT_FILTER)
          .setParameter("tenantId", TenantContext.getCurrentTenant());
    }
  }
}
