package io.openaev.migration;

import io.openaev.service.EsIndexingUtils;
import java.sql.Statement;
import java.time.Instant;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Requests an in-place re-feed of the {@code finding} search index, so that {@code
 * base_asset_groups_side} holds the asset groups that contain one of the finding's assets (static
 * and dynamic membership) instead of the groups targeted by the injects of its occurrences, which
 * included groups the hit asset is not in.
 *
 * <p>Same procedure, and same reasons, as {@link
 * V6_20261118120000000__Request_finding_refeed_for_asset_groups_side}: the cursor moves back to
 * epoch so the incremental sync rewrites every document in place, a pending reset request is left
 * alone, and the model is registered in-process so the pod applying this migration re-asserts epoch
 * once the pods of the previous version are gone.
 *
 * <p>Idempotent and lock-light: a single row of a bookkeeping table.
 */
@Component
public class V6_20261119170000000__Request_finding_refeed_for_asset_groups_of_assets
    extends BaseJavaMigration {

  private static final String FINDING_MODEL = "finding";

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      // ISO-8601 UTC literals: PostgreSQL stores the absolute instants in the timestamptz column,
      // with no JVM time zone involved.
      statement.executeUpdate(
          "UPDATE indexing_status SET indexing_status_indexing_date = '"
              + Instant.EPOCH
              + "', indexing_status_last_id = NULL"
              + " WHERE indexing_status_type = '"
              + FINDING_MODEL
              + "' AND indexing_status_indexing_date < '"
              + EsIndexingUtils.REINDEX_REQUESTED_THRESHOLD
              + "'");
    }
    EsIndexingUtils.requestRefeedAtStartup(FINDING_MODEL);
  }
}
