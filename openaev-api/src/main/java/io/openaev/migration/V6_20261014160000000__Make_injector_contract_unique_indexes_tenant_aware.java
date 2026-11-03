package io.openaev.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Makes the unique keys of {@code injectors_contracts} per tenant, like its primary key {@code
 * (injector_contract_id, tenant_id)}.
 *
 * <ul>
 *   <li>External id: the global unique constraint added by {@code V4_15} is replaced by a unique
 *       index on {@code (injector_contract_external_id, tenant_id)}, partial because the column is
 *       nullable.
 *   <li>Payload: the global unique constraint added by {@code V4_98} is dropped. {@code V4_90}
 *       already created the per-tenant index {@code (injector_contract_payload, tenant_id)}; it is
 *       checked here, and created when missing, before the global one goes.
 * </ul>
 *
 * <p>Never merges or deletes a row. If a tenant already holds a key twice, the migration fails with
 * the colliding values and their tenants, to be resolved by hand. A schema that still carries the
 * global constraints cannot hold such duplicates, so only a schema that lost them can fail here.
 */
@Component
public class V6_20261014160000000__Make_injector_contract_unique_indexes_tenant_aware
    extends BaseJavaMigration {

  public static final String EXTERNAL_ID_INDEX = "injector_contract_external_id_unique";
  public static final String PAYLOAD_INDEX = "injector_contract_payload_unique";
  public static final String GLOBAL_EXTERNAL_ID_CONSTRAINT =
      "injectors_contracts_injector_contract_external_id_key";
  public static final String GLOBAL_PAYLOAD_CONSTRAINT = "unique_injector_contract_payload";

  private static final String EXTERNAL_ID_COLUMN = "injector_contract_external_id";
  private static final String PAYLOAD_COLUMN = "injector_contract_payload";
  private static final int MAX_REPORTED_COLLISIONS = 50;

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    ensureTenantUniqueIndex(connection, EXTERNAL_ID_INDEX, EXTERNAL_ID_COLUMN);
    ensureTenantUniqueIndex(connection, PAYLOAD_INDEX, PAYLOAD_COLUMN);
    try (Statement statement = connection.createStatement()) {
      // Both were declared as constraints (column UNIQUE, ADD CONSTRAINT): dropping the constraint
      // drops its index.
      statement.execute(
          "ALTER TABLE injectors_contracts DROP CONSTRAINT IF EXISTS "
              + GLOBAL_EXTERNAL_ID_CONSTRAINT);
      statement.execute(
          "ALTER TABLE injectors_contracts DROP CONSTRAINT IF EXISTS " + GLOBAL_PAYLOAD_CONSTRAINT);
    }
  }

  /**
   * Creates the partial unique index {@code (column, tenant_id) WHERE column IS NOT NULL} unless it
   * already exists. An index of that name with any other definition stops the migration: dropping
   * the global constraint next to it would leave the column without per-tenant uniqueness.
   */
  private void ensureTenantUniqueIndex(Connection connection, String index, String column)
      throws SQLException {
    String existing = findIndexDefinition(connection, index);
    if (existing != null) {
      if (!isTenantUniqueIndex(existing, column)) {
        throw new FlywayException(
            "Index "
                + index
                + " has an unexpected definition: "
                + existing
                + ". Expected a unique index on ("
                + column
                + ", tenant_id) WHERE "
                + column
                + " IS NOT NULL. Fix it by hand before restarting.");
      }
      return;
    }
    failOnCollisions(connection, index, column);
    try (Statement statement = connection.createStatement()) {
      // Index and column names are constants of this class, never input.
      statement.execute(
          "CREATE UNIQUE INDEX "
              + index
              + " ON injectors_contracts ("
              + column
              + ", tenant_id) WHERE "
              + column
              + " IS NOT NULL");
    }
  }

  private static boolean isTenantUniqueIndex(String definition, String column) {
    return definition.startsWith("CREATE UNIQUE INDEX ")
        && definition.contains("(" + column + ", tenant_id)")
        && definition.endsWith("WHERE (" + column + " IS NOT NULL)");
  }

  private String findIndexDefinition(Connection connection, String index) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "SELECT pg_get_indexdef(i.indexrelid) FROM pg_index i"
                + " JOIN pg_class c ON c.oid = i.indexrelid"
                + " WHERE i.indrelid = 'injectors_contracts'::regclass AND c.relname = ?")) {
      statement.setString(1, index);
      try (ResultSet resultSet = statement.executeQuery()) {
        return resultSet.next() ? resultSet.getString(1) : null;
      }
    }
  }

  private void failOnCollisions(Connection connection, String index, String column)
      throws SQLException {
    List<String> collisions = new ArrayList<>();
    long total = 0;
    try (Statement statement = connection.createStatement();
        ResultSet resultSet =
            statement.executeQuery(
                "SELECT tenant_id, "
                    + column
                    + " AS key_value, count(*) AS row_count, count(*) OVER () AS total"
                    + " FROM injectors_contracts WHERE "
                    + column
                    + " IS NOT NULL GROUP BY tenant_id, "
                    + column
                    + " HAVING count(*) > 1 ORDER BY tenant_id, "
                    + column
                    + " LIMIT "
                    + MAX_REPORTED_COLLISIONS)) {
      while (resultSet.next()) {
        total = resultSet.getLong("total");
        collisions.add(
            "tenant "
                + resultSet.getString("tenant_id")
                + " holds "
                + column
                + " '"
                + resultSet.getString("key_value")
                + "' on "
                + resultSet.getLong("row_count")
                + " contracts");
      }
    }
    if (!collisions.isEmpty()) {
      throw new FlywayException(
          "Cannot create unique index "
              + index
              + ": "
              + total
              + " value(s) of "
              + column
              + " are held more than once within a tenant"
              + (total > collisions.size() ? " (first " + collisions.size() + " listed)" : "")
              + ": "
              + String.join("; ", collisions)
              + ". This migration does not merge or delete contracts; resolve them by hand"
              + " before restarting.");
    }
  }

  /* ROLLBACK
   DROP INDEX IF EXISTS injector_contract_external_id_unique;
   ALTER TABLE injectors_contracts
     ADD CONSTRAINT injectors_contracts_injector_contract_external_id_key
     UNIQUE (injector_contract_external_id);
   ALTER TABLE injectors_contracts
     ADD CONSTRAINT unique_injector_contract_payload UNIQUE (injector_contract_payload);
   -- Both ADD CONSTRAINT fail once two tenants hold the same value: a rollback is only possible
   -- before any tenant has made use of the per-tenant keys.
  */
}
