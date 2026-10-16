package io.openaev.service.tenants;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Tenant;
import jakarta.persistence.EntityManager;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The scope moves of tenant provisioning that a dependency manager living on the HTTP side cannot
 * make itself: the background primitive is kept off {@code io.openaev.rest..} and {@code
 * io.openaev.api..} ({@code TenantBackgroundTransactionRules.NO_PRIMITIVE_ON_HTTP_PATH}). Tenant
 * creation runs in the caller's request transaction, whose scope is the caller's tenants: it never
 * includes the tenant being created, and includes the default tenant only for its members.
 */
@Service
@RequiredArgsConstructor
public class TenantProvisioningScope {

  private final TenantScopedTransaction tenantTx;
  private final EntityManager entityManager;

  /**
   * Reads the default tenant's copies of the given built-in injector contracts, whatever the
   * caller's scope. Runs in a nested transaction, so the default scope ends with the read and never
   * reaches the managers that run later in the onboarding transaction. The contracts come back
   * detached, with their eager links loaded under the default scope.
   *
   * @param ids the built-in contract ids
   * @return the contracts the default tenant holds; an id it does not hold is simply absent
   */
  public List<InjectorContract> readDefaultInjectorContracts(Collection<String> ids) {
    return tenantTx.executeNew(
        TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID),
        () ->
            entityManager
                .createQuery(
                    "SELECT ic FROM InjectorContract ic WHERE ic.compositeId.id IN :ids"
                        + " AND ic.compositeId.tenantId = :tenantId",
                    InjectorContract.class)
                .setParameter("ids", ids)
                .setParameter("tenantId", Tenant.DEFAULT_TENANT_UUID)
                .getResultList());
  }

  /**
   * Scopes the onboarding transaction to the tenant being provisioned, so a manager reads the rows
   * the earlier managers wrote for it, uncommitted, in that same transaction.
   */
  public void scopeTo(Tenant tenant) {
    tenantTx.setScopeOnCurrentTransaction(TxCtx.forTenant(tenant.getId()));
  }
}
