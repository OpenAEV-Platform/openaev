package io.openaev.migration;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;

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
 * false), on a connection in autocommit mode, so the extra disk it needs is bounded by one batch of
 * rows, not by the size of the table:
 *
 * <ol>
 *   <li>the column is added, nullable and with no default (a catalog change only);
 *   <li>the backfill walks the primary key {@value #BATCH_SIZE} rows at a time, each batch in its
 *       own transaction, so the total read grows with the table and not with its square;
 *   <li>{@code VACUUM} runs every {@link #vacuumInterval} batches and once after the loop, so that
 *       the next batches reuse the space of the old row versions and the table does not double. The
 *       ids are random, so each batch touches pages all over the table and each {@code VACUUM}
 *       reads nearly all of it: the interval is the larger of {@value #VACUUM_EVERY_BATCHES}
 *       batches and the number of batches that cover {@value #VACUUM_SHARE_PERCENT} percent of the
 *       estimated rows ({@code pg_class.reltuples}, read once). That makes about twenty vacuums
 *       whatever the size, and the space held by dead row versions stays near that share of the
 *       table;
 *   <li>a running instance can insert rows with no tenant behind the cursor, so a drain builds a
 *       temporary partial index of the rows with no tenant ({@code CONCURRENTLY}, so inserts go
 *       on), walks it in primary key order and fills them in committed batches, with the same
 *       {@code VACUUM} interval and one {@code VACUUM} at the end if it wrote rows: each row is
 *       read once, whatever the size of that backlog;
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

  static final int BATCH_SIZE = 10_000;
  static final int VACUUM_EVERY_BATCHES = 50;
  static final int VACUUM_SHARE_PERCENT = 5;
  static final String FOREIGN_KEY = "fk_" + TABLE + "_tenant_id";
  static final String INDEX = "idx_" + TABLE + "_tenant_id";
  static final String PENDING_INDEX = "idx_" + TABLE + "_tenant_id_pending";

  /**
   * Fills the rows of {@code %s} (a predicate on the alias {@code x}) with the tenant of their
   * parent. Returns the rows written and how many had no parent tenant.
   */
  private static final String BACKFILL =
      "WITH written AS ("
          + " UPDATE "
          + TABLE
          + " t SET tenant_id = COALESCE(b.parent_tenant, ?) FROM ("
          + " SELECT x.execution_trace_id AS trace_id,"
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
          + " WHERE x.tenant_id IS NULL AND %s) b"
          + " WHERE t.execution_trace_id = b.trace_id"
          + " RETURNING b.parent_tenant IS NULL AS orphan)"
          + " SELECT count(*) AS written_rows, count(*) FILTER (WHERE orphan) AS orphans"
          + " FROM written";

  private static final String BATCH_UPDATE = BACKFILL.formatted("x.execution_trace_id = ANY(?)");
  private static final String FINAL_UPDATE = BACKFILL.formatted("true");

  /** Picks the next ids in primary key order: an index range scan, never a rescan of the table. */
  static final String NEXT_IDS =
      "SELECT execution_trace_id FROM "
          + TABLE
          + " WHERE execution_trace_id > ? ORDER BY execution_trace_id LIMIT ?";

  /** Picks the next rows with no tenant in primary key order, served by the partial index. */
  static final String NEXT_PENDING_IDS =
      "SELECT execution_trace_id FROM "
          + TABLE
          + " WHERE tenant_id IS NULL AND execution_trace_id > ?"
          + " ORDER BY execution_trace_id LIMIT ?";

  private final int batchSize;
  private final int minVacuumEveryBatches;

  public V6_20261112060000000__Add_tenant_id_to_execution_traces() {
    this(BATCH_SIZE, VACUUM_EVERY_BATCHES);
  }

  V6_20261112060000000__Add_tenant_id_to_execution_traces(
      int batchSize, int minVacuumEveryBatches) {
    this.batchSize = batchSize;
    this.minVacuumEveryBatches = minVacuumEveryBatches;
  }

  /**
   * Number of batches between two vacuums: {@code minBatches}, or the batches that cover {@value
   * #VACUUM_SHARE_PERCENT} percent of {@code estimatedRows} when that is larger. An estimate of 0
   * or below means unknown ({@code reltuples} is -1 before the first analyze).
   */
  static long vacuumInterval(long estimatedRows, int batchSize, int minBatches) {
    if (estimatedRows <= 0) {
      return minBatches;
    }
    long batchesOfShare =
        (estimatedRows * VACUUM_SHARE_PERCENT + 100L * batchSize - 1) / (100L * batchSize);
    return Math.max(minBatches, batchesOfShare);
  }

  @Override
  public boolean canExecuteInTransaction() {
    return false;
  }

  @Override
  public void migrate(Context context) throws Exception {
    migrate(context.getConnection());
  }

  /**
   * Runs every step on {@code connection}, which is put in autocommit mode: {@code VACUUM} refuses
   * to run in a transaction.
   */
  void migrate(Connection connection) throws SQLException {
    connection.setAutoCommit(true);
    // Table and column names below are constants of this class, never input.
    try (Statement statement = connection.createStatement()) {
      statement.execute(
          "ALTER TABLE " + TABLE + " ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(255)");
    }
    long estimatedRows = estimatedRows(connection);
    long vacuumEvery = vacuumInterval(estimatedRows, batchSize, minVacuumEveryBatches);
    log.info(
        "{}: about {} row(s), batches of {}, a vacuum every {} batch(es)",
        TABLE,
        estimatedRows,
        batchSize,
        vacuumEvery);
    long orphans = backfillInBatches(connection, vacuumEvery);
    orphans += drain(connection, vacuumEvery);
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
   * Walks the primary key and fills each batch of ids in its own transaction. Rows inserted behind
   * the cursor are left to {@link #drain}. Returns the orphans.
   */
  private long backfillInBatches(Connection connection, long vacuumEvery) throws SQLException {
    long scanned = 0;
    long orphans = 0;
    long batches = 0;
    long startedAt = System.nanoTime();
    String lastId = "";
    try (PreparedStatement pick = connection.prepareStatement(NEXT_IDS);
        PreparedStatement update = connection.prepareStatement(BATCH_UPDATE)) {
      while (true) {
        pick.setString(1, lastId);
        pick.setInt(2, batchSize);
        List<String> ids = new ArrayList<>(batchSize);
        try (ResultSet result = pick.executeQuery()) {
          while (result.next()) {
            ids.add(result.getString(1));
          }
        }
        if (ids.isEmpty()) {
          break;
        }
        update.setString(1, DEFAULT_TENANT_UUID);
        update.setArray(2, connection.createArrayOf("varchar", ids.toArray()));
        orphans += run(update)[1];
        lastId = ids.getLast();
        scanned += ids.size();
        batches++;
        if (batches % vacuumEvery == 0) {
          log.info(
              "{}: {} row(s) done in {} s",
              TABLE,
              scanned,
              Duration.ofNanos(System.nanoTime() - startedAt).toSeconds());
          vacuum(connection);
        }
      }
    }
    if (batches > 0) {
      vacuum(connection);
    }
    log.info(
        "{}: {} row(s) walked in {} batch(es), {} s",
        TABLE,
        scanned,
        batches,
        Duration.ofNanos(System.nanoTime() - startedAt).toSeconds());
    return orphans;
  }

  /**
   * Fills, in committed batches, the rows with no tenant left after the walk: those inserted behind
   * the cursor by a running instance. A temporary partial index holds only those rows, so each
   * batch reads its rows and no other. Returns the orphans.
   */
  private long drain(Connection connection, long vacuumEvery) throws SQLException {
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
    long batches = 0;
    String lastId = "";
    try (PreparedStatement pick = connection.prepareStatement(NEXT_PENDING_IDS);
        PreparedStatement update = connection.prepareStatement(BATCH_UPDATE)) {
      while (true) {
        pick.setString(1, lastId);
        pick.setInt(2, batchSize);
        List<String> ids = new ArrayList<>(batchSize);
        try (ResultSet result = pick.executeQuery()) {
          while (result.next()) {
            ids.add(result.getString(1));
          }
        }
        if (ids.isEmpty()) {
          break;
        }
        update.setString(1, DEFAULT_TENANT_UUID);
        update.setArray(2, connection.createArrayOf("varchar", ids.toArray()));
        orphans += run(update)[1];
        lastId = ids.getLast();
        drained += ids.size();
        batches++;
        if (batches % vacuumEvery == 0) {
          vacuum(connection);
        }
      }
    }
    if (drained > 0) {
      vacuum(connection);
    }
    log.info("{}: {} row(s) inserted during the backfill filled in batches", TABLE, drained);
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
        orphans = run(remaining)[1];
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

  private static long estimatedRows(Connection connection) throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet result =
            statement.executeQuery(
                "SELECT reltuples::bigint FROM pg_class WHERE oid = '" + TABLE + "'::regclass")) {
      return result.next() ? result.getLong(1) : 0;
    }
  }

  private static long[] run(PreparedStatement statement) throws SQLException {
    try (ResultSet result = statement.executeQuery()) {
      result.next();
      return new long[] {result.getLong("written_rows"), result.getLong("orphans")};
    }
  }

  private static void vacuum(Connection connection) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute("VACUUM " + TABLE);
    }
  }

  /* ROLLBACK
   ALTER TABLE execution_traces DROP COLUMN IF EXISTS tenant_id;
   -- Dropping the column drops its foreign key and index. Only valid together with a revert of the
   -- entity mapping, which writes the column on every insert.
  */
}
