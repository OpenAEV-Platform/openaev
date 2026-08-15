package io.openaev.database.repository.autonomous;

import io.openaev.database.model.autonomous.AutonomousEvent;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Append-only, per-run-sequenced timeline store for autonomous runs. */
@Repository
public interface AutonomousEventRepository extends JpaRepository<AutonomousEvent, String> {

  List<AutonomousEvent> findByRunIdOrderBySequenceAsc(String runId);

  List<AutonomousEvent> findByRunIdAndSequenceGreaterThanOrderBySequenceAsc(
      String runId, long sequence);

  /** Highest sequence written for a run, 0 when the run has no events yet. */
  @Query("SELECT COALESCE(MAX(e.sequence), 0) FROM AutonomousEvent e WHERE e.runId = :runId")
  long findMaxSequence(@Param("runId") String runId);

  /**
   * Instant of the newest timeline entry for a run, or {@code null} when the run has no events yet.
   * This is the run's liveness clock for the idle/stall watchdog: every active decision cycle
   * appends events (including a ~45s "still working" heartbeat), so a newest-event age far past
   * that cadence means the orchestrator has gone silent (crashed, disconnected, or never resumed).
   */
  @Query("SELECT MAX(e.createdAt) FROM AutonomousEvent e WHERE e.runId = :runId")
  Instant findMaxCreatedAt(@Param("runId") String runId);

  /**
   * Takes a PostgreSQL transaction-scoped advisory lock keyed on {@code key} and returns once it is
   * held. {@code AutonomousEventService} calls this before its {@link #findMaxSequence} +1 insert
   * so two appenders writing to the same run serialise instead of both computing the same next
   * sequence. The lock auto-releases at commit/rollback and is cluster-wide (it holds across API
   * nodes), unlike a JVM lock; it complements the UNIQUE {@code (run_id, sequence)} index, which is
   * the hard backstop. Wrapped as {@code SELECT 1 FROM (...)} so the {@code void} lock function
   * maps to a plain scalar (the volatile function is still evaluated to produce the row). Must run
   * inside a transaction.
   */
  @Query(value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(:key)) AS locked", nativeQuery = true)
  Integer lockRunEventSequence(@Param("key") long key);

  /**
   * True when the run already carries a STATUS event whose title is one of {@code titles}. Used to
   * narrate a run's END (canceled / completed / timed out / failed) at most once per run life: an
   * operator Stop, the timeout watchdog and the read-path reconcile can all reach a run, and a
   * settled run can even be transiently resurrected by a late orchestrator write and re-settled by
   * the next reconcile - without this guard every one of those appended its own identical "Run
   * canceled" line, which is the reported duplicate + repeated terminal-message spam. A restart /
   * promote purges the timeline ({@link #deleteByRunId}), so the guard naturally resets for the
   * fresh run life.
   */
  @Query(
      "SELECT COUNT(e) > 0 FROM AutonomousEvent e WHERE e.runId = :runId "
          + "AND e.type = io.openaev.database.model.autonomous.AutonomousEventType.STATUS "
          + "AND e.title IN :titles")
  boolean existsTerminalStatusEvent(
      @Param("runId") String runId, @Param("titles") Collection<String> titles);

  /** Bulk-purges a run's timeline when the run (and its scenario/simulation) is deleted. */
  @Modifying
  @Query("DELETE FROM AutonomousEvent e WHERE e.runId = :runId")
  void deleteByRunId(@Param("runId") String runId);
}
