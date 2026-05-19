package io.openaev.migration;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_93__Create_tenant_settings_table extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement stmt = context.getConnection().createStatement()) {

      // -- Create tenant_settings table --
      stmt.execute(
          """
          CREATE TABLE IF NOT EXISTS tenant_settings (
            tenant_setting_id VARCHAR(255) NOT NULL PRIMARY KEY,
            tenant_setting_key VARCHAR(255) NOT NULL,
            tenant_setting_value TEXT,
            tenant_id VARCHAR(255) NOT NULL
              REFERENCES tenants(tenant_id) ON DELETE CASCADE,
            UNIQUE (tenant_setting_key, tenant_id)
          );
          """);

      stmt.execute(
          "CREATE INDEX IF NOT EXISTS idx_tenant_settings_tenant_id ON tenant_settings(tenant_id);");

      // -- Migrate default dashboard settings to tenant_settings for the default tenant --
      stmt.execute(
          """
          INSERT INTO tenant_settings (tenant_setting_id, tenant_setting_key, tenant_setting_value, tenant_id)
          SELECT gen_random_uuid()::varchar,
                 p.parameter_key,
                 p.parameter_value,
                 '%s'
          FROM   parameters p
          WHERE  p.parameter_key IN (
                     'platform_home_dashboard',
                     'platform_scenario_dashboard',
                     'platform_simulation_dashboard'
                 )
          AND    p.parameter_value IS NOT NULL
          AND    p.parameter_value <> ''
          ON CONFLICT (tenant_setting_key, tenant_id) DO NOTHING;
          """
              .formatted(DEFAULT_TENANT_UUID));
    }
  }
}
