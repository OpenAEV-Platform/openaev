package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Converges the search engine back to database truth after the cascade-delete indexing fixes.
 *
 * <p>Injector contract deletions (direct delete, contract re-sync during injector registration,
 * payload deletion) cascade-delete their injects at the DATABASE level ({@code
 * injects.inject_injector_contract} is {@code ON DELETE CASCADE}): no JPA lifecycle event ever
 * fired for those injects, so their documents — and the dependent expectation and finding documents
 * — were orphaned in the search indexes, inflating coverage tiles, dashboards and every ES-backed
 * statistic. The same class of gap affected re-detected findings (the upsert conflict branch never
 * bumped {@code finding_updated_at}) and deleted agents (denormalized agent data kept stale on
 * endpoint documents).
 *
 * <p>The write paths are fixed in code; this migration purges the garbage already accumulated:
 * deleting every {@code indexing_status} row makes the engine drivers drop, recreate and fully
 * re-feed all indexes from PostgreSQL at the next startup. Idempotent and lock-light (full DELETE
 * on a tiny bookkeeping table).
 */
@Component
public class V6_20260723120000000__Force_full_reindex_after_cascade_delete_fixes
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute("DELETE FROM indexing_status;");
    }
  }
}
