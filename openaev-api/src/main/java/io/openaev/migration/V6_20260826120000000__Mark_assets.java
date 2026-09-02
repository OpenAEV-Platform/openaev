package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Marks {@code assets} — the first marking-enabled table (step 3.1 of the marking design).
 *
 * <p>The whole schema change is one nullable array column. There is no join table, no foreign key
 * and no cascade, which is the point of the inline-array shape (design §3.2, Option 2): the marked
 * table's primary key never appears in the predicate, so relationship tables and composite keys are
 * marked with no special case. It also means nothing global has to be regenerated when the next
 * table is onboarded.
 *
 * <p><b>No backfill, deliberately.</b> {@code is_marking_set_allowed} coalesces a {@code NULL}
 * array to {@code '{}'}, and the empty set is contained in every clearance, so every existing asset
 * stays visible to everyone the moment the column appears. A marking can only ever <i>reduce</i>
 * visibility, never grant it, so adding the column is inert until something writes a marking.
 * Backfilling 200k+ rows with {@code '{}'} would rewrite the table for no behavioural difference.
 *
 * <p><b>Why the index is on an expression rather than on the column.</b> The rewritten predicate is
 * {@code COALESCE(marking_ids,'{}') <@ COALESCE(<clearance>,'{}')} — the left side is an
 * <i>expression</i>, so a plain {@code GIN (marking_ids)} index can never match it and would be
 * pure write-amplification. Verified on a 200k-row probe: with {@code enable_seqscan=off} the
 * planner still refused a plain GIN index and accepted this one.
 *
 * <p>Note this index is not expected to be <i>chosen</i> yet, and that is correct: while almost
 * every row is unmarked the predicate matches ~100% of the table and a sequential scan is genuinely
 * cheaper (14 ms for 200k rows on the same probe). It earns its keep once a meaningful fraction of
 * rows is hidden from the reader. It is created now because doing it later means an index build on
 * a large live table.
 *
 * <p>An index on the function itself is impossible: {@code is_marking_set_allowed} reads a GUC and
 * is therefore {@code STABLE}, and only {@code IMMUTABLE} expressions can be indexed.
 */
@Component
public class V6_20260826120000000__Mark_assets extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute("ALTER TABLE assets ADD COLUMN IF NOT EXISTS marking_ids text[];");

      // The COALESCE must match the one inlined from is_marking_set_allowed byte for byte, or the
      // planner will not recognise the index as applicable to the predicate.
      statement.execute(
          """
          CREATE INDEX IF NOT EXISTS idx_assets_marking_ids
            ON assets USING GIN ((COALESCE(marking_ids, '{}'::text[])));
          """);
    }
  }
}
