package io.openaev.engine;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Durable journal of entity ids deleted from PostgreSQL, pending confirmation in the search engine.
 *
 * <p>The immediate after-commit flush in {@link EngineListener} keeps the engine responsive, but it
 * is not sufficient on its own:
 *
 * <ul>
 *   <li><b>Indexer race (resurrection):</b> the periodic incremental indexer reads rows from
 *       PostgreSQL, transforms them, then bulk-writes documents to the engine. If an entity is
 *       deleted between the read and the write, the delete-by-query runs first and the indexer then
 *       re-creates the stale document - which is never cleaned up because the row no longer exists
 *       in PostgreSQL and no further delete event will ever fire for it. This is exactly how bulk
 *       deletions were observed leaving ghost simulations in dashboards.
 *   <li><b>Engine outage:</b> the after-commit flush logs and swallows engine errors, silently
 *       losing the deletion.
 * </ul>
 *
 * <p>Journaled ids are replayed by the {@code EngineDeletionReplayJob} on every pass until they age
 * out of the {@link #RETENTION} window (replays are idempotent delete-by-query calls), which
 * guarantees convergence: any document resurrected by an in-flight indexer batch is re-deleted on
 * the next replay.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EngineDeletionJournal {

  /**
   * How long a deletion keeps being replayed before it is pruned. Must comfortably exceed the
   * flight time of the slowest indexer batch plus the replay interval.
   */
  public static final Duration RETENTION = Duration.ofMinutes(10);

  private final JdbcTemplate jdbcTemplate;

  /** Records deleted entity ids; re-deleting the same id refreshes its journal date. */
  public void record(Collection<String> ids) {
    if (ids == null || ids.isEmpty()) {
      return;
    }
    try {
      jdbcTemplate.batchUpdate(
          "INSERT INTO engine_deletions (deletion_id, deletion_date) VALUES (?, now()) "
              + "ON CONFLICT (deletion_id) DO UPDATE SET deletion_date = EXCLUDED.deletion_date",
          ids.stream().map(id -> new Object[] {id}).toList());
    } catch (Exception e) {
      // Never break the caller: the immediate engine flush still runs; only the replay safety
      // net is lost for these ids.
      log.error("Failed to journal {} engine deletion(s): {}", ids.size(), e.getMessage(), e);
    }
  }

  /**
   * Returns every journaled id, oldest first. Deliberately unfiltered: retention is enforced by
   * {@link #prune()} after replaying, so entries older than the retention window (e.g. after a long
   * platform downtime) are still replayed one last time before being dropped.
   */
  public List<String> findPendingIds() {
    return jdbcTemplate.queryForList(
        "SELECT deletion_id FROM engine_deletions ORDER BY deletion_date ASC", String.class);
  }

  /** Prunes journal entries older than the retention window. */
  public void prune() {
    int pruned =
        jdbcTemplate.update(
            "DELETE FROM engine_deletions WHERE deletion_date < ?",
            Timestamp.from(Instant.now().minus(RETENTION)));
    if (pruned > 0) {
      log.debug("Pruned {} replayed engine deletion(s) from the journal", pruned);
    }
  }
}
