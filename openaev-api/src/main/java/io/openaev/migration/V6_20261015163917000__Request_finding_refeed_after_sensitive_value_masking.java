package io.openaev.migration;

import io.openaev.service.EsIndexingUtils;
import java.sql.Statement;
import java.time.Instant;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Requests an in-place re-feed of the {@code finding} search index, so that the values indexed
 * before sensitive values were masked at indexing time get masked too.
 *
 * <p>Since then, {@code FindingHandler} writes finding values in their masked form. But the
 * incremental indexing only revisits a finding whose row changed, so every document indexed before
 * the upgrade keeps its value in cleartext - readable in dashboards, widgets and reports, and
 * matched by their filters. Moving the {@code finding} cursor back to epoch makes the incremental
 * sync re-read every finding and rewrite its document (bulk index = upsert), masked.
 *
 * <p><b>Why not {@code DELETE FROM indexing_status}</b>, as the review-migration checklist
 * prescribes for a reindex: a deleted row makes the boot wipe and rebuild the index, which leaves
 * every findings dashboard incomplete for the whole rebuild (the sync indexes 500 rows per 15
 * seconds, about 50 minutes per 100k findings). It is also racy on a rolling deploy: a pod still
 * running re-creates the row before the new pod's boot check (see {@code
 * V6_20260924120000000__Request_expectation_inject_reindex_after_group_parent_dedup}). The
 * documents here only need rewriting, not dropping, so the index is re-fed in place and stays
 * complete throughout.
 *
 * <p>The update leaves alone:
 *
 * <ul>
 *   <li>a pending reset request ({@link EsIndexingUtils#REINDEX_REQUESTED_CURSOR}): that reset
 *       wipes and rebuilds the index, masked, and schedules its own epoch re-assertion;
 *   <li>a missing row: the boot indexes every finding anyway, masked. It is not inserted.
 * </ul>
 *
 * <p><b>Rolling deploy.</b> Pods still running the previous version take part in the re-feed and
 * write cleartext documents; the oldest ones may also overwrite the epoch cursor. So, when the row
 * was actually moved back to epoch, the model is also registered in-process ({@link
 * EsIndexingUtils#requestRefeedAtStartup}): the pod applying this migration re-asserts epoch once
 * more after {@code engine.indexing-refeed-reassert-delay-seconds}, by which time the old pods are
 * expected to be gone. A rollout longer than that delay, a rollback followed by a new upgrade, or
 * this pod restarting before the delay leaves cleartext documents behind; the ops workaround is to
 * move the {@code finding} cursor back to epoch by hand, as this migration does.
 *
 * <p>Idempotent and lock-light: a single row of a bookkeeping table.
 */
@Component
public class V6_20261015163917000__Request_finding_refeed_after_sensitive_value_masking
    extends BaseJavaMigration {

  private static final String FINDING_MODEL = "finding";

  @Override
  public void migrate(Context context) throws Exception {
    int updated;
    try (Statement statement = context.getConnection().createStatement()) {
      // ISO-8601 UTC literals: PostgreSQL stores the absolute instants in the timestamptz column,
      // with no JVM time zone involved.
      updated =
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
    if (updated == 1) {
      EsIndexingUtils.requestRefeedAtStartup(FINDING_MODEL);
    }
  }
}
