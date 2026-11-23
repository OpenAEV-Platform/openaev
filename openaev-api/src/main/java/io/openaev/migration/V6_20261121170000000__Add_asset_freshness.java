package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Asset freshness: when an asset was last observed and by what source. Staleness is not stored (it
 * is the STALE value of asset_status, read from this stamp and the tenant threshold). Existing
 * assets take the most recent heartbeat of their agents as their last observation when they have
 * one; additive, no event.
 */
@Component
public class V6_20261121170000000__Add_asset_freshness extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute("ALTER TABLE assets ADD COLUMN IF NOT EXISTS asset_last_seen TIMESTAMPTZ");
      statement.execute(
          "ALTER TABLE assets ADD COLUMN IF NOT EXISTS asset_last_seen_source VARCHAR(255)");
      // Backfill once: an asset never stamped takes the latest heartbeat of its agents
      statement.execute(
          "UPDATE assets a SET asset_last_seen = s.last_seen, asset_last_seen_source = 'agent'"
              + " FROM (SELECT agent_asset, max(agent_last_seen) AS last_seen FROM agents"
              + " WHERE agent_last_seen IS NOT NULL GROUP BY agent_asset) s"
              + " WHERE s.agent_asset = a.asset_id AND a.asset_last_seen IS NULL");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_assets_tenant_last_seen"
              + " ON assets (tenant_id, asset_last_seen)");
    }
  }
}
