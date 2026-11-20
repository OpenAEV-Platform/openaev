package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Indexes the reads of the exploration graph landing (#9447), so the exposure ranking and the
 * estate overview do not scan every expectation or group membership of a 100K-asset tenant.
 *
 * <ul>
 *   <li>{@code idx_injects_expectations_failed_asset}: a partial index on the failed, scored
 *       expectations of an asset, the only rows the exposure ranking and the group failure rates
 *       count.
 *   <li>{@code idx_asset_groups_assets_asset}: the asset side of the group membership, read by the
 *       ungrouped bucket and by the asset-to-group expansion.
 * </ul>
 */
@Component
public class V6_20261119013000000__Add_graph_landing_indexes extends BaseJavaMigration {

  static final String FAILED_ASSET_INDEX = "idx_injects_expectations_failed_asset";
  static final String GROUP_MEMBER_INDEX = "idx_asset_groups_assets_asset";

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "CREATE INDEX IF NOT EXISTS "
              + FAILED_ASSET_INDEX
              + " ON injects_expectations (asset_id)"
              + " WHERE asset_id IS NOT NULL AND inject_expectation_score IS NOT NULL"
              + " AND inject_expectation_score"
              + " < COALESCE(inject_expectation_expected_score, 100)");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS "
              + GROUP_MEMBER_INDEX
              + " ON asset_groups_assets (asset_id)");
    }
  }
}
