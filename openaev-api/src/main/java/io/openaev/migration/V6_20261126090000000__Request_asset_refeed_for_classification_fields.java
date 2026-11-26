package io.openaev.migration;

import io.openaev.service.EsIndexingUtils;
import java.sql.Statement;
import java.time.Instant;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Requests an in-place re-feed of the {@code asset} search index, so that every asset document
 * carries the classification fields ({@code asset_subcategory}, {@code asset_criticality}, {@code
 * asset_internet_facing}, {@code asset_url}).
 *
 * <p>{@link V6_20261125170000000__Reindex_assets_with_classification_fields} requested the rebuild
 * by deleting the {@code indexing_status} row. During a rolling deploy the incremental indexer of a
 * pod still running the previous version re-creates that row and re-feeds the assets with its own
 * document building, which has no classification fields; the new pod then finds a row at boot and
 * never rebuilds the index. The documents written that way keep a null {@code
 * asset_internet_facing} until their asset changes again, so analytics on the classification
 * fields undercount (issue #9888).
 *
 * <p>Same procedure as {@link V6_20261118120000000__Request_finding_refeed_for_asset_groups_side}:
 * the {@code asset} cursor is moved back to epoch (the index stays complete throughout, bulk index =
 * upsert), a pending reset request ({@link EsIndexingUtils#REINDEX_REQUESTED_CURSOR}) and a missing
 * row are left alone, and the model is registered in-process ({@link
 * EsIndexingUtils#requestRefeedAtStartup}) so that the pod applying this migration re-asserts epoch
 * once the pods of the previous version are expected to be gone. Instances that already applied the
 * previous migration get the reliable re-feed too.
 *
 * <p>Idempotent and lock-light: a single row of a bookkeeping table.
 */
@Component
public class V6_20261126090000000__Request_asset_refeed_for_classification_fields
    extends BaseJavaMigration {

  private static final String ASSET_MODEL = "asset";

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
              + ASSET_MODEL
              + "' AND indexing_status_indexing_date < '"
              + EsIndexingUtils.REINDEX_REQUESTED_THRESHOLD
              + "'");
    }
    EsIndexingUtils.requestRefeedAtStartup(ASSET_MODEL);
  }
}
