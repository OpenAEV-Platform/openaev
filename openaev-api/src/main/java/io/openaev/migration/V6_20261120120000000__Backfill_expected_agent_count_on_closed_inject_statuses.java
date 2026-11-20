package io.openaev.migration;

import io.openaev.database.model.ExecutionTraceStatus;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Fills {@code status_expected_agent_count} on the closed inject statuses that have none: the
 * injects launched before {@code V5_22} added the column. The status a reader sees compares that
 * count with the agents whose COMPLETE trace the reader's clearance shows ({@code
 * InjectStatusUtils.visibleStatus}); without it, the stored verdict is shown, including the outcome
 * on agents the reader may not see.
 *
 * <p>The count is the distinct agents with a COMPLETE trace on the status: the stored verdict is
 * computed from those traces, so an agent without one took no part in it and hides nothing. An
 * inject dispatched to no agent gets 0, which the reader's status treats like no count. In-flight
 * statuses keep no count: their agents have not all completed yet, and the close fills it ({@code
 * InjectStatusService.updateFinalInjectStatus}). The migration runs without the marking filter, so
 * it counts every agent.
 *
 * <p>Unless traces were deleted before the upgrade: an agent deletion cascades to its traces, and
 * the retention job purged every old trace until it kept the COMPLETE ones. A count of the agents
 * left would then match a reader who sees them all, and show that reader the stored verdict,
 * including the deleted agents' outcome. So when the evidence is incomplete, the count is the
 * agents left plus one: no reader sees that many, and each gets the status recomputed from the
 * traces they see, or {@code NOT_VISIBLE} with none, as after an agent deletion on a counted
 * status. Incomplete is either of:
 *
 * <ul>
 *   <li>COMPLETE traces are left, and their verdict ({@code InjectStatusUtils.computeStatus}) is
 *       not the stored one: a deleted agent changed it. When they agree, the stored verdict tells
 *       nothing the traces left do not;
 *   <li>no COMPLETE trace is left on an inject sent to assets ({@code tracking_sent_date} set, an
 *       asset or asset group target): purged. A status set by hand has no sent date, and an inject
 *       sent only to teams has no asset a clearance could hide; both keep 0 and their stored
 *       status.
 * </ul>
 *
 * <p>{@code injects_statuses} grows with the use of the platform, so this follows ADR-023: a walk
 * in physical order, one committed transaction per range of pages, a pause after each, row lock
 * requests under a short {@code lock_timeout} with retry, and a final pass for the rows a
 * concurrent update moved behind the walk. Every step is guarded by {@code
 * status_expected_agent_count IS NULL}, so an interrupted run finishes on the next start. It writes
 * one integer on the legacy closed rows only, so its WAL is bounded by those rows and does not need
 * the WAL-lag pacing of a whole-table rewrite.
 */
