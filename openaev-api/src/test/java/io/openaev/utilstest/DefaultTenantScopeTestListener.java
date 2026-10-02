package io.openaev.utilstest;

import io.openaev.database.model.Tenant;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Runs the test-managed transaction inside the default tenant's scope, the way a production write
 * always runs inside a request or a {@code TenantScopedTransaction}. Without it the test's own
 * setup writes and flushes run unscoped: the statement inspector then scopes their UPDATEs to no
 * row (Hibernate 7 also bumps {@code @UpdateTimestamp} owners when only a collection changed, so
 * this happens on plain fixture setup).
 *
 * <p>The database-side counterpart of {@link DefaultTenantExtension}. Each MockMvc request starts
 * from a cleared scope (see {@link RequestTenantScopeTestConfiguration}), so a request still gets
 * exactly the scope it asks for.
 */
public class DefaultTenantScopeTestListener extends AbstractTestExecutionListener {

  /**
   * After TransactionalTestExecutionListener (4000) so the test transaction is open, before
   * WithMockUserTestExecutionListener (5000) and {@code @BeforeEach} so their writes are scoped.
   */
  @Override
  public int getOrder() {
    return 4500;
  }

  @Override
  public void beforeTestMethod(TestContext testContext) {
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
      return; // set_config(..., true) is transaction-local: nothing to scope without a transaction
    }
    testContext
        .getApplicationContext()
        .getBean(EntityManager.class)
        .createNativeQuery("SELECT set_config('app.current_tenants', :scope, true)")
        .setFlushMode(FlushModeType.COMMIT)
        .setParameter("scope", Tenant.DEFAULT_TENANT_UUID)
        .getSingleResult();
  }
}
