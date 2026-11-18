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
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Gives {@code execution_traces} its own {@code tenant_id}: the tenant of the inject of the trace's
 * inject status, else of its inject test status, else the default tenant (counted in a warning).
 *
 * <p>Built for a large table in use. It runs outside a wrapping transaction, so that the WAL it
 * writes stays close to the size of the table:
 *
 * <ol>
 *   <li>add the column, nullable;
 *   <li>walk the table in physical order, one committed transaction per range of pages, so each
 *       page is rewritten once; no {@code VACUUM}, autovacuum reclaims the old row versions;
 *   <li>after each batch, pause, and wait while WAL archiving or a replica is too far behind;
 *   <li>build the index {@code CONCURRENTLY};
 *   <li>drain the rows written in the meantime, through a temporary partial index;
 *   <li>under a table lock, fill the last rows, {@code SET NOT NULL} and add the foreign key {@code
 *       NOT VALID}; validate it after the commit.
 * </ol>
 *
 * <p>Lock requests time out after a few seconds and retry, so the table never queues behind one.
 * Every step is guarded, so an interrupted run finishes on the next start. The table and its
 * indexes can grow up to their size until autovacuum reclaims the old row versions.
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
   * Fills the rows {@code %1$s} selects with their parent's tenant; returns how many it wrote and
   * how many had none. {@code %2$s} repeats the predicate on the updated side: with the {@code
   * ctid} join alone, the planner reads the whole table there.
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

  static final String RANGE_UPDATE =
      BACKFILL.formatted(
          "x.ctid >= ?::tid AND x.ctid < ?::tid", "t.ctid >= ?::tid AND t.ctid < ?::tid");

  private static final String BATCH_UPDATE =
      BACKFILL.formatted("x.execution_trace_id = ANY(?)", "t.execution_trace_id = ANY(?)");
  private static final String FINAL_UPDATE = BACKFILL.formatted("true", "t.tenant_id IS NULL");

  static final String NEXT_PENDING_IDS =
      "SELECT execution_trace_id FROM "
          + TABLE
          + " WHERE tenant_id IS NULL AND execution_trace_id > ?"
          + " ORDER BY execution_trace_id LIMIT ?";

  /**
   * The WAL not yet archived or replayed, in bytes. Inactive slots, and the archiver when {@code
   * archive_mode} is off, are left out: they never catch up. The archiver's lag comes from WAL file
   * names, counted from the run's first file (the parameter) while no WAL segment is archived.
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
          + " COALESCE(CASE WHEN last_archived_wal ~ '^[0-9A-F]{24}$'"
          + " THEN last_archived_wal END, ?) AS done,"
          + " pg_size_bytes(current_setting('wal_segment_size')) AS seg"
          + " FROM pg_stat_archiver WHERE current_setting('archive_mode') <> 'off') archiver"
          + " WHERE done ~ '^[0-9A-F]{24}$') lags";

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
          return true;
        });
    logInactiveSlots(connection);
    WalGuard walGuard = new WalGuard(currentWalFile(connection));
    long orphans = walk(connection, walGuard).orphans();
    buildTenantIndex(connection);
    createPendingIndex(connection);
    orphans += drain(connection, walGuard);
    Optional<Long> locked;
    while ((locked = underLockTimeout(connection, "locking the table", () -> finish(connection)))
        .isEmpty()) {
      // Rows written while the lock waits go to the paced drain, not to the locked step.
      orphans += drain(connection, walGuard);
      sleep(LOCK_RETRY_MILLIS);
    }
    orphans += locked.get();
    withLockRetry(
        connection,
        "validating the foreign key",
        () -> {
          try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + TABLE + " VALIDATE CONSTRAINT " + FOREIGN_KEY);
          }
          return true;
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
   * Rows the ranges miss (written meanwhile, or moved by a concurrent update) are left to {@link
   * #drain}.
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

  private static void createPendingIndex(Connection connection) throws SQLException {
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
  }

  /**
   * Passes repeat until one finds less than a batch: ids are random, so a row written during a pass
   * can land behind its cursor. Returns the orphans.
   */
  private long drain(Connection connection, WalGuard walGuard) throws SQLException {
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

  /** An invalid index left by an interrupted build is dropped first. */
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

  /** Returns the orphans of the rows it fills. */
  private long finish(Connection connection) throws SQLException {
    connection.setAutoCommit(false);
    try (Statement statement = connection.createStatement()) {
      lockTable(statement);
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
   * {@code pages} is the size on disk and bounds the walk; the estimates only give the row density.
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

  private interface SqlStep<T> {
    T run() throws SQLException;
  }

  /**
   * Retries {@code step} each time it times out on a lock: a waiting lock request makes every later
   * query on the table wait behind it.
   */
  private static void withLockRetry(Connection connection, String what, SqlStep<Boolean> step)
      throws SQLException {
    while (underLockTimeout(connection, what, step).isEmpty()) {
      sleep(LOCK_RETRY_MILLIS);
    }
  }

  /** Empty when {@code step} times out on a lock another transaction holds. */
  private static <T> Optional<T> underLockTimeout(
      Connection connection, String what, SqlStep<T> step) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute("SET lock_timeout = '" + LOCK_TIMEOUT + "'");
    }
    try {
      return Optional.of(step.run());
    } catch (SQLException e) {
      if (!LOCK_NOT_AVAILABLE.equals(e.getSQLState())) {
        throw e;
      }
      log.info("{}: {} waits for a lock another transaction holds, retrying", TABLE, what);
      return Optional.empty();
    } finally {
      try (Statement statement = connection.createStatement()) {
        statement.execute("RESET lock_timeout");
      }
    }
  }

  void lockTable(Statement statement) throws SQLException {
    statement.execute("LOCK TABLE " + TABLE + " IN ACCESS EXCLUSIVE MODE");
  }

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

  private static long orphansOf(PreparedStatement statement) throws SQLException {
    try (ResultSet result = statement.executeQuery()) {
      result.next();
      return result.getLong("orphans");
    }
  }

  /**
   * Pauses, then waits while the WAL lag is above the bound. A lag that cannot be read stops the
   * wait for the rest of the run.
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

  /** {@code startWalFile} stands in for the last archived file while no WAL segment is archived. */
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

  /** The pacing state of one run. */
  static final class WalGuard {
    private final String startWalFile;
    private boolean available = true;

    WalGuard(String startWalFile) {
      this.startWalFile = startWalFile;
    }
  }

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
