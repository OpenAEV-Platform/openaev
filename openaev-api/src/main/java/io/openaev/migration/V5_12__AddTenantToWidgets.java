package io.openaev.migration;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V5_12__AddTenantToWidgets extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          ALTER TABLE widgets
            ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(255);
          """);

      statement.execute(
          """
          UPDATE widgets w
          SET tenant_id = cd.tenant_id
          FROM custom_dashboards cd
          WHERE w.widget_custom_dashboard = cd.custom_dashboard_id
            AND (w.tenant_id IS NULL OR w.tenant_id <> cd.tenant_id);
          """);

      statement.execute(
          String.format(
              """
              UPDATE widgets
              SET tenant_id = '%s'
              WHERE tenant_id IS NULL;
              """,
              DEFAULT_TENANT_UUID));

      statement.execute(
          """
          ALTER TABLE widgets
            ALTER COLUMN tenant_id SET NOT NULL;
          """);

      statement.execute(
          """
          DO $$
          BEGIN
            IF NOT EXISTS (
              SELECT 1
              FROM information_schema.table_constraints
              WHERE constraint_name = 'fk_widgets_tenant_id'
            ) THEN
              ALTER TABLE widgets
                ADD CONSTRAINT fk_widgets_tenant_id
                FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id) ON DELETE CASCADE;
            END IF;
          END $$;
          """);

      statement.execute(
          """
          CREATE INDEX IF NOT EXISTS idx_widgets_tenant_id
            ON widgets(tenant_id);
          """);
    }
  }
}
