package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Updates RLS policies to use {@code current_setting('app.current_tenant', true)} instead of {@code
 * current_setting('app.current_tenant')}. The second parameter {@code true} makes the function
 * return {@code NULL} instead of throwing an error if the session variable hasn't been set yet on
 * the connection. This prevents hard query failures during startup or if a connection is used
 * before {@link io.openaev.config.TenantAwareDataSourceConfig} has set the variable.
 */
@Component
public class V5_08__Fix_rls_policy_current_setting_fallback extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      for (String table : TenantScopedTables.TABLES) {
        String policyName = "tenant_isolation_" + table;

        statement.addBatch("DROP POLICY IF EXISTS " + policyName + " ON " + table);
        statement.addBatch(
            "CREATE POLICY "
                + policyName
                + " ON "
                + table
                + " USING (tenant_id = current_setting('app.current_tenant', true))");
      }
      statement.executeBatch();
    }
  }
}
