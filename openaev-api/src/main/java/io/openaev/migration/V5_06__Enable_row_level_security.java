package io.openaev.migration;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;

import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Enables PostgreSQL Row-Level Security (RLS) on all tenant-scoped tables.
 *
 * <p>RLS acts as a database-level safety net: even if a native SQL query forgets to include {@code
 * WHERE tenant_id = ...}, the database will filter rows automatically based on the session variable
 * {@code app.current_tenant} set by {@link io.openaev.config.TenantAwareDataSourceConfig} on each
 * connection checkout.
 *
 * <p>The policy is strict: {@code tenant_id = current_setting('app.current_tenant')}. There is no
 * bypass — platform-level requests default to the default tenant UUID, and tenant creation
 * explicitly switches the connection's tenant via {@code SET app.current_tenant}.
 *
 * <p>Because superusers bypass RLS, this migration also creates a non-superuser role {@code
 * openaev_app} that the application adopts at runtime via {@code SET ROLE openaev_app} (configured
 * in {@code spring.datasource.hikari.connection-init-sql}). Flyway continues to run as the
 * superuser for DDL operations.
 */
@Component
public class V5_06__Enable_row_level_security extends BaseJavaMigration {

  /** Non-superuser role the application adopts at runtime so that RLS policies are enforced. */
  static final String APP_ROLE = "openaev_app";

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {

      String dbOwner = context.getConnection().getMetaData().getUserName();

      // -- 1. Set a database-level default for app.current_tenant so
      //       current_setting() never fails, even outside a transaction.
      //       Defaults to the default tenant — platform-level requests see only default tenant
      // data.
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

      // PG > 15, allow access for app role for read
      statement.execute("GRANT USAGE ON SCHEMA public TO " + APP_ROLE);

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

      // -- 3. Enable RLS on all tenant-scoped tables with strict tenant isolation.
      //       No bypass — every query must match the current tenant.
      for (String table : TenantScopedTables.TABLES) {
        String policyName = "tenant_isolation_" + table;

        statement.addBatch("ALTER TABLE " + table + " ENABLE ROW LEVEL SECURITY");
        statement.addBatch("ALTER TABLE " + table + " FORCE ROW LEVEL SECURITY");
        statement.addBatch("DROP POLICY IF EXISTS " + policyName + " ON " + table);
        statement.addBatch(
            "CREATE POLICY "
                + policyName
                + " ON "
                + table
                + " USING (tenant_id = current_setting('app.current_tenant'))");
      }

      statement.executeBatch();
    }
  }
}
