package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Software inventory per asset: one row per component an asset runs (vendor, product, version, CPE,
 * PURL), with where it was learned (agent, SBOM, API), when it was first and last seen, and the
 * component that bundles it. Like {@code assets_links} it has no tenant column: every read and
 * write goes through its asset, whose reads the tenant and marking inspectors scope, and deleting
 * the asset deletes its software. A component is stored once per asset, on its identity key (the
 * PURL, else the CPE, else vendor, product and version).
 */
@Component
public class V6_20261121160000000__Add_asset_software extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          CREATE TABLE IF NOT EXISTS asset_software (
            asset_software_id VARCHAR(255) NOT NULL PRIMARY KEY,
            asset_id VARCHAR(255) NOT NULL REFERENCES assets (asset_id) ON DELETE CASCADE,
            asset_software_parent_id VARCHAR(255)
              REFERENCES asset_software (asset_software_id) ON DELETE SET NULL,
            asset_software_key VARCHAR(2048) NOT NULL,
            asset_software_vendor VARCHAR(512),
            asset_software_product VARCHAR(512) NOT NULL,
            asset_software_version VARCHAR(255),
            asset_software_version_state VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
            asset_software_cpe VARCHAR(1024),
            asset_software_purl VARCHAR(2048),
            asset_software_source VARCHAR(32) NOT NULL,
            asset_software_first_seen TIMESTAMPTZ NOT NULL DEFAULT now(),
            asset_software_last_seen TIMESTAMPTZ NOT NULL DEFAULT now(),
            CONSTRAINT asset_software_asset_key_unique UNIQUE (asset_id, asset_software_key)
          );
          """);
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_asset_software_purl ON asset_software"
              + " (asset_software_purl) WHERE asset_software_purl IS NOT NULL;");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_asset_software_cpe ON asset_software"
              + " (asset_software_cpe) WHERE asset_software_cpe IS NOT NULL;");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_asset_software_product ON asset_software"
              + " (lower(asset_software_product));");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_asset_software_parent ON asset_software"
              + " (asset_software_parent_id) WHERE asset_software_parent_id IS NOT NULL;");
    }
  }
}
