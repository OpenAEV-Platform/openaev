package io.openaev.utilstest;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Writes fixture rows inside the scope of the tenant that owns them, the way a production write
 * runs inside its request's or job's scope.
 *
 * <p>Once a table is tenant-active, the statement inspector scopes every UPDATE and DELETE on it to
 * the transaction's tenants. A fixture that inserts a row and then updates it (a second save, a
 * collection added after the first save) therefore needs the row's tenant in scope whenever that
 * UPDATE is flushed. Outside a transaction, the write gets a transaction scoped to the tenant.
 * Inside the test's transaction, the tenant is added to that transaction's scope and stays there,
 * so a later flush from the test still reaches the row; {@link #flushPendingWrites} flushes before
 * a request clears the scope, as the fixture would be committed before a request reads it.
 */
@Component
@RequiredArgsConstructor
public class RowTenantScope {

  private static final String CURRENT_SCOPE_SETTING = "app.current_tenants";
  private static final Object WROTE_IN_TRANSACTION = RowTenantScope.class.getName();

  private final EntityManager entityManager;
  private final TenantScopedTransaction tenantTx;

  /**
   * Runs {@code work} so its writes for {@code tenantId} land in that tenant's scope: in a
   * transaction scoped to the tenant when none is open, otherwise in the open transaction with the
   * tenant added to its scope for the rest of that transaction.
   */
  public void write(String tenantId, Runnable work) {
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
      tenantTx.execute(TxCtx.forTenant(tenantId), work);
      return;
    }
    Object[] found =
        (Object[])
            entityManager
                .createNativeQuery(
                    "SELECT coalesce(current_setting('"
                        + CURRENT_SCOPE_SETTING
                        + "', true), ''), coalesce(current_setting('"
                        + DefaultTenantScopeTestListener.AMBIENT_SCOPE_SETTING
                        + "', true), '')")
                .setFlushMode(FlushModeType.COMMIT)
                .getSingleResult();
    // The same tenant goes into the current and the ambient setting, so the comparison
    // AmbientTenantScopeTestAspect makes between the two keeps its meaning.
    entityManager
        .createNativeQuery(
            "SELECT set_config('"
                + CURRENT_SCOPE_SETTING
                + "', :current, true), set_config('"
                + DefaultTenantScopeTestListener.AMBIENT_SCOPE_SETTING
                + "', :ambient, true)")
        .setFlushMode(FlushModeType.COMMIT)
        .setParameter("current", withTenant((String) found[0], tenantId))
        .setParameter("ambient", withTenant((String) found[1], tenantId))
        .getResultList();
    markWritten();
    work.run();
  }

  /**
   * Flushes the fixture writes still pending in the current transaction, in the scope {@link
   * #write} left on it. Called before a request clears the scope; does nothing in a transaction
   * {@link #write} never touched.
   */
  public static void flushPendingWrites(EntityManager entityManager) {
    if (!TransactionSynchronizationManager.hasResource(WROTE_IN_TRANSACTION)) {
      return;
    }
    entityManager.flush();
    // A scenario's first flush leaves one more UPDATE behind, which only moves its update
    // timestamp. Flushing again runs it in this scope too, instead of in the request's.
    entityManager.flush();
  }

  private static void markWritten() {
    if (TransactionSynchronizationManager.hasResource(WROTE_IN_TRANSACTION)) {
      return;
    }
    TransactionSynchronizationManager.bindResource(WROTE_IN_TRANSACTION, Boolean.TRUE);
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            TransactionSynchronizationManager.unbindResourceIfPossible(WROTE_IN_TRANSACTION);
          }
        });
  }

  private static String withTenant(String scope, String tenantId) {
    Set<String> tenants = new LinkedHashSet<>();
    for (String tenant : scope.split(",")) {
      if (!tenant.isBlank()) {
        tenants.add(tenant.trim());
      }
    }
    tenants.add(tenantId);
    return String.join(",", tenants);
  }
}
