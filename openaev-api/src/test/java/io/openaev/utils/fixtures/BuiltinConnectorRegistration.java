package io.openaev.utils.fixtures;

import io.openaev.integration.BuiltinIntegrationFactory;
import io.openaev.integration.ManagerCreator;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registers a built-in connector for a tenant from a test, under that tenant's scope.
 *
 * <p>Production never registers a connector without a scope: every caller funnels through {@link
 * ManagerCreator#createManager}, which asserts it on whatever transaction it joins. A test that
 * calls an integration factory directly runs on a bare thread, outside any request, so nothing sets
 * the scope for it. Registration reads the tenant's preset domains to decide whether to create
 * them, and an unset scope is fail-closed: every existing row is invisible, the upsert concludes
 * the preset is absent, and the insert collides on {@code domains_domain_name_tenant_key}.
 *
 * <p>Unlike {@code createManager}, the scope is restored to what the caller had as soon as the
 * registration is done. In production, pinning the scope of the transaction being joined is the
 * point: the request or the job owns that transaction and wants that tenant. In a test it is a side
 * effect on someone else's transaction: a {@code @Transactional} test shares one transaction with
 * every endpoint it then calls, and a request carrying a different {@code TxCtx} is refused by
 * {@code TenantScopeTransactionAspect} rather than scoped. So this sets the scope for the
 * registration only, and puts back whatever was there.
 *
 * <p>The marking scope is deliberately left alone: the registration reads no marking-active table,
 * and widening the clearance of the caller's transaction is exactly the kind of side effect above.
 */
@Component
@RequiredArgsConstructor
public class BuiltinConnectorRegistration {

  private final EntityManager entityManager;

  /** Registers {@code factory}'s built-in connector for {@code tenantId}, under its scope. */
  @Transactional
  public void register(BuiltinIntegrationFactory factory, String tenantId) {
    String callerScope = currentScope();
    setScope(tenantId);
    try {
      factory.registerConnectorForTenant(tenantId);
    } catch (Exception e) {
      throw new RuntimeException(
          "Failed to register the built-in connector of " + factory.getClass().getSimpleName(), e);
    } finally {
      setScope(callerScope);
    }
  }

  private String currentScope() {
    return (String)
        noFlush(
                entityManager.createNativeQuery(
                    "SELECT coalesce(current_setting('app.current_tenants', true), '')"))
            .getSingleResult();
  }

  private void setScope(String scope) {
    noFlush(
            entityManager.createNativeQuery(
                "SELECT set_config('app.current_tenants', :scope, true)"))
        .setParameter("scope", scope)
        .getSingleResult();
  }

  /**
   * Keeps these two statements from auto-flushing the persistence context. Hibernate flushes before
   * a native query because it cannot tell which tables it reads, and a fixture must not change when
   * the work around it is written to the database.
   */
  private static Query noFlush(Query query) {
    return query.setFlushMode(FlushModeType.COMMIT);
  }
}
