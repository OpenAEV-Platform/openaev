package io.openaev.migration;

import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Indexes the reads of the exploration graph landing (#9447), so the exposure ranking and the
 * estate overview do not scan every expectation of a 100K-asset tenant.
 *
 * <p>{@code idx_injects_expectations_failed_asset} is a partial index on the failed, scored
 * expectations of an asset, the only rows the exposure ranking and the group failure rates count.
 * It is built {@code CONCURRENTLY}, outside a Flyway transaction, so writes to {@code
 * injects_expectations} are not blocked while it builds; an index left invalid by an interrupted
 * build is dropped and built again. The asset side of the group membership is already indexed by
 * {@code idx_asset_groups_assets_asset} since {@code V2_68}: this migration does not touch it.
 */
@Component
public class V6_20261119013000000__Add_graph_landing_indexes extends BaseJavaMigration {

  static final String FAILED_ASSET_INDEX = "idx_injects_expectations_failed_asset";

  @Override
  public boolean canExecuteInTransaction() {
    return false;
  }

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      boolean invalid;
      try (ResultSet result =
          statement.executeQuery(
              "SELECT NOT indisvalid FROM pg_index WHERE indexrelid = to_regclass('"
                  + FAILED_ASSET_INDEX
                  + "')")) {
        invalid = result.next() && result.getBoolean(1);
      }
      if (invalid) {
        statement.execute("DROP INDEX CONCURRENTLY IF EXISTS " + FAILED_ASSET_INDEX);
      }
      statement.execute(
          "CREATE INDEX CONCURRENTLY IF NOT EXISTS "
              + FAILED_ASSET_INDEX
              + " ON injects_expectations (asset_id)"
              + " WHERE asset_id IS NOT NULL AND inject_expectation_score IS NOT NULL"
              + " AND inject_expectation_score"
              + " < COALESCE(inject_expectation_expected_score, 100)");
    }
  }
}
