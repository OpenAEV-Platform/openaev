package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Repairs assets demoted by an earlier revision of {@code
 * V6_20260717120000000__Remodel_asset_taxonomy}: agentless endpoints whose category was
 * CLOUD_RESOURCE / WEB_APPLICATION / NETWORK_DEVICE / IOT_OT_DEVICE / IDENTITY / SAAS_APPLICATION /
 * GENERIC_ASSET were re-discriminated from {@code Endpoint} to the base {@code Asset} type. The
 * rest of the product was not ready for base-typed targets: the edit flow ({@code GET/PUT
 * /api/endpoints/{id}}) resolves through the {@code Endpoint} entity and returns 404 for them, the
 * atomic-testing target picker ({@code POST /api/endpoints/search}) is {@code Endpoint}-typed so
 * they disappear from target selection, and the creation flow still persists every non-AI category
 * as an agentless {@code Endpoint}. The demotion has been removed from the original migration for
 * platforms upgrading from stable; this migration restores the rows on platforms that already ran
 * the demoting revision.
 *
 * <p>Idempotent and targeted (no table rewrite):
 *
 * <ol>
 *   <li>Re-promote base {@code Asset} rows of the seven demoted categories back to {@code
 *       Endpoint}. AI targets are untouched here: at the time this migration was written they were
 *       the only rows legitimately persisted with the base type (via {@code /api/ai_targets}) and
 *       always carry {@code asset_category = 'AI_TARGET'}, which is not part of the list. They are
 *       promoted separately by {@code V6_20260831100000000__Promote_ai_targets_to_endpoints}. Once
 *       re-promoted, the second run matches nothing.
 *   <li>Reset the indexing status of the asset-derived ES indexes so documents indexed with the
 *       demoted type are rebuilt.
 * </ol>
 */
@Component
public class V6_20260729120000000__Restore_demoted_endpoint_assets extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      // -- 1. Re-promote demoted rows back to Endpoint --
      statement.execute(
          "UPDATE assets SET asset_type = 'Endpoint' "
              + "WHERE asset_type = 'Asset' AND asset_category IN ("
              + "'CLOUD_RESOURCE','WEB_APPLICATION','NETWORK_DEVICE','IOT_OT_DEVICE',"
              + "'IDENTITY','SAAS_APPLICATION','GENERIC_ASSET');");

      // -- 2. Rebuild the asset-derived ES indexes --
      // The re-promotion is an UPDATE, so the incremental indexer would leave documents carrying
      // the demoted type behind. Dropping the indexing status makes the engine recreate and fully
      // reindex these indexes at startup. Idempotent by nature.
      statement.execute(
          "DELETE FROM indexing_status "
              + "WHERE indexing_status_type IN ('asset', 'vulnerable-endpoint');");
    }
  }
}
