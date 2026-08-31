package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Promotes AI targets ({@code asset_category = 'AI_TARGET'}) from the base {@code Asset} type to
 * {@code Endpoint}.
 *
 * <p>AI targets were the last category persisted with the base discriminator (via {@code
 * /api/ai_targets}). Every {@code Endpoint}-typed surface therefore ignored them: they never showed
 * up in {@code POST /api/endpoints/search}, could not be resolved by {@code GET/PUT
 * /api/endpoints/{id}}, and were missing from the atomic-testing target picker. They are now
 * created as agentless {@code Endpoint} rows, exactly like the other non-host categories restored
 * by {@code V6_20260729120000000__Restore_demoted_endpoint_assets}; this migration re-discriminates
 * the rows created before that change.
 *
 * <p>Idempotent and targeted (no table rewrite):
 *
 * <ol>
 *   <li>Backfill the host-only columns that are NOT NULL or read as primitives on the {@code
 *       Endpoint} entity ({@code endpoint_arch}, {@code endpoint_platform}, {@code
 *       endpoint_is_eol}) for the rows about to be promoted. {@code Unknown} is the same default
 *       {@code Endpoint#applyEndpointDefaults()} applies at runtime for agentless endpoints.
 *   <li>Re-discriminate the rows to {@code Endpoint}. The category is left untouched, so the {@code
 *       /api/ai_targets} facade keeps resolving exactly the same rows. Once promoted, a second run
 *       matches nothing.
 *   <li>Reset the indexing status of the asset-derived ES indexes so documents indexed with the
 *       base type are rebuilt.
 * </ol>
 */
@Component
public class V6_20260831100000000__Promote_ai_targets_to_endpoints extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      // -- 1. Backfill the host-only columns before the promotion --
      statement.execute(
          "UPDATE assets SET "
              + "endpoint_platform = COALESCE(endpoint_platform, 'Unknown'), "
              + "endpoint_arch = COALESCE(endpoint_arch, 'Unknown'), "
              + "endpoint_is_eol = COALESCE(endpoint_is_eol, FALSE) "
              + "WHERE asset_type = 'Asset' AND asset_category = 'AI_TARGET';");

      // -- 2. Promote AI targets to Endpoint --
      statement.execute(
          "UPDATE assets SET asset_type = 'Endpoint' "
              + "WHERE asset_type = 'Asset' AND asset_category = 'AI_TARGET';");

      // -- 3. Rebuild the asset-derived ES indexes --
      // The promotion is an UPDATE, so the incremental indexer would leave documents carrying the
      // base type behind. Dropping the indexing status makes the engine recreate and fully reindex
      // these indexes at startup. Idempotent by nature.
      statement.execute(
          "DELETE FROM indexing_status "
              + "WHERE indexing_status_type IN ('asset', 'vulnerable-endpoint');");
    }
  }
}
