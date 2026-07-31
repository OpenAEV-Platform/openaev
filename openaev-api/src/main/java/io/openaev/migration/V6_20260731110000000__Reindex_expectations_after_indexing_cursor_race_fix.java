package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Rebuilds the inject-expectation index after fixing the indexing cursor commit-visibility race.
 *
 * <p>The incremental sync fetched rows with a strictly-greater-than comparison on the persisted
 * cursor, but {@code @UpdateTimestamp} values are assigned at Hibernate flush time while the rows
 * only become visible at commit. A long transaction (typically the expectations expiration manager
 * batching up to 1000 expired expectations) could therefore commit rows whose {@code updated_at}
 * was already behind a cursor advanced by a concurrent sync round; those rows were never fetched
 * again. Since the engine's expectation status is computed from the score at indexing time, the
 * orphaned documents stayed frozen as PENDING forever while PostgreSQL held the correct final
 * state.
 *
 * <p>The race is fixed in code by a grace window on cursor advancement; this migration converges
 * the documents orphaned before the fix. Dropping the {@code indexing_status} row of a type makes
 * the engine driver drop, recreate and fully re-feed that index from PostgreSQL at the next
 * startup, so only the affected type is rebuilt. Idempotent and lock-light (targeted DELETE on a
 * tiny bookkeeping table).
 */
@Component
public class V6_20260731110000000__Reindex_expectations_after_indexing_cursor_race_fix
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "DELETE FROM indexing_status WHERE indexing_status_type = 'expectation-inject';");
    }
  }
}
