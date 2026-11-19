package io.openaev.migration;

import io.openaev.service.EsIndexingUtils;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Creates {@code inject_expectation_results} and fills it from the JSON array {@code
 * injects_expectations.inject_expectation_results}, then drops that column.
 *
 * <ol>
 *   <li>create the table and its lookup index, while both are still empty;
 *   <li>walk {@code injects_expectations} in physical order, one committed transaction per range of
 *       pages, so each page is read once; no {@code VACUUM};
 *   <li>after each batch, pause, and wait while WAL archiving or a replica is too far behind;
 *   <li>a catch-up pass fills any row the page walk could not have covered (added after the size
 *       was measured, or moved behind the cursor by a concurrent non-HOT update), in committed,
 *       paced batches of about {@link #TARGET_ROWS_PER_BATCH} expectations;
 *   <li>drop the JSON column, with a bounded lock wait and retry.
 * </ol>
 *
 * <p>Idempotent per expectation result row: {@code inject_expectation_result_id} is derived from
 * the expectation id and the result's position in the array, with {@code ON CONFLICT DO NOTHING} on
 * that primary key. A batch is retried on a concurrent delete of its expectation ({@code 23503}) or
 * a deadlock ({@code 40P01}), bounded; a run that still fails leaves a failed Flyway row, and the
 * next start needs {@code flyway repair} (a hard kill resumes by itself).
 *
 * <p>Changed in place rather than released as a new migration: see ADR-023.
 */
@Slf4j
@Component
public class V6_20261023184049806__Add_expectation_results_table extends BaseJavaMigration {

  static final String SOURCE_TABLE = "injects_expectations";
  static final String JSON_COLUMN = "inject_expectation_results";
  static final String RESULTS_TABLE = "inject_expectation_results";
  static final String INDEX = "idx_inject_expectation_results_expectation";
  static final String EXPECTATION_INJECT_MODEL = "expectation-inject";

  static final int TARGET_ROWS_PER_BATCH = 10_000;
  static final long UNKNOWN_DENSITY_PAGES_PER_BATCH = 1_000;
  static final int PAUSE_MILLIS = 200;
  static final long WAL_LAG_BOUND_BYTES = 1_024L * 1_024 * 1_024;
  static final int MAX_RETRY_ATTEMPTS = 5;
  static final String FOREIGN_KEY_VIOLATION = "23503";
  static final String DEADLOCK_DETECTED = "40P01";
  private static final long MEGABYTE = 1_024L * 1_024;
  private static final Duration LOG_INTERVAL = Duration.ofMinutes(1);
  private static final String LOCK_TIMEOUT = "5s";
  private static final int LOCK_RETRY_MILLIS = 5_000;
  private static final String LOCK_NOT_AVAILABLE = "55P03";

  static final String CREATE_TABLE =
      """
      CREATE TABLE IF NOT EXISTS inject_expectation_results(
          inject_expectation_result_id VARCHAR(255) PRIMARY KEY,
          inject_expectation_result_source_id VARCHAR(255) NULL,
          inject_expectation_result_source_type VARCHAR(255) NULL,
          inject_expectation_result_source_name VARCHAR(255) NULL,
          inject_expectation_result_source_asset_id VARCHAR(255) NULL,
          inject_expectation_result_source_platform VARCHAR(255) NULL,
          inject_expectation_result_date TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()::timestamptz,
          inject_expectation_result_result TEXT NULL, -- support provisional results
          inject_expectation_result_score NUMERIC NULL, -- support provisional results
          inject_expectation_result_metadata JSONB,
          inject_expectation_result_created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()::timestamptz,
          inject_expectation_result_updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()::timestamptz,
          inject_expectation_result_inject_expectation_id VARCHAR(255) NOT NULL REFERENCES injects_expectations(inject_expectation_id) ON DELETE CASCADE
      );
      """;

  /**
   * Built right after {@link #CREATE_TABLE}, while the table is still empty: a plain, instant
   * build, maintained incrementally as the walk inserts, rather than a bulk {@code CONCURRENTLY}
   * build once the table has grown. The foreign key's {@code ON DELETE CASCADE} needs it from the
   * start, or a delete of an expectation on a live old pod seq-scans this table.
   */
  static final String CREATE_INDEX =
      "CREATE INDEX IF NOT EXISTS "
          + INDEX
          + " ON "
          + RESULTS_TABLE
          + " (inject_expectation_result_inject_expectation_id)";

  /**
   * Fills {@code inject_expectation_results} from the rows {@code %1$s} selects; returns how many
   * rows it wrote. {@code candidates} filters by {@code json_typeof} before any row reaches {@code
   * json_array_elements}, so a non-array value (or {@code NULL}) in the column is never passed to
   * it. The result id is {@code md5(expectation id : ordinal)}, reshaped as a UUID string, so the
   * same source row always produces the same id.
   */
  private static final String BACKFILL =
      "WITH candidates AS ("
          + " SELECT ie.inject_expectation_id,"
          + " ie.inject_expectation_updated_at,"
          + " ie.inject_expectation_created_at,"
          + " ie.inject_expectation_results"
          + " FROM injects_expectations ie"
          + " WHERE json_typeof(ie.inject_expectation_results) = 'array' AND %1$s"
          + "),"
          + " results AS ("
          + " SELECT c.inject_expectation_id,"
          + " c.inject_expectation_updated_at,"
          + " c.inject_expectation_created_at,"
          + " elems.res,"
          + " md5(c.inject_expectation_id || ':' || elems.ord::text) AS digest"
          + " FROM candidates c,"
          + " LATERAL json_array_elements(c.inject_expectation_results) WITH ORDINALITY AS elems(res, ord)"
          + "),"
          + " written AS ("
          + " INSERT INTO inject_expectation_results ("
          + " inject_expectation_result_id,"
          + " inject_expectation_result_source_id,"
          + " inject_expectation_result_source_type,"
          + " inject_expectation_result_source_name,"
          + " inject_expectation_result_source_asset_id,"
          + " inject_expectation_result_source_platform,"
          + " inject_expectation_result_date,"
          + " inject_expectation_result_result,"
          + " inject_expectation_result_score,"
          + " inject_expectation_result_metadata,"
          + " inject_expectation_result_created_at,"
          + " inject_expectation_result_updated_at,"
          + " inject_expectation_result_inject_expectation_id"
          + " )"
          + " SELECT"
          + " substring(digest for 8) || '-' || substring(digest from 9 for 4) || '-'"
          + " || substring(digest from 13 for 4) || '-' || substring(digest from 17 for 4) || '-'"
          + " || substring(digest from 21 for 12),"
          + " res->>'sourceId',"
          + " res->>'sourceType',"
          + " res->>'sourceName',"
          + " res->>'sourceAssetId',"
          + " res->>'sourcePlatform',"
          + " COALESCE("
          + " NULLIF(res->>'date', '')::timestamptz,"
          + " inject_expectation_updated_at,"
          + " inject_expectation_created_at,"
          + " NOW()"
          + " ),"
          + " res->>'result',"
          + " NULLIF(res->>'score', '')::numeric,"
          + " res->'metadata',"
          + " NOW(),"
          + " NOW(),"
          + " inject_expectation_id"
          + " FROM results"
          + " ON CONFLICT (inject_expectation_result_id) DO NOTHING"
          + " RETURNING 1"
          + ")"
          + " SELECT count(*) AS written FROM written";

  static final String RANGE_INSERT = BACKFILL.formatted("ie.ctid >= ?::tid AND ie.ctid < ?::tid");

  /**
   * An expectation with at least one result and no row in the results table yet; the {@code CASE}
   * keeps {@code json_array_length} away from a non-array value. Once filled, an expectation leaves
   * this set, and one with an empty array never enters it.
   */
  private static final String MISSING =
      "CASE WHEN json_typeof(ie.inject_expectation_results) = 'array'"
          + " THEN json_array_length(ie.inject_expectation_results) ELSE 0 END > 0"
          + " AND NOT EXISTS (SELECT 1 FROM "
          + RESULTS_TABLE
          + " r WHERE r.inject_expectation_result_inject_expectation_id ="
          + " ie.inject_expectation_id)";

  /** The last id and the size of the next batch of missing expectations after a cursor. */
  private static final String NEXT_MISSING =
      "SELECT max(id), count(*) FROM (SELECT ie.inject_expectation_id AS id"
          + " FROM injects_expectations ie WHERE ie.inject_expectation_id > ? AND "
          + MISSING
          + " ORDER BY ie.inject_expectation_id LIMIT ?) batch";

  /** The batch {@link #NEXT_MISSING} picked: ids after the cursor, up to the last one it named. */
  private static final String CATCH_UP_INSERT =
      BACKFILL.formatted(
          "ie.inject_expectation_id > ? AND ie.inject_expectation_id <= ? AND " + MISSING);

  /**
   * The WAL not yet archived or replayed, in bytes. Copied from {@code
   * V6_20261112060000000__Add_tenant_id_to_execution_traces}, the pacing/lock-retry helper's first
   * use; this migration is ADR-023's second use, when a shared helper is due. Extracting it is a
   * follow-up after this release ships.
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

  private final Long pagesPerBatchOverride;
  private final int pauseMillis;
  private final long walLagBoundBytes;
  private final int catchUpRowsPerBatch;

  public V6_20261023184049806__Add_expectation_results_table() {
    this(null, PAUSE_MILLIS, WAL_LAG_BOUND_BYTES);
  }

  V6_20261023184049806__Add_expectation_results_table(
      Long pagesPerBatchOverride, int pauseMillis, long walLagBoundBytes) {
    this(pagesPerBatchOverride, pauseMillis, walLagBoundBytes, TARGET_ROWS_PER_BATCH);
  }

  V6_20261023184049806__Add_expectation_results_table(
      Long pagesPerBatchOverride, int pauseMillis, long walLagBoundBytes, int catchUpRowsPerBatch) {
    this.pagesPerBatchOverride = pagesPerBatchOverride;
    this.pauseMillis = pauseMillis;
    this.walLagBoundBytes = walLagBoundBytes;
    this.catchUpRowsPerBatch = catchUpRowsPerBatch;
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
        "creating the results table and its lookup index",
        () -> {
          try (Statement statement = connection.createStatement()) {
            statement.execute(CREATE_TABLE);
            statement.execute(CREATE_INDEX);
          }
          return true;
        });
    // A rerun (a retried failed run; a direct call outside Flyway's own version tracking) can
    // start from a state where an earlier run already dropped the column: the backfill query
    // names it, so it must not run again once there is nothing left to read.
    boolean hasJsonColumn = sourceJsonColumnExists(connection);
    long written = 0;
    if (hasJsonColumn) {
      logInactiveSlots(connection);
      WalGuard walGuard = new WalGuard(currentWalFile(connection));
      written = walk(connection, walGuard);
      written += catchUp(connection, walGuard);
      withLockRetry(
          connection,
          "dropping the JSON column",
          () -> {
            try (Statement statement = connection.createStatement()) {
              dropJsonColumn(statement);
            }
            return true;
          });
    }
    requestReindex(connection);
    log.info("{}: {} row(s) filled in total", RESULTS_TABLE, written);
  }

  /**
   * Rows the page ranges miss (the table grew after its size was measured, or a row moved behind
   * the cursor) go to {@link #catchUp}.
   */
  long walk(Connection connection, WalGuard walGuard) throws SQLException {
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
        SOURCE_TABLE,
        size.pages(),
        size.estimatedRows(),
        pagesPerBatch);
    return backfillByPageRanges(connection, size.pages(), pagesPerBatch, walGuard);
  }

  private long backfillByPageRanges(
      Connection connection, long totalPages, long pagesPerBatch, WalGuard walGuard)
      throws SQLException {
    long written = 0;
    long batches = 0;
    long startedAt = System.nanoTime();
    long nextLogAt = startedAt + LOG_INTERVAL.toNanos();
    try (PreparedStatement insert = connection.prepareStatement(RANGE_INSERT)) {
      for (long page = 0; page < totalPages; page += pagesPerBatch) {
        insert.setString(1, tid(page));
        insert.setString(2, tid(page + pagesPerBatch));
        written += runRetrying(insert, "range " + tid(page) + " to " + tid(page + pagesPerBatch));
        batches++;
        if (System.nanoTime() - nextLogAt >= 0) {
          log.info(
              "{}: page {} of {} walked, {} row(s) filled",
              SOURCE_TABLE,
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
        RESULTS_TABLE,
        written,
        batches,
        pagesPerBatch,
        Duration.ofNanos(System.nanoTime() - startedAt).toSeconds());
    return written;
  }

  /**
   * Fills the rows the walk missed, in committed batches of {@link #catchUpRowsPerBatch}
   * expectations, each followed by the walk's pacing and the same retry. A pass is a keyset scan
   * over the expectation ids and a filled expectation never comes back, so it ends whatever is
   * inserted meanwhile; another pass starts only while the last one found a full batch, so the
   * catch-up runs forever only if every pass finds a batch of new rows.
   */
  long catchUp(Connection connection, WalGuard walGuard) throws SQLException {
    long written = 0;
    long picked;
    try (PreparedStatement next = connection.prepareStatement(NEXT_MISSING);
        PreparedStatement insert = connection.prepareStatement(CATCH_UP_INSERT)) {
      do {
        picked = 0;
        String cursor = "";
        while (true) {
          next.setString(1, cursor);
          next.setInt(2, catchUpRowsPerBatch);
          String last;
          long size;
          try (ResultSet batch = next.executeQuery()) {
            batch.next();
            last = batch.getString(1);
            size = batch.getLong(2);
          }
          if (size == 0) {
            break;
          }
          insert.setString(1, cursor);
          insert.setString(2, last);
          written += runRetrying(insert, "the catch-up batch after '" + cursor + "'");
          picked += size;
          cursor = last;
          pace(connection, walGuard);
        }
      } while (picked >= catchUpRowsPerBatch);
    }
    log.info("{}: {} row(s) filled by the catch-up pass", RESULTS_TABLE, written);
    return written;
  }

  /**
   * Retries {@code statement} on a concurrent delete of its expectation ({@link
   * #FOREIGN_KEY_VIOLATION}) or a deadlock ({@link #DEADLOCK_DETECTED}), bounded by {@link
   * #MAX_RETRY_ATTEMPTS}: the cause is gone by the next attempt (the conflicting transaction has
   * already committed or rolled back), so no further wait is needed.
   */
  private long runRetrying(PreparedStatement statement, String what) throws SQLException {
    SQLException last = null;
    for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
      try {
        return runOnce(statement);
      } catch (SQLException e) {
        if (!isRetryable(e.getSQLState())) {
          throw e;
        }
        last = e;
        log.info(
            "{}: {} retried after {} (attempt {} of {})",
            RESULTS_TABLE,
            what,
            e.getSQLState(),
            attempt,
            MAX_RETRY_ATTEMPTS);
      }
    }
    throw last;
  }

  long runOnce(PreparedStatement statement) throws SQLException {
    try (ResultSet result = statement.executeQuery()) {
      result.next();
      return result.getLong("written");
    }
  }

  private static boolean isRetryable(String sqlState) {
    return FOREIGN_KEY_VIOLATION.equals(sqlState) || DEADLOCK_DETECTED.equals(sqlState);
  }

  private static String tid(long page) {
    return "(" + page + ",0)";
  }

  private static boolean sourceJsonColumnExists(Connection connection) throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet result =
            statement.executeQuery(
                "SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema()"
                    + " AND table_name = '"
                    + SOURCE_TABLE
                    + "' AND column_name = '"
                    + JSON_COLUMN
                    + "'")) {
      return result.next();
    }
  }

  void dropJsonColumn(Statement statement) throws SQLException {
    statement.execute(
        "ALTER TABLE IF EXISTS " + SOURCE_TABLE + " DROP COLUMN IF EXISTS " + JSON_COLUMN);
  }

  private static void requestReindex(Connection connection) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      // The sentinel is written as an ISO-8601 UTC literal: no JVM time zone is involved.
      statement.execute(
          "INSERT INTO indexing_status (indexing_status_type, indexing_status_indexing_date)"
              + " VALUES ('"
              + EXPECTATION_INJECT_MODEL
              + "', '"
              + EsIndexingUtils.REINDEX_REQUESTED_CURSOR
              + "')"
              + " ON CONFLICT (indexing_status_type)"
              + " DO UPDATE SET indexing_status_indexing_date = EXCLUDED.indexing_status_indexing_date");
    }
    EsIndexingUtils.requestReindexAtStartup(EXPECTATION_INJECT_MODEL);
  }

  /**
   * {@code pages} is the size on disk and bounds the walk; the estimates only give the row density.
   */
  record TableSize(long pages, long estimatedRows, long estimatedPages) {}

  TableSize tableSize(Connection connection) throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet result =
            statement.executeQuery(
                "SELECT pg_relation_size(oid) / current_setting('block_size')::bigint,"
                    + " reltuples::bigint, relpages::bigint FROM pg_class WHERE oid = '"
                    + SOURCE_TABLE
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
      log.info("{}: {} waits for a lock another transaction holds, retrying", RESULTS_TABLE, what);
      return Optional.empty();
    } finally {
      try (Statement statement = connection.createStatement()) {
        statement.execute("RESET lock_timeout");
      }
    }
  }

  private static void logInactiveSlots(Connection connection) {
    try (Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(INACTIVE_SLOTS_QUERY)) {
      while (result.next()) {
        log.warn(
            "{}: inactive replication slot {} retains {} MB of WAL; the backfill does not wait for"
                + " it, and its writes add to that WAL",
            RESULTS_TABLE,
            result.getString(1),
            result.getLong(2) / MEGABYTE);
      }
    } catch (SQLException e) {
      log.warn("{}: cannot read pg_replication_slots: {}", RESULTS_TABLE, e.getMessage());
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
          log.warn(
              "{}: waiting for WAL archiving or replicas, {} MB behind (bound {} MB)",
              RESULTS_TABLE,
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
          "{}: cannot read pg_stat_archiver / pg_stat_replication / pg_replication_slots, pacing"
              + " on the fixed pause alone: {}",
          RESULTS_TABLE,
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
      // Stop here: the next start resumes from the rows the results table does not have yet.
      Thread.currentThread().interrupt();
      throw new IllegalStateException(RESULTS_TABLE + " backfill interrupted", e);
    }
  }

  /* ROLLBACK
   DROP TABLE IF EXISTS inject_expectation_results;
   ALTER TABLE injects_expectations ADD COLUMN IF NOT EXISTS inject_expectation_results json;
   -- The JSON column comes back empty: the rows it held are only recoverable from a backup taken
   -- before this migration ran. Only valid together with a revert of the entity mapping, which
   -- reads results from inject_expectation_results, not from this column.
  */
}
