package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Remodels the asset taxonomy so a single concrete {@code Asset} is the target concept driven by
 * {@code asset_category}, with {@code Endpoint} the only agent-capable subtype and {@code
 * SecurityPlatform} a separate detection-source concept.
 *
 * <p>Two changes, both idempotent and metadata-only (no table rewrite):
 *
 * <ol>
 *   <li>Rename the target-relevant network columns {@code endpoint_* -> asset_*} - they now live on
 *       the {@code Asset} base since they are relevant to more than agent hosts (web / cloud /
 *       network categories).
 *   <li>Re-discriminate rows: former {@code AiTarget} rows and non-host {@code Endpoint} rows
 *       become the base {@code Asset} type ({@code asset_type = 'Asset'}); {@code SecurityPlatform}
 *       rows are left untouched. The category column continues to carry the product taxonomy.
 * </ol>
 */
@Component
public class V6_20260715130000000__Remodel_asset_taxonomy extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      // -- 1. Rename endpoint_* network columns to asset_* (idempotent) --
      renameColumn(statement, "endpoint_hostname", "asset_hostname");
      renameColumn(statement, "endpoint_ips", "asset_ips");
      renameColumn(statement, "endpoint_seen_ip", "asset_seen_ip");
      renameColumn(statement, "endpoint_url", "asset_url");
      renameColumn(statement, "endpoint_mac_addresses", "asset_mac_addresses");

      // -- 2. Re-discriminate to the concrete Asset base --
      // Former AI targets: AI is now a category, not an entity type.
      statement.execute("UPDATE assets SET asset_type = 'Asset' WHERE asset_type = 'AiTarget';");
      // Non-host endpoints become generic assets; agent-capable host categories stay Endpoint.
      statement.execute(
          "UPDATE assets SET asset_type = 'Asset' "
              + "WHERE asset_type = 'Endpoint' AND asset_category IN ("
              + "'CLOUD_RESOURCE','WEB_APPLICATION','NETWORK_DEVICE','IOT_OT_DEVICE',"
              + "'IDENTITY','SAAS_APPLICATION','GENERIC_ASSET');");

      // -- 3. Backfill the category on legacy endpoints that predate the asset_category column --
      // These rows carry a NULL category, so the inventory shows an empty "Category" cell. Endpoint
      // is now agent-capable-host only and the entity's @PrePersist default is HOST, but that only
      // fires on write; mirror it here for rows that never went through a write since the column was
      // introduced (idempotent - only touches the remaining NULLs).
      statement.execute(
          "UPDATE assets SET asset_category = 'HOST' "
              + "WHERE asset_type = 'Endpoint' AND asset_category IS NULL;");
    }
  }

  private void renameColumn(Statement statement, String from, String to) throws Exception {
    statement.execute(
        "DO $$ BEGIN "
            + "IF EXISTS (SELECT 1 FROM information_schema.columns "
            + "WHERE table_name = 'assets' AND column_name = '"
            + from
            + "') "
            + "AND NOT EXISTS (SELECT 1 FROM information_schema.columns "
            + "WHERE table_name = 'assets' AND column_name = '"
            + to
            + "') THEN "
            + "ALTER TABLE assets RENAME COLUMN "
            + from
            + " TO "
            + to
            + "; "
            + "END IF; END $$;");
  }
}
