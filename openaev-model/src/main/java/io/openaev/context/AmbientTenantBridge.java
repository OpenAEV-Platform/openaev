package io.openaev.context;

import jakarta.persistence.EntityManager;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.hibernate.internal.FilterImpl;
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
 * <p>Inside the call the {@code tenantFilter} of the current transaction follows the write tenant.
 * On return it goes back to exactly the state found on entry: armed on the same tenant, or absent
 * when the transaction had none, as one opened by the background primitive or a cross-tenant sweep
 * that disabled it. Re-arming it unconditionally would narrow the rest of such a transaction to one
 * tenant. The parameter of an enabled filter is only readable on Hibernate's {@link FilterImpl},
 * which the snapshot depends on.
 */
@Component
@RequiredArgsConstructor
public class AmbientTenantBridge {

  private static final String TENANT_FILTER = "tenantFilter";
  private static final String TENANT_PARAMETER = "tenantId";

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
    boolean inTransaction = TransactionSynchronizationManager.isActualTransactionActive();
    TenantFilterState previousFilter = inTransaction ? snapshotTenantFilter() : null;
    TenantContext.setCurrentTenant(tenantId);
    if (inTransaction) {
      session().enableFilter(TENANT_FILTER).setParameter(TENANT_PARAMETER, tenantId);
    }
    try {
      return work.call();
    } finally {
      if (previousTenant == null) {
        TenantContext.clearCurrentTenant();
      } else {
        TenantContext.setCurrentTenant(previousTenant);
      }
      if (inTransaction) {
        restoreTenantFilter(previousFilter);
      }
    }
  }

  private TenantFilterState snapshotTenantFilter() {
    Filter filter = session().getEnabledFilter(TENANT_FILTER);
    if (filter == null) {
      return TenantFilterState.DISABLED;
    }
    if (!(filter instanceof FilterImpl enabled)) {
      throw new IllegalStateException(
          "cannot read the " + TENANT_FILTER + " parameter from " + filter.getClass().getName());
    }
    return new TenantFilterState(true, (String) enabled.getParameter(TENANT_PARAMETER));
  }

  private void restoreTenantFilter(TenantFilterState state) {
    Session session = session();
    if (!state.enabled()) {
      session.disableFilter(TENANT_FILTER);
      return;
    }
    Filter filter = session.enableFilter(TENANT_FILTER);
    if (state.tenantId() != null) {
      filter.setParameter(TENANT_PARAMETER, state.tenantId());
    }
  }

  private Session session() {
    return entityManager.unwrap(Session.class);
  }

  /** What {@code tenantFilter} looked like when the bridge was entered. */
  private record TenantFilterState(boolean enabled, String tenantId) {
    static final TenantFilterState DISABLED = new TenantFilterState(false, null);
  }
}
