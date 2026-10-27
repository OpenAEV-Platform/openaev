package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/** Content catalogue synced from the repositories (F16), and catalogue identity of scenarios. */
@Component
public class V6_20261014100000000__Catalog_items extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          CREATE TABLE IF NOT EXISTS catalog_items (
            catalog_item_id VARCHAR(255) NOT NULL PRIMARY KEY,
            catalog_item_type VARCHAR(255) NOT NULL,
            catalog_item_external_id VARCHAR(255) NOT NULL,
            catalog_item_version VARCHAR(255) NOT NULL,
            catalog_item_status VARCHAR(255),
            catalog_item_name TEXT,
            catalog_item_description TEXT,
            catalog_item_author VARCHAR(255),
            catalog_item_severity VARCHAR(255),
            catalog_item_category VARCHAR(255),
            catalog_item_main_focus VARCHAR(255),
            catalog_item_scenario_type VARCHAR(255),
            catalog_item_platforms TEXT[],
            catalog_item_injects_count INTEGER,
            catalog_item_changelog TEXT,
            catalog_item_payload_refs TEXT,
            catalog_item_archive_name VARCHAR(255),
            catalog_item_archive_hash VARCHAR(64),
            catalog_item_repository VARCHAR(255) NOT NULL,
            catalog_item_release VARCHAR(255),
            catalog_item_published_at TIMESTAMP WITH TIME ZONE,
            catalog_item_created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
            catalog_item_updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
            tenant_id VARCHAR(255) NOT NULL REFERENCES tenants (tenant_id) ON DELETE CASCADE,
            CONSTRAINT catalog_items_item_version_tenant_unique
              UNIQUE (catalog_item_type, catalog_item_repository, catalog_item_external_id,
                catalog_item_version, tenant_id)
          );
          """);
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_catalog_items_tenant_id ON catalog_items (tenant_id);");
      statement.execute(
          """
          CREATE TABLE IF NOT EXISTS catalog_sync_state (
            catalog_sync_state_id VARCHAR(255) NOT NULL PRIMARY KEY,
            catalog_sync_state_repository VARCHAR(255) NOT NULL,
            catalog_sync_state_channel VARCHAR(255) NOT NULL,
            catalog_sync_state_release VARCHAR(255),
            catalog_sync_state_created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
            catalog_sync_state_updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
            tenant_id VARCHAR(255) NOT NULL REFERENCES tenants (tenant_id) ON DELETE CASCADE,
            CONSTRAINT catalog_sync_state_repository_channel_tenant_unique
              UNIQUE (catalog_sync_state_repository, catalog_sync_state_channel, tenant_id)
          );
          """);
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_catalog_sync_state_tenant_id ON catalog_sync_state (tenant_id);");
      statement.execute(
          """
          ALTER TABLE scenarios
            ADD COLUMN IF NOT EXISTS scenario_catalog_id VARCHAR(255),
            ADD COLUMN IF NOT EXISTS scenario_version VARCHAR(255);
          """);
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_scenarios_catalog_id ON scenarios (scenario_catalog_id);");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_injects_catalog_id ON injects (inject_catalog_id);");
    }
  }
}
