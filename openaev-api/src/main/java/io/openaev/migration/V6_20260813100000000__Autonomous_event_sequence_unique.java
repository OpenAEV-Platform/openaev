package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Makes an autonomous run's decision-timeline sequence unique per run. {@code
 * AutonomousEventService} assigns each event {@code sequence = max(sequence) + 1} for its run;
 * backed only by the non-unique {@code idx_autonomous_events_run_seq} index, two appenders writing
 * to the same run concurrently could read the same max and persist a duplicate sequence, corrupting
 * timeline order and the "since sequence" incremental reads the live cockpit relies on.
 *
 * <p>This replaces that index with a UNIQUE one on {@code (autonomous_event_run_id,
 * autonomous_event_sequence)}: it enforces the per-run invariant at the database and still serves
 * the run-scoped ordered + since-sequence reads (same leading columns). The service additionally
 * takes a per-run transaction advisory lock before its read-max-then-insert so concurrent appenders
 * serialise (each succeeds with N and N+1) rather than one hitting the unique constraint; the index
 * is the backstop that makes a duplicate impossible even if that lock is ever bypassed.
 *
 * <p>Additive and idempotent: any pre-existing duplicate rows are collapsed first (keeping the
 * physically-first row of each group), then the old index is dropped and the unique index created
 * with {@code IF [NOT] EXISTS}, so re-running is a no-op. The {@code autonomous_events} table is a
 * new, preview-flag-gated, human-review-cadence store, so the brief index build lock is safe.
 */
@Component
public class V6_20260813100000000__Autonomous_event_sequence_unique extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      // Collapse any pre-existing (run_id, sequence) duplicates so the unique index can be built.
      // Keeps the physically-first row of each group (lowest ctid) and drops the rest; a no-op on a
      // clean table.
      statement.execute(
          """
          DELETE FROM autonomous_events a
                USING autonomous_events b
           WHERE a.autonomous_event_run_id = b.autonomous_event_run_id
             AND a.autonomous_event_sequence = b.autonomous_event_sequence
             AND a.ctid > b.ctid;
          """);
      // Replace the non-unique run/sequence index with a unique one: it enforces the per-run
      // sequence invariant AND still serves the run-scoped ordered + since-sequence reads.
      statement.execute("DROP INDEX IF EXISTS idx_autonomous_events_run_seq;");
      statement.execute(
          "CREATE UNIQUE INDEX IF NOT EXISTS idx_autonomous_events_run_seq_unique "
              + "ON autonomous_events (autonomous_event_run_id, autonomous_event_sequence);");
    }
  }
}
