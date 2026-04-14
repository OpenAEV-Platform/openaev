package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_92__Create_tenant_settings_table extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement stmt = connection.createStatement();

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
  }
}
