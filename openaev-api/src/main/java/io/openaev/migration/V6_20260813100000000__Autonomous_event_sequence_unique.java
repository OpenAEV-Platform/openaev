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
 * <p>Additive and idempotent: any run holding duplicate sequences is deterministically resequenced
 * first - every event row is KEPT (each duplicate narrates a distinct decision / audit entry, so
 * deleting one would lose append-only timeline data) and the whole run timeline is renumbered 1..N
 * in its existing order (sequence, then creation time, then event id as the tie-break). Then the
 * old index is dropped and the unique index created with {@code IF [NOT] EXISTS}. On a clean table
 * every sequence already equals its row number, so re-running is a no-op.
 *
 * <p>The whole pass runs behind an {@code ACCESS EXCLUSIVE} table lock held for the migration's
 * single transaction: during a rolling deployment an old application node (without the per-run
 * advisory lock) could otherwise insert a fresh duplicate between the resequencing and the index
 * build and fail it. The strongest mode is deliberate - it is acquired as the FIRST statement, so
 * the migration never upgrades its lock later: {@code DROP INDEX} needs {@code ACCESS EXCLUSIVE}
 * anyway, and upgrading from a weaker table lock while an in-flight appender holds {@code ACCESS
 * SHARE} (from its max-sequence read) with its INSERT queued behind us is a textbook lock-upgrade
 * deadlock that would abort the migration mid-deployment. Taken up front, an in-flight appender
 * simply finishes first (the migration holds nothing while it waits) and everything else queues for
 * the migration's brief duration; the {@code autonomous_events} table is a new, preview-flag-gated,
 * human-review-cadence store, so the momentary stall is safe.
 */
@Component
public class V6_20260813100000000__Autonomous_event_sequence_unique extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      // Shield the repair + enforcement from live writers: during a rolling deployment an OLD
      // application node (without the per-run advisory lock) can still append while this runs, so
      // an insert landing between the resequencing CTE and the index build could re-introduce a
      // duplicate and fail the unique-index creation. Flyway runs this migration in a single
      // transaction, so the lock spans every statement below. ACCESS EXCLUSIVE (not a weaker
      // write-blocking mode) because the DROP INDEX below requires it anyway and it must be the
      // FIRST lock taken: with only SHARE ROW EXCLUSIVE held, an old-node appender that already
      // did its max-sequence read (holding ACCESS SHARE) queues its INSERT behind us, and our
      // later DROP INDEX then waits on its ACCESS SHARE - a mutual wait on granted locks, i.e. a
      // deadlock that aborts the migration mid-deployment. Acquired up front, the migration holds
      // nothing while it waits (in-flight appenders finish first), then never upgrades.
      statement.execute("LOCK TABLE autonomous_events IN ACCESS EXCLUSIVE MODE;");
      // Resequence any run carrying (run_id, sequence) duplicates so the unique index can be
      // built WITHOUT deleting timeline rows: each duplicate is a distinct decision/audit event,
      // so all rows are kept and the run's timeline is renumbered 1..N in deterministic order
      // (existing sequence, then creation time, then event id). A no-op on a clean table, where
      // every sequence already equals its row number.
      statement.execute(
          """
          WITH ranked AS (
              SELECT autonomous_event_id,
                     ROW_NUMBER() OVER (
                         PARTITION BY autonomous_event_run_id
                         ORDER BY autonomous_event_sequence,
                                  autonomous_event_created_at,
                                  autonomous_event_id
                     ) AS new_sequence
                FROM autonomous_events
          )
          UPDATE autonomous_events e
             SET autonomous_event_sequence = r.new_sequence
            FROM ranked r
           WHERE e.autonomous_event_id = r.autonomous_event_id
             AND e.autonomous_event_sequence <> r.new_sequence;
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
