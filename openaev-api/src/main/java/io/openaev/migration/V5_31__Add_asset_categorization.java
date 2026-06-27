package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Introduces the two-level asset taxonomy (category + subcategory) plus criticality,
 * internet-facing exposure and the cloud-resource breadth fields on the single-table {@code assets}
 * table. All columns are additive and nullable; {@code asset_metadata} is {@code jsonb} (not {@code
 * json}) so the {@code SELECT DISTINCT a FROM Asset a} queries keep an equality operator.
 *
 * <p>Existing rows are backfilled so nothing regresses: endpoints become HOST / CONTAINER_WORKLOAD
 * / GENERIC_ASSET based on their platform, security platforms become SECURITY_PLATFORM (subcategory
 * = platform type) and AI targets become AI_TARGET.
 */
@Component
public class V5_31__Add_asset_categorization extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    // Each statement is executed individually: the PostgreSQL JDBC driver does not reliably run
    // several statements from a single execute() call across all configurations.
    try (Statement statement = context.getConnection().createStatement()) {
      // -- Columns --
      statement.execute("ALTER TABLE assets ADD COLUMN IF NOT EXISTS asset_category varchar(255);");
      statement.execute(
          "ALTER TABLE assets ADD COLUMN IF NOT EXISTS asset_subcategory varchar(255);");
      statement.execute(
          "ALTER TABLE assets ADD COLUMN IF NOT EXISTS asset_criticality varchar(255);");
      statement.execute(
          "ALTER TABLE assets ADD COLUMN IF NOT EXISTS asset_internet_facing boolean;");
      statement.execute(
          "ALTER TABLE assets ADD COLUMN IF NOT EXISTS asset_cloud_provider varchar(255);");
      statement.execute(
          "ALTER TABLE assets ADD COLUMN IF NOT EXISTS asset_cloud_native_type varchar(255);");
      statement.execute(
          "ALTER TABLE assets ADD COLUMN IF NOT EXISTS asset_cloud_region varchar(255);");
      statement.execute("ALTER TABLE assets ADD COLUMN IF NOT EXISTS asset_metadata jsonb;");

      // -- Backfill: Endpoint --
      statement.execute(
          "UPDATE assets SET asset_category = CASE"
              + " WHEN endpoint_platform IN ('Linux', 'Windows', 'MacOS') THEN 'HOST'"
              + " WHEN endpoint_platform = 'Container' THEN 'CONTAINER_WORKLOAD'"
              + " ELSE 'GENERIC_ASSET' END"
              + " WHERE asset_type = 'Endpoint' AND asset_category IS NULL;");
      statement.execute(
          "UPDATE assets SET asset_subcategory = 'CONTAINER'"
              + " WHERE asset_type = 'Endpoint' AND endpoint_platform = 'Container'"
              + " AND asset_subcategory IS NULL;");

      // -- Backfill: SecurityPlatform --
      statement.execute(
          "UPDATE assets SET asset_category = 'SECURITY_PLATFORM'"
              + " WHERE asset_type = 'SecurityPlatform' AND asset_category IS NULL;");
      statement.execute(
          "UPDATE assets SET asset_subcategory = security_platform_type"
              + " WHERE asset_type = 'SecurityPlatform' AND asset_subcategory IS NULL"
              + " AND security_platform_type IS NOT NULL;");

      // -- Backfill: AiTarget --
      statement.execute(
          "UPDATE assets SET asset_category = 'AI_TARGET'"
              + " WHERE asset_type = 'AiTarget' AND asset_category IS NULL;");

      // -- Indexes --
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_assets_tenant_category"
              + " ON assets (tenant_id, asset_category);");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_assets_tenant_cloud_provider"
              + " ON assets (tenant_id, asset_cloud_provider);");
    }
  }
}
