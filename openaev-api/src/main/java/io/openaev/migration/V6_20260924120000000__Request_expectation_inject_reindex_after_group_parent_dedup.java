package io.openaev.migration;

import io.openaev.service.EsIndexingUtils;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Requests a full reset of the {@code expectation-inject} search index at the next startup, to drop
 * the documents of the duplicated asset-group expectations deleted by {@code
 * V6_20260922170000000__Repair_duplicated_group_parents_and_stuck_agentless_leaves}.
 *
 * <p>That repair deleted the duplicated rows with a raw SQL {@code DELETE}, so no entity lifecycle
 * event journaled their ids for the engine, and requested the cleanup by deleting the model's
 * {@code indexing_status} row ("a missing row means wipe and recreate at boot"). On a rolling
 * deploy this protocol does not hold: the pod still running the previous version keeps its
 * 15-second engine sync alive, treats the missing row as "index from epoch" and re-creates it long
 * before the new pod reaches its boot-time check, which then finds a row and never wipes the index.
 * The deleted rows therefore survive as documents stuck on {@code PENDING} (paired duplicates on
 * the results page, phantom "Pending" counts on the home dashboard) that nothing can ever update or
 * delete.
 *
 * <p>This migration requests the reset twice over, so that no instance still running can lose it:
 *
 * <ol>
 *   <li>in the database, with the {@link EsIndexingUtils#REINDEX_REQUESTED_CURSOR} far-future
 *       cursor. A pod running any version fetches "rows updated after year 9999", gets nothing,
 *       reports the model as up to date and leaves the row untouched. The one write that can still
 *       replace it is the sync round of a pod running a version OLDER than this one that read the
 *       row before this migration committed and persists its cursor unconditionally at the end of
 *       the round (pods running this version persist conditionally and never replace a request);
 *   <li>in-process, with {@link EsIndexingUtils#requestReindexAtStartup}: Flyway runs before the
 *       engine drivers' boot check, so the pod applying this migration wipes and recreates the
 *       index whatever an older pod did to the row in the meantime.
 * </ol>
 *
 * <p>Installs that never had duplicates only pay a one-time rebuild of this index. Idempotent
 * (upsert on the primary key) and lock-light (a single row of a bookkeeping table).
 */
@Component
public class V6_20260924120000000__Request_expectation_inject_reindex_after_group_parent_dedup
    extends BaseJavaMigration {

  private static final String EXPECTATION_INJECT_MODEL = "expectation-inject";

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      // The sentinel is written as an ISO-8601 UTC literal ('9999-12-31T00:00:00Z'): PostgreSQL
      // stores the absolute instant in the timestamptz column, with no JVM time zone involved.
      statement.execute(
          "INSERT INTO indexing_status (indexing_status_type, indexing_status_indexing_date)"
              + " VALUES ('"
              + EXPECTATION_INJECT_MODEL
              + "', '"
              + EsIndexingUtils.REINDEX_REQUESTED_CURSOR
              + "')"
              + " ON CONFLICT (indexing_status_type)"
              + " DO UPDATE SET indexing_status_indexing_date = EXCLUDED.indexing_status_indexing_date");
    }
    EsIndexingUtils.requestReindexAtStartup(EXPECTATION_INJECT_MODEL);
  }
}
