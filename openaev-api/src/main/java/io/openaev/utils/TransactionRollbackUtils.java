package io.openaev.utils;

import jakarta.persistence.EntityTransaction;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Tells whether the transaction bound to the current thread is already marked rollback-only.
 *
 * <p>A failure thrown out of another bean's {@code @Transactional} method marks the shared
 * transaction rollback-only, even when the caller catches it: whatever the caller writes after that
 * is rolled back at commit, and the commit throws {@code UnexpectedRollbackException}. A catch that
 * wants to recover checks this first (#8115).
 *
 * <p>Unlike {@code TransactionAspectSupport.currentTransactionStatus()}, it works whether the
 * transaction was opened by {@code @Transactional} or programmatically ({@code
 * TenantScopedTransaction}, {@code TransactionTemplate}), and answers false outside a transaction.
 */
public final class TransactionRollbackUtils {

  private TransactionRollbackUtils() {}

  /**
   * @return whether the current thread's JPA transaction is marked rollback-only; false when no
   *     transaction is active
   */
  public static boolean isCurrentTransactionRollbackOnly() {
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
      return false;
    }
    return TransactionSynchronizationManager.getResourceMap().values().stream()
        .filter(EntityManagerHolder.class::isInstance)
        .map(EntityManagerHolder.class::cast)
        .anyMatch(TransactionRollbackUtils::isRollbackOnly);
  }

  private static boolean isRollbackOnly(EntityManagerHolder holder) {
    if (holder.isRollbackOnly()) {
      return true;
    }
    EntityTransaction transaction = holder.getEntityManager().getTransaction();
    return transaction.isActive() && transaction.getRollbackOnly();
  }
}
