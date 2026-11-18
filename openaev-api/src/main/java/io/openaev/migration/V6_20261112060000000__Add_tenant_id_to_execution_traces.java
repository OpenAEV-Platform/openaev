package io.openaev.migration;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Gives {@code execution_traces} its own {@code tenant_id}, so that each trace is scoped on its own
 * column, with no join through its status's inject.
 *
 * <p>The tenant of a trace is, in this order:
 *
 * <ul>
 *   <li>the tenant of the inject of the trace's inject status;
 *   <li>the tenant of the inject of the trace's inject test status;
 *   <li>the default tenant when the parent gives no tenant (an inject status with a null {@code
 *       status_inject}); these traces are counted in a warning of the log.
 * </ul>
 *
 * <p>The column has no default: a row written without a tenant fails on {@code NOT NULL}, and none
 * lands silently in the default tenant.
 *
 * <p>The migration runs outside a wrapping transaction ({@link #canExecuteInTransaction()} returns
 * false), on a connection in autocommit mode, so the extra disk it needs is bounded by one range of
 * pages at a time, not by the size of the table:
 *
 * <ol>
 *   <li>the column is added, nullable and with no default (a catalog change only);
 *   <li>the backfill walks the table in physical order, one range of pages at a time ({@code ctid
 *       >= '(p,0)' AND ctid < '(p+N,0)'}), each range in its own committed transaction. Both sides
 *       of the update, the rows read and the rows written, are a {@code Tid Range Scan} on that
 *       range: each page is read once, in the order it sits on disk, not in the primary key's
 *       random order, which touches pages all over the table. The walk covers the table's size on
 *       disk when it starts ({@code pg_relation_size}). {@code N} is picked once, from the table's
 *       average row density ({@code pg_class.reltuples} and {@code relpages}), so that a range
 *       covers about {@value #TARGET_ROWS_PER_BATCH} rows; with no statistics yet (a table never
 *       analyzed, for instance right after a restore), a range is {@value
 *       #UNKNOWN_DENSITY_PAGES_PER_BATCH} pages;
 *   <li>no {@code VACUUM} runs during or after the walk. Autovacuum reclaims the dead row versions
 *       on its own schedule: each page is rewritten once, inside one range's own transaction, so
 *       the space of that range is reclaimable the moment it commits;
 *   <li>between ranges, the migration pauses a fixed, short duration, and, when the server reports
 *       archiving ({@code pg_stat_archiver}) or a replica ({@code pg_stat_replication}, {@code
 *       pg_replication_slots}, active slots only), it waits longer while the WAL not yet archived
 *       or replayed is above a bound, so that archiving and any replica catch up, and logs every
 *       minute it waits. A role that cannot read those views falls back to the fixed pause alone,
 *       logged once;
 *   <li>a running instance can insert rows with no tenant that the walk above does not reach (its
 *       page range is fixed before the insert happens), so a drain builds a temporary partial index
 *       of the rows with no tenant ({@code CONCURRENTLY}, so inserts go on), walks it in primary
 *       key order and fills them in committed, paced batches: each row is read once, whatever the
 *       size of that backlog;
 *   <li>one short transaction locks the table, fills the last rows with no tenant (served by the
 *       partial index), drops the partial index, then sets {@code NOT NULL}, adds the foreign key
 *       and creates the index.
 * </ol>
 *
 * <p>Every step is guarded ({@code IF NOT EXISTS}, {@code WHERE tenant_id IS NULL}), so a run
 * interrupted at any point, or a table already partly migrated, reaches the same final state when
 * the migration runs again.
 */
@Slf4j
@Component
public class V6_20261112060000000__Add_tenant_id_to_execution_traces extends BaseJavaMigration {

  public static final String TABLE = "execution_traces";

  static final int TARGET_ROWS_PER_BATCH = 10_000;
  static final long UNKNOWN_DENSITY_PAGES_PER_BATCH = 1_000;
  static final int DRAIN_BATCH_SIZE = 10_000;
  static final int PAUSE_MILLIS = 200;
  static final long WAL_LAG_BOUND_BYTES = 1_024L * 1_024 * 1_024;
  private static final long MEGABYTE = 1_024L * 1_024;
  private static final Duration WAIT_LOG_INTERVAL = Duration.ofMinutes(1);
  static final String FOREIGN_KEY = "fk_" + TABLE + "_tenant_id";
  static final String INDEX = "idx_" + TABLE + "_tenant_id";
  static final String PENDING_INDEX = "idx_" + TABLE + "_tenant_id_pending";

  /**
   * Fills the rows of {@code %1$s} (a predicate on the alias {@code x}) with the tenant of their
   * parent. Returns, for the rows it wrote, their count and how many had no parent tenant. {@code
   * %2$s} is the same predicate on the updated alias {@code t}: with the {@code ctid} join alone,
   * the planner reads the whole table on the {@code t} side of every batch.
   */
  private static final String BACKFILL =
      "WITH written AS ("
          + " UPDATE "
          + TABLE
          + " t SET tenant_id = COALESCE(b.parent_tenant, ?) FROM ("
          + " SELECT x.ctid AS trace_ctid,"
          + " COALESCE(inject_of_status.tenant_id, inject_of_test_status.tenant_id) AS parent_tenant"
          + " FROM "
          + TABLE
          + " x"
          + " LEFT JOIN injects_statuses status ON status.status_id = x.execution_inject_status_id"
          + " LEFT JOIN injects inject_of_status ON inject_of_status.inject_id = status.status_inject"
          + " LEFT JOIN injects_tests_statuses test_status"
          + " ON test_status.status_id = x.execution_inject_test_status_id"
          + " LEFT JOIN injects inject_of_test_status"
          + " ON inject_of_test_status.inject_id = test_status.status_inject"
          + " WHERE x.tenant_id IS NULL AND %1$s) b"
          + " WHERE t.ctid = b.trace_ctid AND %2$s"
          + " RETURNING b.parent_tenant IS NULL AS orphan)"
          + " SELECT count(*) AS written, count(*) FILTER (WHERE orphan) AS orphans"
          + " FROM written";

  /** One range of pages, in physical order: a {@code Tid Range Scan}, never a table-wide sort. */
  static final String RANGE_UPDATE =
      BACKFILL.formatted(
          "x.ctid >= ?::tid AND x.ctid < ?::tid", "t.ctid >= ?::tid AND t.ctid < ?::tid");

  private static final String BATCH_UPDATE =
      BACKFILL.formatted("x.execution_trace_id = ANY(?)", "t.execution_trace_id = ANY(?)");
  private static final String FINAL_UPDATE = BACKFILL.formatted("true", "true");

  /** Picks the next rows with no tenant in primary key order, served by the partial index. */
  static final String NEXT_PENDING_IDS =
      "SELECT execution_trace_id FROM "
          + TABLE
          + " WHERE tenant_id IS NULL AND execution_trace_id > ?"
          + " ORDER BY execution_trace_id LIMIT ?";

  /**
   * Returns how much WAL is not yet archived or replayed: the largest of the archiver's lag, every
   * streaming replica's lag and every active replication slot's lag, in bytes. {@code NULL} (no
   * archiving, no replica, no active slot) reads as zero. An inactive slot is left out: nothing
   * consumes it while the migration runs, so a wait on it never ends. The archiver's lag is the
   * distance between the current WAL segment and the last archived one, both read from their file
   * names (timeline, log, segment, 8 hex digits each), which every supported server version parses;
   * a {@code .history} or {@code .partial} file name is skipped.
   */
  private static final String WAL_LAG_QUERY =
      "SELECT MAX(lag) FROM ("
          + " SELECT pg_wal_lsn_diff(pg_current_wal_lsn(), replay_lsn) AS lag"
          + " FROM pg_stat_replication"
          + " UNION ALL"
          + " SELECT pg_wal_lsn_diff(pg_current_wal_lsn(), restart_lsn) AS lag"
          + " FROM pg_replication_slots WHERE active AND restart_lsn IS NOT NULL"
          + " UNION ALL"
          + " SELECT ((('x' || substr(cur, 9, 8))::bit(32)::bigint"
          + " - ('x' || substr(done, 9, 8))::bit(32)::bigint) * (4294967296 / seg)"
          + " + ('x' || substr(cur, 17, 8))::bit(32)::bigint"
          + " - ('x' || substr(done, 17, 8))::bit(32)::bigint) * seg AS lag"
          + " FROM (SELECT pg_walfile_name(pg_current_wal_lsn()) AS cur,"
          + " last_archived_wal AS done,"
          + " pg_size_bytes(current_setting('wal_segment_size')) AS seg"
          + " FROM pg_stat_archiver) archiver"
          + " WHERE done ~ '^[0-9A-F]{24}$') lags";

  private final int drainBatchSize;
  private final Long pagesPerBatchOverride;
  private final int pauseMillis;
  private final long walLagBoundBytes;

  public V6_20261112060000000__Add_tenant_id_to_execution_traces() {
    this(DRAIN_BATCH_SIZE, null, PAUSE_MILLIS, WAL_LAG_BOUND_BYTES);
  }

  V6_20261112060000000__Add_tenant_id_to_execution_traces(
      int drainBatchSize, Long pagesPerBatchOverride, int pauseMillis, long walLagBoundBytes) {
    this.drainBatchSize = drainBatchSize;
    this.pagesPerBatchOverride = pagesPerBatchOverride;
    this.pauseMillis = pauseMillis;
    this.walLagBoundBytes = walLagBoundBytes;
  }

  /**
   * Pages per batch, from the table's average row density: the pages that cover about {@code
   * targetRowsPerBatch} rows. An estimate of 0 or below (no {@code ANALYZE} yet) gives {@link
   * #UNKNOWN_DENSITY_PAGES_PER_BATCH} pages, about 8 MB of table.
   */
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

  /** Runs every step on {@code connection}, which is put in autocommit mode. */
  void migrate(Connection connection) throws SQLException {
    connection.setAutoCommit(true);
    // Table and column names below are constants of this class, never input.
    try (Statement statement = connection.createStatement()) {
      statement.execute(
          "ALTER TABLE " + TABLE + " ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(255)");
    }
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
    AtomicBoolean walGuardAvailable = new AtomicBoolean(true);
    long orphans = backfillByPageRanges(connection, size.pages(), pagesPerBatch, walGuardAvailable);
    orphans += drain(connection, walGuardAvailable);
    orphans += finish(connection);
    if (orphans > 0) {
      log.warn(
          "{} row(s) of {} have no parent to take a tenant from and were attributed to the"
              + " default tenant",
          orphans,
          TABLE);
    }
  }

  /**
   * Walks the table one range of pages at a time, in physical order, each range in its own
   * transaction. Rows outside every range at the time it is read (inserted after this method
   * measured the table, or missed for any other reason) are left to {@link #drain}. Returns the
   * orphans.
   */
  private long backfillByPageRanges(
      Connection connection, long totalPages, long pagesPerBatch, AtomicBoolean walGuardAvailable)
      throws SQLException {
    long written = 0;
    long orphans = 0;
    long batches = 0;
    long startedAt = System.nanoTime();
    try (PreparedStatement update = connection.prepareStatement(RANGE_UPDATE)) {
      for (long page = 0; page < totalPages; page += pagesPerBatch) {
        update.setString(1, DEFAULT_TENANT_UUID);
        update.setString(2, tid(page));
        update.setString(3, tid(page + pagesPerBatch));
        update.setString(4, tid(page));
        update.setString(5, tid(page + pagesPerBatch));
        try (ResultSet result = update.executeQuery()) {
          result.next();
          written += result.getLong("written");
          orphans += result.getLong("orphans");
        }
        batches++;
        pace(connection, walGuardAvailable);
      }
    }
    log.info(
        "{}: {} row(s) filled over {} range(s) of {} page(s) each, {} s",
        TABLE,
        written,
        batches,
        pagesPerBatch,
        Duration.ofNanos(System.nanoTime() - startedAt).toSeconds());
    return orphans;
  }

  private static String tid(long page) {
    return "(" + page + ",0)";
  }

  /**
   * Fills, in committed, paced batches, the rows with no tenant left after the walk: those its
   * fixed page ranges did not cover. A temporary partial index holds only those rows, so each batch
   * reads its rows and no other. Returns the orphans.
   */
  private long drain(Connection connection, AtomicBoolean walGuardAvailable) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      // A leftover of an interrupted run can be invalid: rebuild it.
      statement.execute("DROP INDEX CONCURRENTLY IF EXISTS " + PENDING_INDEX);
      statement.execute(
          "CREATE INDEX CONCURRENTLY "
              + PENDING_INDEX
              + " ON "
              + TABLE
              + " (execution_trace_id) WHERE tenant_id IS NULL");
    }
    long drained = 0;
    long orphans = 0;
    String lastId = "";
    try (PreparedStatement pick = connection.prepareStatement(NEXT_PENDING_IDS);
        PreparedStatement update = connection.prepareStatement(BATCH_UPDATE)) {
      while (true) {
        pick.setString(1, lastId);
        pick.setInt(2, drainBatchSize);
        List<String> ids = new ArrayList<>(drainBatchSize);
        try (ResultSet result = pick.executeQuery()) {
          while (result.next()) {
            ids.add(result.getString(1));
          }
        }
        if (ids.isEmpty()) {
          break;
        }
        Array idArray = connection.createArrayOf("varchar", ids.toArray());
        update.setString(1, DEFAULT_TENANT_UUID);
        update.setArray(2, idArray);
        update.setArray(3, idArray);
        orphans += orphansOf(update);
        lastId = ids.getLast();
        drained += ids.size();
        pace(connection, walGuardAvailable);
      }
    }
    log.info("{}: {} row(s) outside the walked ranges filled in batches", TABLE, drained);
    return orphans;
  }

  /**
   * One short transaction: with the table locked, fills the rows left with no tenant, then
   * completes the schema. Returns the orphans of those rows.
   */
  private long finish(Connection connection) throws SQLException {
    connection.setAutoCommit(false);
    try (Statement statement = connection.createStatement()) {
      statement.execute("LOCK TABLE " + TABLE + " IN ACCESS EXCLUSIVE MODE");
      long orphans;
      try (PreparedStatement remaining = connection.prepareStatement(FINAL_UPDATE)) {
        remaining.setString(1, DEFAULT_TENANT_UUID);
        orphans = orphansOf(remaining);
      }
      statement.execute("DROP INDEX IF EXISTS " + PENDING_INDEX);
      statement.execute("ALTER TABLE " + TABLE + " ALTER COLUMN tenant_id SET NOT NULL");
      statement.execute(
          "DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = '"
              + FOREIGN_KEY
              + "' AND conrelid = '"
              + TABLE
              + "'::regclass) THEN ALTER TABLE "
              + TABLE
              + " ADD CONSTRAINT "
              + FOREIGN_KEY
              + " FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id)"
              + " ON DELETE CASCADE; END IF; END $$");
      statement.execute("CREATE INDEX IF NOT EXISTS " + INDEX + " ON " + TABLE + "(tenant_id)");
      connection.commit();
      return orphans;
    } catch (SQLException | RuntimeException e) {
      connection.rollback();
      throw e;
    } finally {
      connection.setAutoCommit(true);
    }
  }

  /**
   * The table's size: {@code pages}, its exact page count on disk, which bounds the walk, and the
   * catalog's estimates ({@code reltuples}, {@code relpages}, both as of the last {@code ANALYZE}
   * or {@code VACUUM}), which give its row density only.
   */
  private record TableSize(long pages, long estimatedRows, long estimatedPages) {}

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

  /** Runs a fill statement and returns how many of the rows it wrote had no parent tenant. */
  private static long orphansOf(PreparedStatement statement) throws SQLException {
    try (ResultSet result = statement.executeQuery()) {
      result.next();
      return result.getLong("orphans");
    }
  }

  /**
   * Sleeps the fixed pause, then, when the WAL-lag views are readable, waits longer while the WAL
   * not yet archived or replayed is above {@link #walLagBoundBytes}. A role that cannot read those
   * views (a permission error, or a server without them) disables this wait for the rest of the
   * run, logs once, and the fixed pause alone paces every later batch.
   */
  private void pace(Connection connection, AtomicBoolean walGuardAvailable) throws SQLException {
    sleep(pauseMillis);
    if (!walGuardAvailable.get()) {
      return;
    }
    try {
      long lag = walLagBytes(connection);
      long nextLogAt = System.nanoTime();
      while (lag > walLagBoundBytes) {
        if (System.nanoTime() - nextLogAt >= 0) {
          log.info(
              "{}: waiting for WAL archiving or replicas, {} MB behind (bound {} MB)",
              TABLE,
              lag / MEGABYTE,
              walLagBoundBytes / MEGABYTE);
          nextLogAt = System.nanoTime() + WAIT_LOG_INTERVAL.toNanos();
        }
        sleep(Math.max(pauseMillis, 1_000));
        lag = walLagBytes(connection);
      }
    } catch (SQLException e) {
      walGuardAvailable.set(false);
      log.warn(
          "{}: cannot read pg_stat_archiver / pg_stat_replication / pg_replication_slots,"
              + " pacing on the fixed pause alone: {}",
          TABLE,
          e.getMessage());
    }
  }

  static long walLagBytes(Connection connection) throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(WAL_LAG_QUERY)) {
      result.next();
      long lag = result.getLong(1);
      return result.wasNull() ? 0 : lag;
    }
  }

  private static void sleep(int millis) {
    if (millis <= 0) {
      return;
    }
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      // Stop here: the next start resumes from the rows left with no tenant.
      Thread.currentThread().interrupt();
      throw new IllegalStateException(TABLE + " tenant backfill interrupted", e);
    }
  }

  /* ROLLBACK
   ALTER TABLE execution_traces DROP COLUMN IF EXISTS tenant_id;
   -- Dropping the column drops its foreign key and index. Only valid together with a revert of the
   -- entity mapping, which writes the column on every insert.
  */
}
