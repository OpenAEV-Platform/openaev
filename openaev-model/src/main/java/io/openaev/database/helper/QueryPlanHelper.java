package io.openaev.database.helper;

import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Makes PostgreSQL plan each query of the current transaction for its actual parameter values.
 *
 * <p>After a few executions of the same statement, the JDBC driver prepares it server-side and
 * PostgreSQL may then switch to a generic plan, built without the parameter values. A keyset query
 * resuming from a cursor is the worst case: the generic plan cannot know that the rows past the
 * cursor are a thin slice of the table, and plans as if they were most of it.
 */
@Component
@RequiredArgsConstructor
public class QueryPlanHelper {

  private final EntityManager entityManager;

  /**
   * Forces a custom plan until the end of the current transaction. Refuses to run outside one: the
   * setting is transaction-local, so it would be dropped before the query it is meant for.
   */
  public void forceCustomPlan() {
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException(
          "forceCustomPlan() needs an active transaction: the setting is transaction-local and"
              + " would be dropped before the query it is meant for.");
    }
    entityManager
        .createNativeQuery("SELECT set_config('plan_cache_mode', 'force_custom_plan', true)")
        .setFlushMode(FlushModeType.COMMIT)
        .getSingleResult();
  }
}
