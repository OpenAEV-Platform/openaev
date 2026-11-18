package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Typed links between assets (#9447): an identity asset logged on to an endpoint, a service running
 * on a host. A join table like {@code asset_groups_assets}: no tenant column, both ends are assets
 * and deleting either one deletes its links. The same-tenant rule is enforced by {@code
 * AssetLinkService}, the only writer.
 */
@Component
public class V6_20261118230000000__Add_assets_links extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          CREATE TABLE IF NOT EXISTS assets_links (
            asset_id VARCHAR(255) NOT NULL REFERENCES assets (asset_id) ON DELETE CASCADE,
            linked_asset_id VARCHAR(255) NOT NULL REFERENCES assets (asset_id) ON DELETE CASCADE,
            asset_link_type VARCHAR(255) NOT NULL,
            asset_link_created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
            CONSTRAINT assets_links_pkey PRIMARY KEY (asset_id, linked_asset_id, asset_link_type),
            CONSTRAINT assets_links_not_self CHECK (asset_id <> linked_asset_id)
          );
          """);
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_assets_links_linked_asset"
              + " ON assets_links (linked_asset_id);");
    }
  }
}
