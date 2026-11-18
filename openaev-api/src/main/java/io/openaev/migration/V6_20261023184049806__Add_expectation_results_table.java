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
 * <p>The first version of this migration ran the whole table in one {@code INSERT ... SELECT},
 * inside Flyway's wrapping transaction: on 27 Oct it filled a platform's WAL disk (ADR-023). This
 * version applies the same shape as {@code
 * V6_20261112060000000__Add_tenant_id_to_execution_traces}:
 *
 * <ol>
 *   <li>create the table, without its lookup index;
 *   <li>walk {@code injects_expectations} in physical order, one committed transaction per range of
 *       pages, so each page is read once; no {@code VACUUM};
 *   <li>after each batch, pause, and wait while WAL archiving or a replica is too far behind;
 *   <li>a final unbounded pass mops up any row the page walk could not have covered because the
 *       table grew after its size was measured; harmless and cheap, since such a row can only be
 *       one created after this migration started, and the application has not written the JSON
 *       column in a long time;
 *   <li>build the lookup index {@code CONCURRENTLY};
 *   <li>drop the JSON column, with a bounded lock wait and retry.
 * </ol>
 *
 * <p>Changed in place rather than released as a new migration: it is a Java migration (no Flyway
 * checksum), and it reaches the same final state the first version reached, whatever state a
 * platform is in. The first version ran inside one transaction, so a platform that has not applied
 * it yet is the only state there is to handle: a failed run leaves nothing behind (table absent,
 * column present). A platform that already applied the first version in full is never replayed:
 * Flyway does not re-run an already-recorded version.
 *
 * <p>Idempotent per expectation result row, not per expectation: {@code
 * inject_expectation_result_id} is derived from the expectation id and the result's position in the
 * array, so a retried range, or the final catch-up pass re-reading an already-filled row, writes
 * nothing new ({@code ON CONFLICT DO NOTHING} on the primary key, which is already maintained
 * whether this migration runs or not, so this costs nothing beyond the write itself).
 *
 * <p>Replaces the first version's {@code DELETE FROM indexing_status}: rule 6 of {@code
 * migration.instructions.md} forbids it (a pod still up during a rolling deploy recreates the row
 * before the new pod's boot check, so the wipe silently never happens). The first version's intent
 * was a full reset of the {@code expectation-inject} index at the next boot, requested here the
 * same way as {@code
 * V6_20260924120000000__Request_expectation_inject_reindex_after_group_parent_dedup}.
 */
@Slf4j
@Component
public class V6_20261023184049806__Add_expectation_results_table extends BaseJavaMigration {

  static final String SOURCE_TABLE = "injects_expectations";
  static final String JSON_COLUMN = "inject_expectation_results";
  static final String RESULTS_TABLE = "inject_expectation_results";
  static final String INDEX = "idx_inject_expectation_results_expectation";
  private static final String EXPECTATION_INJECT_MODEL = "expectation-inject";

  static final int TARGET_ROWS_PER_BATCH = 10_000;
  static final long UNKNOWN_DENSITY_PAGES_PER_BATCH = 1_000;
  static final int PAUSE_MILLIS = 200;
  static final long WAL_LAG_BOUND_BYTES = 1_024L * 1_024 * 1_024;
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
          inject_expectation_result_result TEXT NULL,
          inject_expectation_result_score NUMERIC NULL,
          inject_expectation_result_metadata JSONB,
          inject_expectation_result_created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()::timestamptz,
          inject_expectation_result_updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()::timestamptz,
          inject_expectation_result_inject_expectation_id VARCHAR(255) NOT NULL REFERENCES injects_expectations(inject_expectation_id) ON DELETE CASCADE
      );
      """;

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
  private static final String FINAL_INSERT = BACKFILL.formatted("true");

  /**
   * The WAL not yet archived or replayed, in bytes. Copied from {@code
   * V6_20261112060000000__Add_tenant_id_to_execution_traces}; a shared helper is due once a third
   * migration needs this (ADR-023).
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

  public V6_20261023184049806__Add_expectation_results_table() {
    this(null, PAUSE_MILLIS, WAL_LAG_BOUND_BYTES);
  }

  V6_20261023184049806__Add_expectation_results_table(
      Long pagesPerBatchOverride, int pauseMillis, long walLagBoundBytes) {
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
        "creating the results table",
        () -> {
          try (Statement statement = connection.createStatement()) {
            statement.execute(CREATE_TABLE);
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
      written += finalCatchUp(connection);
    }
    buildResultsIndex(connection);
    if (hasJsonColumn) {
      withLockRetry(
          connection,
          "dropping the JSON column",
          () -> {
            try (Statement statement = connection.createStatement()) {
              statement.execute(
                  "ALTER TABLE IF EXISTS "
                      + SOURCE_TABLE
                      + " DROP COLUMN IF EXISTS "
                      + JSON_COLUMN);
            }
            return true;
          });
    }
    requestReindex(connection);
    log.info("{}: {} row(s) filled in total", RESULTS_TABLE, written);
  }

  /**
   * Rows the page ranges miss (the table grew after its size was measured) go to {@link
   * #finalCatchUp}.
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
        try (ResultSet result = insert.executeQuery()) {
          result.next();
          written += result.getLong("written");
        }
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
   * One unpaced pass over the whole source table, with no {@code ctid} bound: mops up a row the
   * page walk could not have reached because the table grew past the size measured at its start.
   * Cheap, because every row the walk already filled is skipped by the primary-key conflict.
   */
  private long finalCatchUp(Connection connection) throws SQLException {
    try (PreparedStatement insert = connection.prepareStatement(FINAL_INSERT);
        ResultSet result = insert.executeQuery()) {
      result.next();
      long written = result.getLong("written");
      log.info("{}: {} row(s) filled by the final catch-up pass", RESULTS_TABLE, written);
      return written;
    }
  }

  private static String tid(long page) {
    return "(" + page + ",0)";
  }

  private static boolean sourceJsonColumnExists(Connection connection) throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet result =
            statement.executeQuery(
                "SELECT 1 FROM information_schema.columns WHERE table_name = '"
                    + SOURCE_TABLE
                    + "' AND column_name = '"
                    + JSON_COLUMN
                    + "'")) {
      return result.next();
    }
  }

  /** An invalid index left by an interrupted build is dropped first. */
  private static void buildResultsIndex(Connection connection) throws SQLException {
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
          "CREATE INDEX CONCURRENTLY IF NOT EXISTS "
              + INDEX
              + " ON "
              + RESULTS_TABLE
              + " (inject_expectation_result_inject_expectation_id)");
    }
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
  private record TableSize(long pages, long estimatedRows, long estimatedPages) {}

  private static TableSize tableSize(Connection connection) throws SQLException {
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
          log.info(
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
