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
 * <p>All steps are idempotent and either metadata-only or targeted updates (no table rewrite):
 *
 * <ol>
 *   <li>Rename the target-relevant network columns {@code endpoint_* -> asset_*} - they now live on
 *       the {@code Asset} base since they are relevant to more than agent hosts (web / cloud /
 *       network categories).
 *   <li>Re-discriminate rows: former {@code AiTarget} rows and non-host {@code Endpoint} rows
 *       without agents become the base {@code Asset} type ({@code asset_type = 'Asset'}); {@code
 *       SecurityPlatform} rows are left untouched. The category column continues to carry the
 *       product taxonomy.
 *   <li>Backfill {@code asset_category = 'HOST'} on legacy endpoints with a NULL category.
 *   <li>Rewrite stored dynamic asset-group filter keys to the renamed properties.
 *   <li>Reset the indexing status of the endpoint-derived ES indexes so stale documents of demoted
 *       rows are rebuilt away.
 * </ol>
 */
@Component
public class V6_20260717120000000__Remodel_asset_taxonomy extends BaseJavaMigration {

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
      // Rows that carry agents are never demoted regardless of category: agents only exist on
      // Endpoint, so demoting an agent-bearing row (e.g. a host a user categorized as
      // CLOUD_RESOURCE, or a legacy platform bucketed into GENERIC_ASSET) would orphan its agents
      // and silently break execution on that host.
      statement.execute(
          "UPDATE assets SET asset_type = 'Asset' "
              + "WHERE asset_type = 'Endpoint' AND asset_category IN ("
              + "'CLOUD_RESOURCE','WEB_APPLICATION','NETWORK_DEVICE','IOT_OT_DEVICE',"
              + "'IDENTITY','SAAS_APPLICATION','GENERIC_ASSET') "
              + "AND NOT EXISTS (SELECT 1 FROM agents ag WHERE ag.agent_asset = assets.asset_id);");

      // -- 3. Backfill the category on legacy endpoints that predate the asset_category column --
      // These rows carry a NULL category, so the inventory shows an empty "Category" cell. Endpoint
      // is now agent-capable-host only and the entity's @PrePersist default is HOST, but that only
      // fires on write; mirror it here for rows untouched since the column was introduced
      // (idempotent - only touches the remaining NULLs).
      statement.execute(
          "UPDATE assets SET asset_category = 'HOST' "
              + "WHERE asset_type = 'Endpoint' AND asset_category IS NULL;");

      // -- 4. Rewrite stored dynamic asset-group filter keys to the renamed properties --
      // Dynamic filters persist JPA queryable keys; the network properties moved to the Asset
      // base under new names, so saved filters on the old endpoint_* keys would become
      // unresolvable and fail every group resolution. Idempotent: the LIKE guard matches
      // nothing once the keys are rewritten.
      for (String[] rename :
          new String[][] {
            {"endpoint_hostname", "asset_hostname"},
            {"endpoint_ips", "asset_ips"},
            {"endpoint_seen_ip", "asset_seen_ip"},
            {"endpoint_url", "asset_url"},
            {"endpoint_mac_addresses", "asset_mac_addresses"},
          }) {
        statement.execute(
            "UPDATE asset_groups SET asset_group_dynamic_filter = "
                + "replace(asset_group_dynamic_filter::text, '\""
                + rename[0]
                + "\"', '\""
                + rename[1]
                + "\"')::json "
                + "WHERE asset_group_dynamic_filter::text LIKE '%\""
                + rename[0]
                + "\"%';");
      }

      // -- 5. Rebuild the endpoint-derived ES indexes --
      // Demoted rows (former AI targets / non-host endpoints) leave stale documents behind in
      // the endpoint index (re-discrimination is an UPDATE, not a DELETE, so the incremental
      // indexer never removes them). Dropping the indexing status makes the engine recreate and
      // fully reindex these indexes at startup. Idempotent by nature.
      statement.execute(
          "DELETE FROM indexing_status "
              + "WHERE indexing_status_type IN ('endpoint', 'vulnerable-endpoint');");
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
