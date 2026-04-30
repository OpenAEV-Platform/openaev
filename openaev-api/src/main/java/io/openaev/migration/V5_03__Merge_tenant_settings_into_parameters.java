package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V5_03__Merge_tenant_settings_into_parameters extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement stmt = context.getConnection().createStatement()) {
      // 1. Add nullable tenant_id FK to parameters table
      stmt.execute(
          """
          ALTER TABLE parameters
          ADD COLUMN IF NOT EXISTS tenant_id varchar(255)
          REFERENCES tenants(tenant_id) ON DELETE CASCADE;
          """);

      // 2. Add index on tenant_id
      stmt.execute(
          """
          CREATE INDEX IF NOT EXISTS idx_parameters_tenant_id
          ON parameters(tenant_id);
          """);

      // 3. Migrate tenant_settings data into parameters
      stmt.execute(
          """
          INSERT INTO parameters (parameter_id, parameter_key, parameter_value, tenant_id)
          SELECT tenant_setting_id, tenant_setting_key, tenant_setting_value, tenant_id
          FROM tenant_settings;
          """);

      // 4. Drop the old tenant_settings table
      stmt.execute("DROP TABLE IF EXISTS tenant_settings;");
    }
  }
}
