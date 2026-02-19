package io.openaev.migration;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;

import java.sql.Statement;
import java.util.List;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_73__Create_default_tenant extends BaseJavaMigration {

  // Strings to replace in the SQL statement
  private static String DEFAULT_TENANT_ID = "[DEFAULT_TENANT_ID]";
  private static String TABLE_FK_TENANT = "[TABLE_FK_TENANT]";
  private static List<String> TABLES =
      List.of(
          "agents",
          "asset_agent_jobs",
          "asset_groups",
          "assets",
          "attack_patterns",
          "challenges",
          "channels",
          "collectors",
          "connector_instances",
          "custom_dashboards",
          "datapacks",
          "documents",
          "executors",
          "exercises",
          "findings",
          "groups",
          "import_mappers",
          "injectors",
          "injectors_contracts",
          "injects",
          "kill_chain_phases",
          "lessons_templates",
          "organizations",
          "payloads",
          "roles",
          "scenarios",
          "tag_rules",
          "tags",
          "teams",
          "vulnerabilities");

  @Override
  public void migrate(Context context) throws Exception {

    // SQL statements to execute with Strings to replace
    String addDefaultTenant =
        """
          DO $$
          BEGIN
            IF NOT EXISTS (SELECT 1 FROM tenants) THEN
              INSERT INTO tenants(tenant_id, tenant_name, tenant_description) VALUES ('[DEFAULT_TENANT_ID]', 'First default tenant auto created to rename', 'First default tenant auto created to rename');
            END IF;
          END $$;
      """;
    String addForeignKeyTenant =
        """
                      ALTER TABLE [TABLE_FK_TENANT]
                         ADD COLUMN tenant_id VARCHAR(255) NOT NULL DEFAULT '[DEFAULT_TENANT_ID]',
                         ADD CONSTRAINT fk_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id) ON DELETE CASCADE;
                      """;
    String addIndexTenant =
        "CREATE INDEX IF NOT EXISTS idx_tenant_id ON [TABLE_FK_TENANT](tenant_id);";

    try (Statement statement = context.getConnection().createStatement()) {
      // Add default tenant
      statement.execute(addDefaultTenant.replace(DEFAULT_TENANT_ID, DEFAULT_TENANT_UUID));
      // Add deleted_at in tenants for soft delete
      statement.execute("ALTER TABLE tenants ADD tenant_deleted_at TIMESTAMP WITH TIME ZONE;");
      // Add foreign keys with index, auto set default tenant id with default value
      for (String table : TABLES) {
        statement.addBatch(
            addForeignKeyTenant
                .replace(DEFAULT_TENANT_ID, DEFAULT_TENANT_UUID)
                .replace(TABLE_FK_TENANT, table));
        statement.addBatch(addIndexTenant.replace(TABLE_FK_TENANT, table));
      }
      statement.executeBatch();
      // Add linked table for users and tenants
      statement.execute(
          """
                    CREATE TABLE IF NOT EXISTS users_tenants (
                        user_id VARCHAR(255) NOT NULL,
                        tenant_id VARCHAR(255) NOT NULL,
                        PRIMARY KEY (user_id, tenant_id),
                        CONSTRAINT fk_user_id FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
                        CONSTRAINT fk_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id) ON DELETE CASCADE
                    );
                """);
      statement.execute("CREATE INDEX IF NOT EXISTS idx_user_id ON users_tenants(user_id);");
      statement.execute("CREATE INDEX IF NOT EXISTS idx_tenant_id ON users_tenants(tenant_id);");
    }
  }
}
