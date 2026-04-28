package io.openaev.migration;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Enables PostgreSQL Row-Level Security (RLS) on all tenant-scoped tables.
 *
 * <p>RLS acts as a database-level safety net: even if a native SQL query forgets to include {@code
 * WHERE tenant_id = ...}, the database will filter rows automatically based on the session variable
 * {@code app.current_tenant} set by {@link io.openaev.aop.HibernateFilterTransactionAspect}.
 *
 * <p>Because superusers bypass RLS, this migration also creates a non-superuser role {@code
 * openaev_app} that the application adopts at runtime via {@code SET ROLE openaev_app} (configured
 * in {@code spring.datasource.hikari.connection-init-sql}). Flyway continues to run as the
 * superuser for DDL operations.
 */
@Component
public class V4_99__Enable_row_level_security extends BaseJavaMigration {

  /** Non-superuser role the application adopts at runtime so that RLS policies are enforced. */
  static final String APP_ROLE = "openaev_app";

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {

      String dbOwner = context.getConnection().getMetaData().getUserName();

      // -- 1. Set a database-level default for app.current_tenant so
      //       current_setting() never fails, even outside a transaction.
      String dbName = context.getConnection().getCatalog();
      statement.execute(
          "ALTER DATABASE \""
              + dbName
              + "\" SET app.current_tenant = '"
              + DEFAULT_TENANT_UUID
              + "'");

      // -- 2. Create a non-superuser role that the app will adopt via SET ROLE.
      //       Superusers bypass RLS, so the app must not run queries as a superuser.
      ResultSet rs =
          statement.executeQuery("SELECT 1 FROM pg_roles WHERE rolname = '" + APP_ROLE + "'");
      if (!rs.next()) {
        statement.execute("CREATE ROLE " + APP_ROLE + " NOLOGIN NOSUPERUSER");
      }
      rs.close();

      // Grant the app role to the DB owner so SET ROLE works
      statement.execute("GRANT " + APP_ROLE + " TO \"" + dbOwner + "\"");

      // Grant full DML privileges on all tables and sequences
      statement.execute("GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO " + APP_ROLE);
      statement.execute("GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO " + APP_ROLE);

      // Ensure future tables/sequences also get privileges
      statement.execute(
          "ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO " + APP_ROLE);
      statement.execute(
          "ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO " + APP_ROLE);

      // -- 3. Discover all tenant-scoped tables dynamically (those with a tenant_id column,
      //       excluding the tenants table itself), then enable RLS on each one.
      //       Results are fully materialized into the list before the ResultSet closes.
      List<String> tenantScopedTables = new ArrayList<>();
      try (Statement tableStmt = context.getConnection().createStatement();
          ResultSet tableRs =
              tableStmt.executeQuery(
                  "SELECT table_name FROM information_schema.columns"
                      + " WHERE table_schema = 'public' AND column_name = 'tenant_id'"
                      + " AND table_name != 'tenants' ORDER BY table_name")) {
        while (tableRs.next()) {
          tenantScopedTables.add(tableRs.getString("table_name"));
        }
      }

      for (String table : tenantScopedTables) {
        String quotedTable = "\"" + table + "\"";
        String policyName = "tenant_isolation_" + table;

        statement.addBatch("ALTER TABLE " + quotedTable + " ENABLE ROW LEVEL SECURITY");
        statement.addBatch("ALTER TABLE " + quotedTable + " FORCE ROW LEVEL SECURITY");
        statement.addBatch("DROP POLICY IF EXISTS " + policyName + " ON " + quotedTable);
        statement.addBatch(
            "CREATE POLICY "
                + policyName
                + " ON "
                + quotedTable
                + " USING (tenant_id = current_setting('app.current_tenant'))");
      }

      statement.executeBatch();
    }
  }
}
