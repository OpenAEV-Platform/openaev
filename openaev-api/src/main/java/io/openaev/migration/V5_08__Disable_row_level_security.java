package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Reverts V5_06: disables PostgreSQL Row-Level Security (RLS) on all tenant-scoped tables and
 * removes the associated policies and role grants.
 *
 * <p>Tenant isolation is now enforced exclusively at the application level via Hibernate
 * {@code @Filter("tenantFilter")} and explicit {@code WHERE tenant_id} clauses in native queries.
 */
@Component
public class V5_08__Disable_row_level_security extends BaseJavaMigration {

  static final String APP_ROLE = "openaev_app";

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {

      // -- 1. Disable RLS and drop policies on all tenant-scoped tables
      for (String table : TenantScopedTables.TABLES) {
        String policyName = "tenant_isolation_" + table;

        statement.addBatch("DROP POLICY IF EXISTS " + policyName + " ON " + table);
        statement.addBatch("ALTER TABLE " + table + " DISABLE ROW LEVEL SECURITY");
        statement.addBatch("ALTER TABLE " + table + " NO FORCE ROW LEVEL SECURITY");
      }

      statement.executeBatch();

      // -- 2. Revoke privileges and remove default privilege grants
      statement.execute(
          "ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON TABLES FROM " + APP_ROLE);
      statement.execute(
          "ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON SEQUENCES FROM " + APP_ROLE);
      statement.execute(
          "REVOKE ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public FROM " + APP_ROLE);
      statement.execute("REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA public FROM " + APP_ROLE);
      statement.execute("REVOKE USAGE ON SCHEMA public FROM " + APP_ROLE);
    }
  }
}

