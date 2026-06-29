package io.openaev.config;

import io.openaev.utilstest.RabbitMQTestListener;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.transaction.annotation.Transactional;

/**
 * Base for end-to-end tenant-isolation tests. A subclass activates one table with
 * {@code @SpringBootTest(properties = "openaev.tenant.active-tables=<table>")} and uses these
 * helpers to seed tenants, set the transaction scope, and read or delete rows under it. Every such
 * test makes the same point: with the table active, the scope alone decides what a query sees and
 * what a write can touch.
 *
 * <p>This targets <b>data-layer</b> isolation (the inspector's SQL filtering, driven by the GUC
 * scope). It is deliberately separate from {@link io.openaev.utils.TenantIsolationTestHelper},
 * which tests isolation through the HTTP API and RBAC tenant membership.
 */
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@Transactional
abstract class TenantIsolationIntegrationTest {

  @Autowired protected EntityManager entityManager;

  /** Sets the transaction-local tenant scope; an empty string means "no scope" (fail-closed). */
  protected void setScope(String scope) {
    entityManager
        .createNativeQuery("SELECT set_config('app.current_tenants', :scope, true)")
        .setParameter("scope", scope)
        .getSingleResult();
  }

  protected void seedTenant(String tenantId) {
    entityManager
        .createNativeQuery("INSERT INTO tenants (tenant_id, tenant_name) VALUES (:id, :id)")
        .setParameter("id", tenantId)
        .executeUpdate();
  }

  /** Count of the given rows of {@code table} visible under the current scope. */
  protected int countVisible(String table, String idColumn, String... ids) {
    return ((Number) inQuery("SELECT count(*)", table, idColumn, ids).getSingleResult()).intValue();
  }

  /** The {@code idColumn} of the single such row visible under the current scope. */
  protected Object onlyVisible(String table, String idColumn, String... ids) {
    return inQuery("SELECT " + idColumn, table, idColumn, ids).getSingleResult();
  }

  /**
   * Deletes one row by id (subject to the scope) and returns how many rows were actually deleted.
   */
  protected int deleteRow(String table, String idColumn, String idValue) {
    return entityManager
        .createNativeQuery("DELETE FROM " + table + " WHERE " + idColumn + " = :id")
        .setParameter("id", idValue)
        .executeUpdate();
  }

  private Query inQuery(String select, String table, String idColumn, String... ids) {
    StringBuilder sql =
        new StringBuilder(select)
            .append(" FROM ")
            .append(table)
            .append(" WHERE ")
            .append(idColumn)
            .append(" IN (");
    for (int i = 0; i < ids.length; i++) {
      sql.append(i == 0 ? "" : ", ").append(":id").append(i);
    }
    sql.append(")");
    Query query = entityManager.createNativeQuery(sql.toString());
    for (int i = 0; i < ids.length; i++) {
      query.setParameter("id" + i, ids[i]);
    }
    return query;
  }
}
