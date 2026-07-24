package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds the {@code engine_deletions} journal backing the search-engine deletion replay.
 *
 * <p>Deleting an entity removes its documents from the search engine through an after-commit
 * delete-by-query, but that flush alone cannot converge the engine with PostgreSQL: the periodic
 * incremental indexer reads rows, transforms them, then bulk-writes documents — an entity deleted
 * between the read and the write gets its document resurrected right after the delete-by-query, and
 * since the row no longer exists in PostgreSQL, no event will ever clean it up again (observed as
 * ghost simulations inflating dashboards after bulk deletions). An engine outage during the
 * after-commit flush loses the deletion the same way.
 *
 * <p>Deleted ids are now journaled in {@code engine_deletions} and replayed by a scheduled job
 * (idempotent delete-by-query) until they age out of the retention window, guaranteeing
 * convergence. The table is platform-level plumbing (ids are globally unique UUIDs, and engine
 * deletions are not tenant-filtered), so it carries no tenant column.
 *
 * <p>Also purges every {@code indexing_status} row so the engine drivers drop, recreate and fully
 * re-feed all indexes at the next startup — cleaning documents already resurrected by the race on
 * existing installs. Idempotent and lock-light (new table + full DELETE on a tiny bookkeeping
 * table).
 */
@Component
public class V6_20260723190000000__Add_engine_deletions_journal extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          CREATE TABLE IF NOT EXISTS engine_deletions (
            deletion_id VARCHAR(255) NOT NULL,
            deletion_date TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
            CONSTRAINT pk_engine_deletions PRIMARY KEY (deletion_id)
          );
          """);
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_engine_deletions_date"
              + " ON engine_deletions (deletion_date);");
      // Purge stale documents already accumulated by the indexer/delete race: force a full
      // drop + re-feed of every index at next startup.
      statement.execute("DELETE FROM indexing_status;");
    }
  }
}