@Slf4j
@Component
public class V6_20261120120000000__Backfill_expected_agent_count_on_closed_inject_statuses
    extends BaseJavaMigration {

  public static final String TABLE = "injects_statuses";

  static final int TARGET_ROWS_PER_BATCH = 10_000;
  static final long UNKNOWN_DENSITY_PAGES_PER_BATCH = 100;
  static final int PAUSE_MILLIS = 200;
  static final String LOCK_TIMEOUT = "5s";
  static final int LOCK_RETRY_MILLIS = 5_000;
  private static final String LOCK_NOT_AVAILABLE = "55P03";
  private static final Duration LOG_INTERVAL = Duration.ofMinutes(1);

  /**
   * The verdict of the COMPLETE traces {@code t}, as {@code InjectStatusUtils.computeStatus} gives
   * it, by kind: {@code SUCCESS} (EXECUTED), {@code MIXED} (PARTIAL) or {@code FAILURE} (ERROR,
   * also with no counted trace). The trace statuses come from {@link ExecutionTraceStatus}, so the
   * two cannot drift apart.
   */
  private static final String VERDICT =
      "CASE WHEN bool_or(t.execution_status IN (%1$s)) AND bool_or(t.execution_status IN (%2$s))"
              .formatted(
                  sqlList(ExecutionTraceStatus.SUCCESS_STATUSES),
                  sqlList(ExecutionTraceStatus.ERROR_STATUSES))
          + " THEN 'MIXED' WHEN bool_or(t.execution_status IN (%s)) THEN 'SUCCESS'"
              .formatted(sqlList(ExecutionTraceStatus.SUCCESS_STATUSES))
          + " ELSE 'FAILURE' END";

  /**
   * Whether the stored status {@code s} agrees with the verdict {@code e.verdict} of the traces
   * left. The legacy MAYBE_PREVENTED is a failure, MAYBE_PARTIAL_PREVENTED a failure or a mix.
   */
  private static final String AGREES =
      "CASE s.status_name WHEN 'EXECUTED' THEN e.verdict = 'SUCCESS'"
          + " WHEN 'PARTIAL' THEN e.verdict = 'MIXED'"
          + " WHEN 'MAYBE_PARTIAL_PREVENTED' THEN e.verdict <> 'SUCCESS'"
          + " ELSE e.verdict = 'FAILURE' END";

  /** No COMPLETE trace left on a status sent to an asset or an asset group. */
  private static final String PURGED =
      "e.traces = 0 AND s.tracking_sent_date IS NOT NULL"
          + " AND (EXISTS (SELECT 1 FROM injects_assets a WHERE a.inject_id = s.status_inject)"
          + " OR EXISTS (SELECT 1 FROM injects_asset_groups g WHERE g.inject_id = s.status_inject))";

  /**
   * Fills the rows {@code %s} selects; returns how many it wrote and how many of them are
   * agent-backed or lost evidence. The action is a literal so the traces are read from the partial
   * COMPLETE index of {@code V6_20261120110000000}, which includes their status and agent.
   */
  private static final String FILL =
      "WITH filled AS ("
          + " UPDATE "
          + TABLE
          + " s SET status_expected_agent_count = ("
          + " SELECT e.agents + CASE WHEN "
          + PURGED
          + " OR (e.traces > 0 AND NOT ("
          + AGREES
          + ")) THEN 1 ELSE 0 END"
          + " FROM (SELECT count(DISTINCT t.execution_agent_id) AS agents, count(*) AS traces, "
          + VERDICT
          + " AS verdict FROM execution_traces t"
          + " WHERE t.execution_inject_status_id = s.status_id"
          + " AND t.execution_action = 'COMPLETE') e)"
          + " WHERE %s AND s.status_expected_agent_count IS NULL"
          + " AND s.status_name IN"
          + " ('EXECUTED', 'PARTIAL', 'ERROR', 'MAYBE_PREVENTED', 'MAYBE_PARTIAL_PREVENTED')"
          + " RETURNING s.status_expected_agent_count AS agents)"
          + " SELECT count(*) AS written, count(*) FILTER (WHERE agents > 0) AS agent_backed"
          + " FROM filled";

  static final String RANGE_FILL = FILL.formatted("s.ctid >= ?::tid AND s.ctid < ?::tid");
  private static final String FINAL_FILL = FILL.formatted("true");

  private final Long pagesPerBatchOverride;
  private final int pauseMillis;
  private final String lockTimeout;
  private final int lockRetryMillis;

  public V6_20261120120000000__Backfill_expected_agent_count_on_closed_inject_statuses() {
    this(null, PAUSE_MILLIS, LOCK_TIMEOUT, LOCK_RETRY_MILLIS);
  }

  V6_20261120120000000__Backfill_expected_agent_count_on_closed_inject_statuses(
      Long pagesPerBatchOverride, int pauseMillis, String lockTimeout, int lockRetryMillis) {
    this.pagesPerBatchOverride = pagesPerBatchOverride;
    this.pauseMillis = pauseMillis;
    this.lockTimeout = lockTimeout;
    this.lockRetryMillis = lockRetryMillis;
  }

  /** {@link #UNKNOWN_DENSITY_PAGES_PER_BATCH} when the table has no statistics yet. */
  static long pagesPerBatch(long estimatedRows, long estimatedPages, int targetRowsPerBatch) {
    if (estimatedRows <= 0 || estimatedPages <= 0) {
      return UNKNOWN_DENSITY_PAGES_PER_BATCH;
    }
    double rowsPerPage = (double) estimatedRows / estimatedPages;
    long pages = Math.round(targetRowsPerBatch / Math.max(rowsPerPage, 1.0));
    return Math.max(1, pages);
  }

  @Override
  public boolean canExecuteInTransaction() {
    return false;
  }

  @Override
  public void migrate(Context context) throws Exception {
    migrate(context.getConnection());
  }

  /** Puts {@code connection} in autocommit mode: each range commits on its own. */
  void migrate(Connection connection) throws SQLException {
    connection.setAutoCommit(true);
    try (Statement statement = connection.createStatement()) {
      // A constant of this class, never input.
      statement.execute("SET lock_timeout = '" + lockTimeout + "'");
    }
    try {
      Fill walked = walk(connection);
      Fill rest;
      try (PreparedStatement update = connection.prepareStatement(FINAL_FILL)) {
        rest = fillWithLockRetry(update, "the final pass");
      }
      log.info(
          "{}: expected agent count filled on {} closed status(es), {} of them dispatched to"
              + " agents or with lost evidence",
          TABLE,
          walked.written() + rest.written(),
          walked.agentBacked() + rest.agentBacked());
    } finally {
      try (Statement statement = connection.createStatement()) {
        statement.execute("RESET lock_timeout");
      }
    }
  }

  /** Rows written by a fill, and how many of them have at least one agent. */
  record Fill(long written, long agentBacked) {
    Fill plus(Fill other) {
      return new Fill(written + other.written, agentBacked + other.agentBacked);
    }
  }

  private Fill walk(Connection connection) throws SQLException {
    TableSize size = tableSize(connection);
    long pagesPerBatch =
        Math.max(
            1,
            pagesPerBatchOverride != null
                ? pagesPerBatchOverride
                : pagesPerBatch(
                    size.estimatedRows(), size.estimatedPages(), TARGET_ROWS_PER_BATCH));
    log.info(
        "{}: {} page(s) on disk, about {} row(s), {} page(s) per batch",
        TABLE,
        size.pages(),
        size.estimatedRows(),
        pagesPerBatch);
    Fill total = new Fill(0, 0);
    long startedAt = System.nanoTime();
    long nextLogAt = startedAt + LOG_INTERVAL.toNanos();
    try (PreparedStatement update = connection.prepareStatement(RANGE_FILL)) {
      for (long page = 0; page < size.pages(); page += pagesPerBatch) {
        update.setString(1, tid(page));
        update.setString(2, tid(page + pagesPerBatch));
        total = total.plus(fillWithLockRetry(update, "page " + page));
        if (System.nanoTime() - nextLogAt >= 0) {
          log.info(
              "{}: page {} of {} walked, {} row(s) filled",
              TABLE,
              Math.min(page + pagesPerBatch, size.pages()),
              size.pages(),
              total.written());
          nextLogAt = System.nanoTime() + LOG_INTERVAL.toNanos();
        }
        sleep(pauseMillis);
      }
    }
    log.info(
        "{}: {} row(s) filled by the walk, {} s",
        TABLE,
        total.written(),
        Duration.ofNanos(System.nanoTime() - startedAt).toSeconds());
    return total;
  }

  /**
   * Runs {@code update} again each time it times out on a row lock another transaction holds: a
   * waiting lock request makes the next writer of that row wait behind it.
   */
  private Fill fillWithLockRetry(PreparedStatement update, String what) throws SQLException {
    while (true) {
      try (ResultSet result = update.executeQuery()) {
        result.next();
        return new Fill(result.getLong("written"), result.getLong("agent_backed"));
      } catch (SQLException e) {
        if (!LOCK_NOT_AVAILABLE.equals(e.getSQLState())) {
          throw e;
        }
        log.info("{}: {} waits for a row lock another transaction holds, retrying", TABLE, what);
        sleep(lockRetryMillis);
      }
    }
  }

  private static String tid(long page) {
    return "(" + page + ",0)";
  }

  /**
   * {@code pages} is the size on disk and bounds the walk; the estimates only give the row density.
   */
  private record TableSize(long pages, long estimatedRows, long estimatedPages) {}

  /** {@code 'A', 'B'}: enum names, never input. Sorted, so the statement is always the same. */
  private static String sqlList(Set<ExecutionTraceStatus> statuses) {
    return statuses.stream()
        .map(status -> "'" + status.name() + "'")
        .sorted()
        .collect(Collectors.joining(", "));
  }

  private static TableSize tableSize(Connection connection) throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet result =
            statement.executeQuery(
                "SELECT pg_relation_size(oid) / current_setting('block_size')::bigint,"
                    + " reltuples::bigint, relpages::bigint FROM pg_class WHERE oid = '"
                    + TABLE
                    + "'::regclass")) {
      result.next();
      return new TableSize(result.getLong(1), result.getLong(2), result.getLong(3));
    }
  }

  private static void sleep(int millis) {
    if (millis <= 0) {
      return;
    }
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      // Stop here: the next start resumes from the rows left with no count.
      Thread.currentThread().interrupt();
      throw new IllegalStateException(TABLE + " expected agent count backfill interrupted", e);
    }
  }

  /* ROLLBACK
   No rollback: the count is what the platform writes at launch since V5_22. Clearing it would
   show the stored verdict of the legacy injects to every reader again.
  */
}
