package io.openaev.migration;

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
 * <p>Prerequisites (created by init-db.sql or CI bootstrap):
 *
 * <pre>
 * CREATE ROLE openaev_app NOLOGIN NOSUPERUSER;
 * GRANT openaev_app TO openaev;
 * ALTER DATABASE openaev SET app.current_tenant = '2cffad3a-0001-4078-b0e2-ef74274022c3';
 * </pre>
 *
 * <p>The migration runs as the database superuser (openaev). At runtime, {@link
 * io.openaev.config.TenantAwareDataSourceConfig} does {@code SET ROLE openaev_app} so that RLS
 * policies are enforced.
 */
@Component
public class V5_06__Enable_row_level_security extends BaseJavaMigration {

  private static final String DEFAULT_APP_ROLE = "openaev_app";

  @Override
  public void migrate(Context context) throws Exception {
    // Resolve role name from system property or env var (Spring @Value not available in Flyway)
    String appRole =
        System.getProperty(
            "openaev.rls.app-role",
            System.getenv().getOrDefault("OPENAEV_RLS_APP_ROLE", DEFAULT_APP_ROLE));

    try (Statement statement = context.getConnection().createStatement()) {

      // -- Prerequisites: the app role, GRANT, and app.current_tenant default
      //    must already exist (created by init-db.sql, CI bootstrap, or ops script).
      //    This migration only enables RLS — it does NOT create roles or alter database settings.

      // -- 1. Grant privileges to the app role on existing and future objects
      statement.execute("GRANT USAGE ON SCHEMA public TO " + appRole);
      statement.execute("GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO " + appRole);
      statement.execute("GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO " + appRole);
      statement.execute(
          "ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO " + appRole);
      statement.execute(
          "ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO " + appRole);

      // -- 2. Enable RLS on all tenant-scoped tables with strict tenant isolation.
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
