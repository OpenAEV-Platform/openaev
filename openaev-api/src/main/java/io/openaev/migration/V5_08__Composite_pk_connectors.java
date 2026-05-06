package io.openaev.migration;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Migrates {@code collectors}, {@code injectors} and {@code executors} to composite primary keys
 * {@code (id, tenant_id)} so that the same static connector ID can exist in multiple tenants.
 *
 * <p>FK changes:
 *
 * <ul>
 *   <li>{@code injectors_injector_contracts.injector_id} → composite FK {@code (injector_id,
 *       tenant_id)}
 *   <li>{@code injects.inject_injector} → composite FK {@code (inject_injector, tenant_id)}
 *   <li>{@code agents.agent_executor} → composite FK {@code (agent_executor, tenant_id)}
 *   <li>{@code collectors} — no remaining FK references
 * </ul>
 */
@Component
public class V5_08__Composite_pk_connectors extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    try (Statement stmt = connection.createStatement()) {

      // =====================================================================
      // INJECTORS
      // =====================================================================

      // 1a. Drop FK from injectors_injector_contracts → injectors
      dropForeignKeysReferencing(connection, "injectors_injector_contracts", "injectors");

      // 1b. Drop FK from injects → injectors
      dropForeignKeysReferencing(connection, "injects", "injectors");

      // 1c. Drop the PK on injectors (name-independent)
      dropPrimaryKey(connection, "injectors");

      // 1d. Add composite PK
      stmt.execute("ALTER TABLE injectors ADD PRIMARY KEY (injector_id, tenant_id)");

      // 1e. Re-create FK from injectors_injector_contracts → injectors (composite)
      stmt.execute(
          """
          ALTER TABLE injectors_injector_contracts
              ADD CONSTRAINT injectors_injector_contracts_injector_fk
                  FOREIGN KEY (injector_id, tenant_id)
                  REFERENCES injectors(injector_id, tenant_id)
                  ON DELETE CASCADE
          """);

      // 1f. Re-create FK from injects → injectors (composite)
      stmt.execute(
          """
          ALTER TABLE injects
              ADD CONSTRAINT fk_inject_injector
                  FOREIGN KEY (inject_injector, tenant_id)
                  REFERENCES injectors(injector_id, tenant_id)
                  ON DELETE CASCADE
          """);

      // =====================================================================
      // EXECUTORS
      // =====================================================================

      // 2a. Drop FK from agents → executors
      dropForeignKeysReferencing(connection, "agents", "executors");

      // 2b. Drop the PK on executors
      dropPrimaryKey(connection, "executors");

      // 2c. Add composite PK
      stmt.execute("ALTER TABLE executors ADD PRIMARY KEY (executor_id, tenant_id)");

      // 2d. Re-create FK from agents → executors (composite)
      stmt.execute(
          """
          ALTER TABLE agents
              ADD CONSTRAINT agent_executor_id_fk
                  FOREIGN KEY (agent_executor, tenant_id)
                  REFERENCES executors(executor_id, tenant_id)
                  ON DELETE CASCADE
          """);

      // =====================================================================
      // COLLECTORS
      // =====================================================================
      // No remaining FKs reference collectors(collector_id).

      // 3a. Drop the PK on collectors
      dropPrimaryKey(connection, "collectors");

      // 3b. Add composite PK
      stmt.execute("ALTER TABLE collectors ADD PRIMARY KEY (collector_id, tenant_id)");
    }
  }

  /** Drops the primary key constraint of a table, regardless of the constraint name. */
  private void dropPrimaryKey(Connection connection, String table) throws Exception {
    String constraintName;
    try (Statement queryStmt = connection.createStatement();
        ResultSet rs =
            queryStmt.executeQuery(
                """
                SELECT con.conname
                FROM pg_constraint con
                JOIN pg_class rel ON rel.oid = con.conrelid
                WHERE rel.relname = '%s' AND con.contype = 'p'
                """
                    .formatted(table))) {
      if (!rs.next()) {
        throw new IllegalStateException("No primary key found on table " + table);
      }
      constraintName = rs.getString(1);
    }
    try (Statement ddlStmt = connection.createStatement()) {
      ddlStmt.execute("ALTER TABLE %s DROP CONSTRAINT %s".formatted(table, constraintName));
    }
  }

  /**
   * Drops all foreign key constraints from {@code sourceTable} that reference {@code targetTable}.
   */
  private void dropForeignKeysReferencing(
      Connection connection, String sourceTable, String targetTable) throws Exception {
    List<String> fkNames = new ArrayList<>();
    try (Statement queryStmt = connection.createStatement();
        ResultSet rs =
            queryStmt.executeQuery(
                """
                SELECT con.conname
                FROM pg_constraint con
                JOIN pg_class src ON src.oid = con.conrelid
                JOIN pg_class tgt ON tgt.oid = con.confrelid
                WHERE src.relname = '%s' AND tgt.relname = '%s' AND con.contype = 'f'
                """
                    .formatted(sourceTable, targetTable))) {
      while (rs.next()) {
        fkNames.add(rs.getString(1));
      }
    }
    try (Statement ddlStmt = connection.createStatement()) {
      for (String fkName : fkNames) {
        ddlStmt.execute("ALTER TABLE %s DROP CONSTRAINT %s".formatted(sourceTable, fkName));
      }
    }
  }
}
