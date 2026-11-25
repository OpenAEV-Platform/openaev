package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Asset ownership (#9725): an asset owned (business owner) or managed (technical owner) by a person
 * or a team, and the source of every asset link, so a link an agent reported can be refreshed and a
 * manual removal of it kept.
 *
 * <p>{@code assets_owners} is a join table like {@code assets_links}: no tenant column, the asset
 * carries it and {@code AssetOwnerService}, the only writer, refuses an owner outside the asset's
 * tenant. Deleting the asset, the person or the team deletes the owner row.
 */
@Component
public class V6_20261125140000000__Add_assets_owners_and_link_sources extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          CREATE TABLE IF NOT EXISTS assets_owners (
            asset_owner_id VARCHAR(255) NOT NULL PRIMARY KEY,
            asset_id VARCHAR(255) NOT NULL REFERENCES assets (asset_id) ON DELETE CASCADE,
            asset_owner_user_id VARCHAR(255) REFERENCES users (user_id) ON DELETE CASCADE,
            asset_owner_team_id VARCHAR(255) REFERENCES teams (team_id) ON DELETE CASCADE,
            asset_owner_role VARCHAR(255) NOT NULL,
            asset_owner_source VARCHAR(255) NOT NULL DEFAULT 'MANUAL',
            asset_owner_created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
            CONSTRAINT assets_owners_one_owner CHECK (
              (asset_owner_user_id IS NULL) <> (asset_owner_team_id IS NULL))
          );
          """);
      statement.execute(
          "CREATE UNIQUE INDEX IF NOT EXISTS uq_assets_owners_user"
              + " ON assets_owners (asset_id, asset_owner_user_id, asset_owner_role)"
              + " WHERE asset_owner_user_id IS NOT NULL;");
      statement.execute(
          "CREATE UNIQUE INDEX IF NOT EXISTS uq_assets_owners_team"
              + " ON assets_owners (asset_id, asset_owner_team_id, asset_owner_role)"
              + " WHERE asset_owner_team_id IS NOT NULL;");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_assets_owners_user ON assets_owners (asset_owner_user_id);");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_assets_owners_team ON assets_owners (asset_owner_team_id);");
      statement.execute(
          "ALTER TABLE assets_links"
              + " ADD COLUMN IF NOT EXISTS asset_link_source VARCHAR(255) NOT NULL DEFAULT 'MANUAL',"
              + " ADD COLUMN IF NOT EXISTS asset_link_last_seen_at TIMESTAMPTZ,"
              + " ADD COLUMN IF NOT EXISTS asset_link_suppressed BOOLEAN NOT NULL DEFAULT false;");
    }
  }
}
