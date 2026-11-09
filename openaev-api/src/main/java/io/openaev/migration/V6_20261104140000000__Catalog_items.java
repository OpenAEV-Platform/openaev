package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/** Releases received from the content repositories, and the catalogue items they bring. */
@Component
public class V6_20261104140000000__Catalog_items extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          CREATE TABLE IF NOT EXISTS catalog_releases (
            catalog_release_id VARCHAR(255) NOT NULL PRIMARY KEY,
            catalog_release_repository VARCHAR(255) NOT NULL,
            catalog_release_name VARCHAR(255) NOT NULL,
            catalog_release_created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
            catalog_release_updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
            tenant_id VARCHAR(255) NOT NULL REFERENCES tenants (tenant_id) ON DELETE CASCADE,
            CONSTRAINT catalog_releases_name_tenant_unique
              UNIQUE (catalog_release_repository, catalog_release_name, tenant_id)
          );
          """);
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_catalog_releases_tenant_id ON catalog_releases (tenant_id);");
      statement.execute(
          """
          CREATE TABLE IF NOT EXISTS catalog_items (
            catalog_item_id VARCHAR(255) NOT NULL PRIMARY KEY,
            catalog_item_type VARCHAR(255) NOT NULL,
            catalog_item_external_id VARCHAR(255) NOT NULL,
            catalog_item_version VARCHAR(255) NOT NULL,
            catalog_item_status VARCHAR(255) NOT NULL,
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
            catalog_item_content_hash VARCHAR(64),
            catalog_item_domain VARCHAR(255),
            catalog_item_payload_refs TEXT,
            catalog_item_requires TEXT,
            catalog_item_archive_name VARCHAR(255) NOT NULL,
            catalog_item_archive_hash VARCHAR(64) NOT NULL,
            catalog_item_release_id VARCHAR(255) NOT NULL
              REFERENCES catalog_releases (catalog_release_id) ON DELETE CASCADE,
            catalog_item_published_at TIMESTAMP WITH TIME ZONE,
            catalog_item_created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
            catalog_item_updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
            tenant_id VARCHAR(255) NOT NULL REFERENCES tenants (tenant_id) ON DELETE CASCADE,
            CONSTRAINT catalog_items_item_version_tenant_unique
              UNIQUE (catalog_item_type, catalog_item_external_id, catalog_item_version, tenant_id)
          );
          """);
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_catalog_items_tenant_id ON catalog_items (tenant_id);");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_catalog_items_release_id ON catalog_items (catalog_item_release_id);");
    }
  }
}
