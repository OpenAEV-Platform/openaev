package io.openaev.migration;

import io.openaev.service.EsIndexingUtils;
import java.sql.Statement;
import java.time.Instant;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Requests an in-place re-feed of the {@code finding} search index, so that every finding document
 * gets {@code base_asset_groups_side}, the asset groups targeted by the injects of its occurrences.
 *
 * <p>The field is added to the live index mapping at boot (the mapping update of the engine driver
 * keeps the documents in place), but the incremental indexing only revisits a finding whose row
 * changed: without a re-feed, the findings indexed before the upgrade would carry no asset group,
 * and a widget grouped by asset group would undercount them. Moving the {@code finding} cursor back
 * to epoch makes the incremental sync re-read every finding and rewrite its document (bulk index =
 * upsert), while the index stays complete throughout.
 *
 * <p>Same procedure, and same reasons not to delete the {@code indexing_status} row, as {@link
 * V6_20261022140626000__Request_finding_refeed_after_sensitive_value_masking}: the update leaves a
 * pending reset request ({@link EsIndexingUtils#REINDEX_REQUESTED_CURSOR}) and a missing row alone,
 * and the model is also registered in-process ({@link EsIndexingUtils#requestRefeedAtStartup}) so
 * that the pod applying this migration re-asserts epoch once the pods of the previous version,
 * which write documents without the field, are expected to be gone.
 *
 * <p>Idempotent and lock-light: a single row of a bookkeeping table.
 */
@Component
public class V6_20261118120000000__Request_finding_refeed_for_asset_groups_side
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
