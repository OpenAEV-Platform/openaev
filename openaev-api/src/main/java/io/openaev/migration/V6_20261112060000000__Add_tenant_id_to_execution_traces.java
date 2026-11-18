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
 * false), on a connection in autocommit mode, so every batch commits on its own and the WAL it
 * writes stays close to the size of the table:
 *
 * <ol>
 *   <li>the column is added, nullable and with no default (a catalog change only). Every statement
 *       that takes a lock other sessions wait on runs under a short {@code lock_timeout} and
 *       retries after a pause: a lock request that waits behind a long transaction makes every
 *       later query on the table wait behind it;
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
 *   <li>no {@code VACUUM} runs during or after the walk. Each page is rewritten once, inside one
 *       range's own transaction, and autovacuum reclaims the dead row versions of a range once it
 *       commits, on its own schedule. Until it does, the table and its indexes grow: the disk needs
 *       free space up to the size of the table, less when autovacuum runs as fast as the walk;
 *   <li>between ranges, the migration pauses a fixed, short duration, and, when the server reports
 *       archiving ({@code pg_stat_archiver}) or a replica ({@code pg_stat_replication}, {@code
 *       pg_replication_slots}, active slots only), it waits longer while the WAL not yet archived
 *       or replayed is above a bound, so that archiving and any replica catch up, and logs every
 *       minute it waits. A role that cannot read those views falls back to the fixed pause alone,
 *       logged once. An inactive slot is only logged at the start, with the WAL it retains: nothing
 *       consumes it during the run, so a wait on it never ends;
 *   <li>a running instance can insert rows with no tenant that the walk above does not reach (its
 *       page range is fixed before the insert happens), so a drain builds a temporary partial index
 *       of the rows with no tenant ({@code CONCURRENTLY}, so inserts go on), walks it in primary
 *       key order and fills them in committed, paced batches. The ids are random, so a row inserted
 *       during a pass can land behind its cursor: passes repeat until one finds less than a batch;
 *   <li>the index on {@code tenant_id} is built {@code CONCURRENTLY}, so writes go on;
 *   <li>one transaction locks the table, fills the last rows with no tenant (served by the partial
 *       index), drops the partial index, sets {@code NOT NULL} and adds the foreign key {@code NOT
 *       VALID}. {@code SET NOT NULL} reads the whole table under the lock; it writes no row;
 *   <li>the foreign key is validated after that commit, under a lock that lets reads and writes go
 *       on.
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
  private static final Duration LOG_INTERVAL = Duration.ofMinutes(1);
  private static final String LOCK_TIMEOUT = "5s";
  private static final int LOCK_RETRY_MILLIS = 5_000;
  private static final String LOCK_NOT_AVAILABLE = "55P03";
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
  private static final String FINAL_UPDATE = BACKFILL.formatted("true", "t.tenant_id IS NULL");

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
   * consumes it while the migration runs, so a wait on it never ends. With {@code archive_mode}
   * off, the archiver is left out too: its last archived file name outlives the setting, and the
   * distance to it grows without end. With archiving on and nothing archived yet, the distance is
   * counted from the WAL file current when the run started (the parameter). The archiver's lag is
   * the distance between the current WAL segment and the last archived one, both read from their
   * file names (timeline, log, segment, 8 hex digits each), which every supported server version
   * parses; a {@code .history} or {@code .partial} file name is skipped.
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
          + " COALESCE(last_archived_wal, ?) AS done,"
          + " pg_size_bytes(current_setting('wal_segment_size')) AS seg"
          + " FROM pg_stat_archiver WHERE current_setting('archive_mode') <> 'off') archiver"
          + " WHERE done ~ '^[0-9A-F]{24}$') lags";

  /** The WAL each inactive replication slot retains, in bytes. */
  private static final String INACTIVE_SLOTS_QUERY =
      "SELECT slot_name, pg_wal_lsn_diff(pg_current_wal_lsn(), restart_lsn)"
          + " FROM pg_replication_slots WHERE NOT active AND restart_lsn IS NOT NULL";

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
    withLockRetry(
        connection,
        "adding the column",
        () -> {
          try (Statement statement = connection.createStatement()) {
            statement.execute(
                "ALTER TABLE " + TABLE + " ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(255)");
          }
          return null;
        });
    logInactiveSlots(connection);
    WalGuard walGuard = new WalGuard(currentWalFile(connection));
    long orphans = walk(connection, walGuard).orphans();
    orphans += drain(connection, walGuard);
    buildTenantIndex(connection);
    orphans += withLockRetry(connection, "locking the table", () -> finish(connection));
    withLockRetry(
        connection,
        "validating the foreign key",
        () -> {
          try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + TABLE + " VALIDATE CONSTRAINT " + FOREIGN_KEY);
          }
          return null;
        });
    if (orphans > 0) {
      log.warn(
          "{} row(s) of {} have no parent to take a tenant from and were attributed to the"
              + " default tenant",
          orphans,
          TABLE);
    }
  }

  /** Rows written by a fill, and how many of them had no parent tenant. */
  record Fill(long written, long orphans) {}

  /**
   * Measures the table, then walks it one range of pages at a time, in physical order, each range
   * in its own transaction. Rows outside every range at the time it is read (inserted after the
   * table is measured, or moved by a concurrent update) are left to {@link #drain}.
   */
  Fill walk(Connection connection, WalGuard walGuard) throws SQLException {
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
    return backfillByPageRanges(connection, size.pages(), pagesPerBatch, walGuard);
  }

  private Fill backfillByPageRanges(
      Connection connection, long totalPages, long pagesPerBatch, WalGuard walGuard)
      throws SQLException {
    long written = 0;
    long orphans = 0;
    long batches = 0;
    long startedAt = System.nanoTime();
    long nextLogAt = startedAt + LOG_INTERVAL.toNanos();
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
        if (System.nanoTime() - nextLogAt >= 0) {
          log.info(
              "{}: page {} of {} walked, {} row(s) filled",
              TABLE,
              Math.min(page + pagesPerBatch, totalPages),
              totalPages,
              written);
          nextLogAt = System.nanoTime() + LOG_INTERVAL.toNanos();
        }
        pace(connection, walGuard);
      }
    }
    log.info(
        "{}: {} row(s) filled over {} range(s) of {} page(s) each, {} s",
        TABLE,
        written,
        batches,
        pagesPerBatch,
        Duration.ofNanos(System.nanoTime() - startedAt).toSeconds());
    return new Fill(written, orphans);
  }

  private static String tid(long page) {
    return "(" + page + ",0)";
  }

  /**
   * Fills, in committed, paced batches, the rows with no tenant left after the walk: those its
   * fixed page ranges did not cover. A temporary partial index holds only those rows, so each batch
   * reads its rows and no other. A pass walks that index in primary key order; passes repeat until
   * one finds less than a batch, and the locked step fills what is left. Returns the orphans.
   */
  private long drain(Connection connection, WalGuard walGuard) throws SQLException {
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
    try (PreparedStatement pick = connection.prepareStatement(NEXT_PENDING_IDS);
        PreparedStatement update = connection.prepareStatement(BATCH_UPDATE)) {
      Fill pass;
      do {
        pass = drainPass(connection, pick, update, walGuard);
        drained += pass.written();
        orphans += pass.orphans();
      } while (pass.written() >= drainBatchSize);
    }
    log.info("{}: {} row(s) outside the walked ranges filled in batches", TABLE, drained);
    return orphans;
  }

  private Fill drainPass(
      Connection connection, PreparedStatement pick, PreparedStatement update, WalGuard walGuard)
      throws SQLException {
    long drained = 0;
    long orphans = 0;
    String lastId = "";
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
        return new Fill(drained, orphans);
      }
      Array idArray = connection.createArrayOf("varchar", ids.toArray());
      update.setString(1, DEFAULT_TENANT_UUID);
      update.setArray(2, idArray);
      update.setArray(3, idArray);
      orphans += orphansOf(update);
      lastId = ids.getLast();
      drained += ids.size();
      pace(connection, walGuard);
    }
  }

  /**
   * Builds the index on {@code tenant_id} without blocking writes. A build interrupted by an
   * earlier run leaves an invalid index, dropped first.
   */
  private static void buildTenantIndex(Connection connection) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      boolean invalid;
      try (ResultSet result =
          statement.executeQuery(
              "SELECT NOT indisvalid FROM pg_index WHERE indexrelid = to_regclass('"
                  + INDEX
                  + "')")) {
        invalid = result.next() && result.getBoolean(1);
      }
      if (invalid) {
        statement.execute("DROP INDEX CONCURRENTLY IF EXISTS " + INDEX);
      }
      statement.execute(
          "CREATE INDEX CONCURRENTLY IF NOT EXISTS " + INDEX + " ON " + TABLE + " (tenant_id)");
    }
  }

  /**
   * One transaction: with the table locked, fills the rows left with no tenant, sets {@code NOT
   * NULL} and adds the foreign key {@code NOT VALID}. Returns the orphans of those rows.
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
              + " ON DELETE CASCADE NOT VALID; END IF; END $$");
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

  /** A step that runs SQL and returns a value. */
  private interface SqlStep<T> {
    T run() throws SQLException;
  }

  /**
   * Runs {@code step} under a short {@code lock_timeout}, and again after a pause each time it
   * times out on a lock another transaction holds: a lock request that waits makes every later
   * query on the table wait behind it.
   */
  private static <T> T withLockRetry(Connection connection, String what, SqlStep<T> step)
      throws SQLException {
    while (true) {
      try (Statement statement = connection.createStatement()) {
        statement.execute("SET lock_timeout = '" + LOCK_TIMEOUT + "'");
      }
      try {
        return step.run();
      } catch (SQLException e) {
        if (!LOCK_NOT_AVAILABLE.equals(e.getSQLState())) {
          throw e;
        }
        log.info("{}: {} waits for a lock another transaction holds, retrying", TABLE, what);
      } finally {
        try (Statement statement = connection.createStatement()) {
          statement.execute("RESET lock_timeout");
        }
      }
      sleep(LOCK_RETRY_MILLIS);
    }
  }

  /**
   * Logs each inactive replication slot and the WAL it retains: the migration does not wait for a
   * slot nothing consumes, and its writes add to that WAL until the slot is consumed or dropped.
   */
  private static void logInactiveSlots(Connection connection) {
    try (Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(INACTIVE_SLOTS_QUERY)) {
      while (result.next()) {
        log.warn(
            "{}: inactive replication slot {} retains {} MB of WAL; the backfill does not wait"
                + " for it, and its writes add to that WAL",
            TABLE,
            result.getString(1),
            result.getLong(2) / MEGABYTE);
      }
    } catch (SQLException e) {
      log.warn("{}: cannot read pg_replication_slots: {}", TABLE, e.getMessage());
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
  private void pace(Connection connection, WalGuard walGuard) {
    sleep(pauseMillis);
    if (!walGuard.available) {
      return;
    }
    try {
      long lag = walLagBytes(connection, walGuard.startWalFile);
      long nextLogAt = System.nanoTime();
      while (lag > walLagBoundBytes) {
        if (System.nanoTime() - nextLogAt >= 0) {
          log.info(
              "{}: waiting for WAL archiving or replicas, {} MB behind (bound {} MB)",
              TABLE,
              lag / MEGABYTE,
              walLagBoundBytes / MEGABYTE);
          nextLogAt = System.nanoTime() + LOG_INTERVAL.toNanos();
        }
        sleep(Math.max(pauseMillis, 1_000));
        lag = walLagBytes(connection, walGuard.startWalFile);
      }
    } catch (SQLException e) {
      walGuard.available = false;
      log.warn(
          "{}: cannot read pg_stat_archiver / pg_stat_replication / pg_replication_slots,"
              + " pacing on the fixed pause alone: {}",
          TABLE,
          e.getMessage());
    }
  }

  /**
   * Returns the WAL not yet archived or replayed, in bytes; {@code startWalFile} stands for the
   * last archived file when archiving is on and nothing is archived yet.
   */
  long walLagBytes(Connection connection, String startWalFile) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(WAL_LAG_QUERY)) {
      statement.setString(1, startWalFile);
      try (ResultSet result = statement.executeQuery()) {
        result.next();
        long lag = result.getLong(1);
        return result.wasNull() ? 0 : lag;
      }
    }
  }

  /**
   * The pacing state of one run: the WAL file current when it started, and whether the WAL-lag
   * views are readable.
   */
  static final class WalGuard {
    private final String startWalFile;
    private boolean available = true;

    WalGuard(String startWalFile) {
      this.startWalFile = startWalFile;
    }
  }

  /** Returns the WAL file current now, or {@code null} when the server does not say. */
  private static String currentWalFile(Connection connection) {
    try (Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery("SELECT pg_walfile_name(pg_current_wal_lsn())")) {
      result.next();
      return result.getString(1);
    } catch (SQLException e) {
      return null;
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
