package io.openaev.migration;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static io.openaev.migration.V6_20261112060000000__Add_tenant_id_to_execution_traces.FOREIGN_KEY;
import static io.openaev.migration.V6_20261112060000000__Add_tenant_id_to_execution_traces.INDEX;
import static io.openaev.migration.V6_20261112060000000__Add_tenant_id_to_execution_traces.RANGE_UPDATE;
import static io.openaev.migration.V6_20261112060000000__Add_tenant_id_to_execution_traces.TABLE;
import static io.openaev.migration.V6_20261112060000000__Add_tenant_id_to_execution_traces.pagesPerBatch;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openaev.IntegrationTest;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Verifies, on the live schema, that {@code execution_traces} carries its own {@code tenant_id},
 * that the backfill attributes each row to the tenant of the inject its status belongs to, and that
 * the migration reaches the same final state from every partly migrated state.
 *
 * <p>The migration runs outside a transaction, so this class is not {@code @Transactional}: it
 * seeds and runs the migration on its own autocommit connection, and {@code @AfterEach} removes
 * what it seeded and runs the migration once more so the shared database ends as the rest of the
 * suite expects it (column {@code NOT NULL}, foreign key and index in place).
 *
 * <p>Seeded rows carry a random filler in their message so a handful of them spans more than one
 * heap page: the tests that check partial-commit and resumption behavior need that to force more
 * than one page range.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Migration V6_20261112060000000: tenant_id on execution_traces")
class AddTenantIdToExecutionTracesMigrationTest extends IntegrationTest {

  private static final String SEED_MESSAGE = "migration-test-seed";

  /** One page a batch, no pause, no WAL-lag wait: deterministic and fast for tests. */
  private final V6_20261112060000000__Add_tenant_id_to_execution_traces small =
      new V6_20261112060000000__Add_tenant_id_to_execution_traces(2, 1L, 0, Long.MAX_VALUE);

  @Autowired private V6_20261112060000000__Add_tenant_id_to_execution_traces migration;
  @Autowired private DataSource dataSource;

  private Connection connection;
  private final List<String> seededStatuses = new ArrayList<>();
  private final List<String> seededTestStatuses = new ArrayList<>();
  private final List<String> seededInjects = new ArrayList<>();
  private final List<String> seededTenants = new ArrayList<>();

  private String tenantB;
  private String tenantC;
  private String statusOfB;
  private String testStatusOfC;
  private String statusWithoutInject;

  @BeforeEach
  void openConnectionAndSeedParents() throws SQLException {
    connection = dataSource.getConnection();
    connection.setAutoCommit(true);

    tenantB = seedTenant("trace-owner-b");
    tenantC = seedTenant("trace-owner-c");
    String injectOfB = seedInject(tenantB);
    String injectOfC = seedInject(tenantC);
    statusOfB = seedStatus(injectOfB);
    statusWithoutInject = seedStatus(null);
    testStatusOfC = seedTestStatus(injectOfC);
  }

  @AfterEach
  void removeSeedAndRestoreSchema() throws SQLException {
    try {
      execute("DROP TRIGGER IF EXISTS test_trace_fail ON " + TABLE);
      execute("DROP FUNCTION IF EXISTS test_trace_fail()");
      execute("DELETE FROM " + TABLE + " WHERE execution_message LIKE '" + SEED_MESSAGE + "%'");
      deleteAll("injects_tests_statuses", "status_id", seededTestStatuses);
      deleteAll("injects_statuses", "status_id", seededStatuses);
      deleteAll("injects", "inject_id", seededInjects);
      deleteAll("tenants", "tenant_id", seededTenants);
      migration.migrate(connection);
    } finally {
      seededStatuses.clear();
      seededTestStatuses.clear();
      seededInjects.clear();
      seededTenants.clear();
      connection.close();
    }
  }

  @Nested
  @DisplayName("Final state")
  class FinalState {

    @Test
    @DisplayName("The migration does not ask Flyway for a wrapping transaction")
    void given_migration_should_runOutsideATransaction() {
      // Act + Assert
      assertThat(migration.canExecuteInTransaction()).isFalse();
    }

    @Test
    @DisplayName(
        "A table with no tenant column state ends with NOT NULL, the cascading foreign key and the index")
    void given_pendingTable_should_endWithTheFullSchema() throws Exception {
      // Arrange
      makePending();
      seedTrace("m-1", statusOfB, null, null);

      // Act
      migration.migrate(contextOn(connection));

      // Assert
      assertFullSchema();
    }

    @Test
    @DisplayName("Running the migration on a migrated table changes nothing")
    void given_migratedTable_should_rerunWithoutChange() throws Exception {
      // Arrange
      seedTrace("m-1", statusOfB, null, tenantC);

      // Act
      migration.migrate(contextOn(connection));

      // Assert
      assertFullSchema();
      assertThat(tenantOf("m-1")).isEqualTo(tenantC);
    }
  }

  @Nested
  @DisplayName("Backfill")
  class Backfill {

    @Test
    @DisplayName(
        "A trace of an inject status or of an inject test status takes its inject's tenant")
    void given_tracesOfStatuses_should_takeTheInjectTenant() throws Exception {
      // Arrange
      makePending();
      seedTrace("m-status", statusOfB, null, null);
      seedTrace("m-test-status", null, testStatusOfC, null);

      // Act
      migration.migrate(contextOn(connection));

      // Assert
      assertThat(tenantOf("m-status")).isEqualTo(tenantB);
      assertThat(tenantOf("m-test-status")).isEqualTo(tenantC);
    }

    @Test
    @DisplayName("A trace of both an inject status of B and a test status of C stays in B")
    void given_traceOfBothStatuses_should_takeTheInjectStatusTenant() throws Exception {
      // Arrange
      makePending();
      seedTrace("m-both", statusOfB, testStatusOfC, null);

      // Act
      migration.migrate(contextOn(connection));

      // Assert
      assertThat(tenantOf("m-both")).isEqualTo(tenantB);
    }

    @Test
    @DisplayName("A trace whose parent gives no tenant falls back to the default tenant")
    void given_orphanTrace_should_fallBackToTheDefaultTenant() throws Exception {
      // Arrange
      makePending();
      seedTrace("m-orphan-status", statusWithoutInject, null, null);

      // Act
      migration.migrate(contextOn(connection));

      // Assert
      assertThat(tenantOf("m-orphan-status")).isEqualTo(DEFAULT_TENANT_UUID);
    }

    @Test
    @DisplayName(
        "A trace that already has a tenant holds the same tenant after the run, whatever its status's inject")
    void given_traceAlreadyAttributed_should_leaveItsTenantUnchanged() throws Exception {
      // Arrange
      makePending();
      seedTrace("m-set", statusOfB, null, tenantC);

      // Act
      migration.migrate(contextOn(connection));

      // Assert
      assertThat(tenantOf("m-set")).isEqualTo(tenantC);
    }

    @Test
    @DisplayName("More rows than one page range are all filled, over several commits")
    void given_moreRowsThanOnePageRange_should_fillEveryRow() throws SQLException {
      // Arrange
      makePending();
      for (int i = 0; i < 7; i++) {
        seedTrace(
            "m-many-" + i, i % 2 == 0 ? statusOfB : null, i % 2 == 0 ? null : testStatusOfC, null);
      }

      // Act
      small.migrate(connection);

      // Assert
      assertThat(count("SELECT count(*) FROM " + TABLE + " WHERE tenant_id IS NULL")).isZero();
      for (int i = 0; i < 7; i++) {
        assertThat(tenantOf("m-many-" + i)).isEqualTo(i % 2 == 0 ? tenantB : tenantC);
      }
      assertFullSchema();
    }
  }

  @Nested
  @DisplayName("Rerun")
  class Rerun {

    @Test
    @DisplayName(
        "A table with the column present and half of its rows filled reaches the final state")
    void given_partlyMigratedTable_should_reachTheFinalState() throws SQLException {
      // Arrange
      makePending();
      seedTrace("m-done-1", statusOfB, null, tenantB);
      seedTrace("m-done-2", null, testStatusOfC, tenantC);
      seedTrace("m-todo-1", statusOfB, null, null);
      seedTrace("m-todo-2", null, testStatusOfC, null);
      seedTrace("m-todo-3", statusWithoutInject, null, null);

      // Act
      small.migrate(connection);

      // Assert
      assertThat(tenantOf("m-done-1")).isEqualTo(tenantB);
      assertThat(tenantOf("m-done-2")).isEqualTo(tenantC);
      assertThat(tenantOf("m-todo-1")).isEqualTo(tenantB);
      assertThat(tenantOf("m-todo-2")).isEqualTo(tenantC);
      assertThat(tenantOf("m-todo-3")).isEqualTo(DEFAULT_TENANT_UUID);
      assertFullSchema();
    }

    @Test
    @DisplayName(
        "A run that fails after some ranges leaves them committed, and a second run finishes")
    void given_failureAfterSomeRanges_should_leaveCommittedRangesAndFinishOnRerun()
        throws SQLException {
      // Arrange: six rows, each carrying enough filler that four fit on one heap page and two
      // spill to the next, so the single-page-per-range migration commits at least one range
      // before the seeded trigger fails the range holding m-6
      makePending();
      for (int i = 1; i <= 6; i++) {
        seedTrace("m-" + i, statusOfB, null, null);
      }
      execute(
          "CREATE FUNCTION test_trace_fail() RETURNS trigger AS $$ BEGIN"
              + " IF NEW.execution_trace_id = 'm-6' THEN RAISE EXCEPTION 'seeded failure'; END IF;"
              + " RETURN NEW; END $$ LANGUAGE plpgsql");
      execute(
          "CREATE TRIGGER test_trace_fail BEFORE UPDATE ON "
              + TABLE
              + " FOR EACH ROW EXECUTE FUNCTION test_trace_fail()");

      // Act
      assertThatThrownBy(() -> small.migrate(connection))
          .isInstanceOf(SQLException.class)
          .hasMessageContaining("seeded failure");

      // Assert: the ranges before the failing one are committed, the failing one is not
      long filledAfterFailure =
          count(
              "SELECT count(*) FROM "
                  + TABLE
                  + " WHERE execution_message LIKE '"
                  + SEED_MESSAGE
                  + "%' AND tenant_id IS NOT NULL");
      assertThat(filledAfterFailure).isBetween(1L, 5L);

      // Act: the cause is gone, the migration runs again
      execute("DROP TRIGGER test_trace_fail ON " + TABLE);
      small.migrate(connection);

      // Assert
      for (int i = 1; i <= 6; i++) {
        assertThat(tenantOf("m-" + i)).isEqualTo(tenantB);
      }
      assertFullSchema();
    }
  }

  @Nested
  @DisplayName("Pages per batch")
  class PagesPerBatch {

    @Test
    @DisplayName("An unknown row or page estimate gives one page per batch")
    void given_unknownEstimate_should_useOnePagePerBatch() {
      // Act + Assert
      assertThat(pagesPerBatch(-1, 100, 10_000)).isEqualTo(1);
      assertThat(pagesPerBatch(0, 100, 10_000)).isEqualTo(1);
      assertThat(pagesPerBatch(100_000, 0, 10_000)).isEqualTo(1);
    }

    @Test
    @DisplayName("The batch covers about the target row count, given the table's row density")
    void given_knownDensity_should_coverAboutTheTargetRows() {
      // Act + Assert: 1,000,000 rows over 100,000 pages is 10 rows/page, so 10,000 rows take 1,000
      // pages
      assertThat(pagesPerBatch(1_000_000, 100_000, 10_000)).isEqualTo(1_000);
    }

    @Test
    @DisplayName("At least one page per batch, even for a very dense table")
    void given_veryDenseTable_should_useAtLeastOnePage() {
      // Act + Assert
      assertThat(pagesPerBatch(1_000_000_000, 100, 10_000)).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("Range picker")
  class RangePicker {

    @Test
    @DisplayName("A page range is read as a Tid Range Scan, in physical order, with no sort")
    void given_pageRange_should_useATidRangeScan() throws SQLException {
      // Arrange: the same predicate shape RANGE_UPDATE's inner scan of x uses, isolated from the
      // join to the parent tables, whose own statistics can otherwise lead the planner to drive
      // the join from an index on x rather than a range of x itself. The column must be nullable,
      // or the planner constant-folds "tenant_id IS NULL" to false from the NOT NULL constraint.
      assertThat(RANGE_UPDATE).contains("x.ctid >= ?::tid AND x.ctid < ?::tid");
      makePending();
      String sql =
          "SELECT 1 FROM "
              + TABLE
              + " WHERE tenant_id IS NULL AND ctid >= '(0,0)'::tid AND ctid < '(10,0)'::tid";

      // Act: a near-empty table otherwise costs a seq scan the same as any other access path
      execute("SET enable_seqscan = off");
      String plan;
      try {
        plan =
            rows("EXPLAIN " + sql).stream()
                .map(row -> (String) row[0])
                .reduce("", (left, right) -> left + "\n" + right);
      } finally {
        execute("RESET enable_seqscan");
      }

      // Assert
      assertThat(plan).contains("Tid Range Scan").doesNotContain("Sort");
    }
  }

  @Nested
  @DisplayName("No VACUUM")
  class NoVacuum {

    @Test
    @DisplayName("A full run, from a pending table to the final state, issues no VACUUM statement")
    void given_pendingTable_should_issueNoVacuumStatement() throws SQLException {
      // Arrange
      makePending();
      for (int i = 0; i < 5; i++) {
        seedTrace("m-rec-" + i, statusOfB, null, null);
      }
      List<String> statements = new ArrayList<>();

      // Act
      small.migrate(recordingConnection(connection, statements));

      // Assert
      assertThat(statements)
          .as("statements executed by the migration")
          .isNotEmpty()
          .noneMatch(sql -> sql.toUpperCase().contains("VACUUM"));
      assertFullSchema();
    }
  }

  @Nested
  @DisplayName("Rows outside the walked ranges")
  class BehindTheCursor {

    @Test
    @DisplayName(
        "Recent rows inserted while the loop runs are filled by the drain, in several batches, and the locked step writes none")
    void given_rowsInsertedDuringTheLoop_should_beFilledByTheDrainInBatches() throws SQLException {
      // Arrange: the trigger inserts five rows when the loop updates m-3, after the migration
      // already measured the table's page count, so none of the five falls inside any page range
      makePending();
      seedTrace("m-2", statusOfB, null, null);
      seedTrace("m-3", statusOfB, null, null);
      createInsertingTrigger(
          "INSERT INTO "
              + TABLE
              + " (execution_trace_id, execution_inject_status_id, execution_message,"
              + " execution_status) SELECT 'm-0-' || g, '"
              + statusWithoutInject
              + "', '"
              + SEED_MESSAGE
              + "', 'INFO' FROM generate_series(1, 5) g ON CONFLICT DO NOTHING");

      // Act: a failure of the trigger in the locked step would fail the run
      new V6_20261112060000000__Add_tenant_id_to_execution_traces(1, 1L, 0, Long.MAX_VALUE)
          .migrate(connection);

      // Assert
      for (int i = 1; i <= 5; i++) {
        assertThat(tenantOf("m-0-" + i)).isEqualTo(DEFAULT_TENANT_UUID);
      }
      assertThat(tenantOf("m-2")).isEqualTo(tenantB);
      assertThat(tenantOf("m-3")).isEqualTo(tenantB);
      assertFullSchema();
    }

    @Test
    @DisplayName("A row inserted behind the walked ranges is filled by the drain")
    void given_rowInsertedBehindTheWalkedRanges_should_beFilledByTheDrain() throws SQLException {
      // Arrange
      makePending();
      seedTrace("m-2", statusOfB, null, null);
      seedTrace("m-3", statusOfB, null, null);
      createInsertingTrigger(
          "INSERT INTO "
              + TABLE
              + " (execution_trace_id, execution_inject_status_id, execution_message,"
              + " execution_status, execution_created_at) VALUES ('m-1', '"
              + statusWithoutInject
              + "', '"
              + SEED_MESSAGE
              + "', 'INFO', now() - interval '1 hour') ON CONFLICT DO NOTHING");

      // Act
      new V6_20261112060000000__Add_tenant_id_to_execution_traces(1, 1L, 0, Long.MAX_VALUE)
          .migrate(connection);

      // Assert
      assertThat(tenantOf("m-1")).isEqualTo(DEFAULT_TENANT_UUID);
      assertThat(tenantOf("m-2")).isEqualTo(tenantB);
      assertThat(tenantOf("m-3")).isEqualTo(tenantB);
      assertFullSchema();
    }

    /**
     * A before-update trigger that runs {@code insertSql} when the row {@code m-3} is updated, and
     * raises when a seeded row other than {@code m-1} is written while the session holds an
     * exclusive lock on the table (the row {@code m-1} is the one with an old creation time).
     */
    private void createInsertingTrigger(String insertSql) throws SQLException {
      execute(
          "CREATE FUNCTION test_trace_fail() RETURNS trigger AS $$ BEGIN"
              + " IF NEW.execution_trace_id = 'm-3' THEN "
              + insertSql
              + "; END IF;"
              + " IF EXISTS (SELECT 1 FROM pg_locks WHERE pid = pg_backend_pid()"
              + " AND locktype = 'relation' AND relation = '"
              + TABLE
              + "'::regclass AND mode = 'AccessExclusiveLock' AND granted) AND"
              + " NEW.execution_message = '"
              + SEED_MESSAGE
              + "' AND NEW.execution_trace_id NOT LIKE 'm-1%' THEN"
              + " RAISE EXCEPTION 'locked step wrote %', NEW.execution_trace_id; END IF;"
              + " RETURN NEW; END $$ LANGUAGE plpgsql");
      execute(
          "CREATE TRIGGER test_trace_fail BEFORE UPDATE ON "
              + TABLE
              + " FOR EACH ROW EXECUTE FUNCTION test_trace_fail()");
    }
  }

  /**
   * Measures the disk the migration needs on a large table: the old, one-transaction shape against
   * the paced, page-range shape. Prints the figures, asserts nothing on them. Run with {@code
   * -Dmigration.measure=true}.
   */
  @Nested
  @DisplayName("Measurement")
  @EnabledIfSystemProperty(named = "migration.measure", matches = "true")
  class Measurement {

    private static final int ROWS = 2_000_000;

    @Test
    @DisplayName("Peak size and WAL of the table, old shape against the page-range shape")
    void given_largeTable_should_printThePeakSizeAndWalOfBothShapes() throws Exception {
      // Arrange
      V6_20261112060000000__Add_tenant_id_to_execution_traces batched =
          new V6_20261112060000000__Add_tenant_id_to_execution_traces(
              10_000, null, 0, Long.MAX_VALUE);

      // Act
      long[] oldShape = measure(this::runOldShape);
      long[] newShape = measure(() -> batched.migrate(connection));

      // Assert
      System.out.printf(
          "MEASURE rows=%d old: start=%d MB peak=%d MB end=%d MB wal=%d MB | paced: start=%d MB"
              + " peak=%d MB end=%d MB wal=%d MB%n",
          ROWS,
          mb(oldShape[0]),
          mb(oldShape[1]),
          mb(oldShape[2]),
          mb(oldShape[3]),
          mb(newShape[0]),
          mb(newShape[1]),
          mb(newShape[2]),
          mb(newShape[3]));
      assertThat(count("SELECT count(*) FROM " + TABLE + " WHERE tenant_id IS NULL")).isZero();
    }

    private long mb(long bytes) {
      return bytes / (1024 * 1024);
    }

    /** Returns table size before, peak, after, and the WAL written, in bytes. */
    private long[] measure(SqlRunnable migrationRun) throws Exception {
      execute("DELETE FROM " + TABLE + " WHERE execution_message LIKE '" + SEED_MESSAGE + "%'");
      execute("VACUUM FULL " + TABLE);
      makePending();
      execute(
          "INSERT INTO "
              + TABLE
              + " (execution_trace_id, execution_inject_status_id,"
              + " execution_inject_test_status_id, execution_message, execution_status)"
              + " SELECT 'perf-' || g || '-' || gen_random_uuid(),"
              + " CASE WHEN g % 2 = 0 THEN '"
              + statusOfB
              + "' END, CASE WHEN g % 2 = 1 THEN '"
              + testStatusOfC
              + "' END, '"
              + SEED_MESSAGE
              + "' || repeat('x', 200), 'INFO' FROM generate_series(1, "
              + ROWS
              + ") g");
      execute("ANALYZE " + TABLE);
      execute("CHECKPOINT");
      long start = size();
      String lsn = (String) rows("SELECT pg_current_wal_lsn()::text").getFirst()[0];
      AtomicLong peak = new AtomicLong(start);
      AtomicBoolean running = new AtomicBoolean(true);
      Thread sampler =
          new Thread(
              () -> {
                try (Connection other = dataSource.getConnection();
                    Statement statement = other.createStatement()) {
                  while (running.get()) {
                    try (ResultSet result =
                        statement.executeQuery("SELECT pg_total_relation_size('" + TABLE + "')")) {
                      result.next();
                      peak.accumulateAndGet(result.getLong(1), Math::max);
                    }
                    Thread.sleep(20);
                  }
                } catch (Exception e) {
                  // sampling is best effort
                }
              });
      sampler.start();
      try {
        migrationRun.run();
      } finally {
        running.set(false);
        sampler.join();
      }
      long end = size();
      long wal =
          ((Number)
                  rows("SELECT pg_wal_lsn_diff(pg_current_wal_lsn(), '" + lsn + "'::pg_lsn)")
                      .getFirst()[0])
              .longValue();
      return new long[] {start, Math.max(peak.get(), end), end, wal};
    }

    private long size() throws SQLException {
      return count("SELECT pg_total_relation_size('" + TABLE + "')");
    }

    /** The statements of the old shape, all in one transaction. */
    private void runOldShape() throws SQLException {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        statement.executeUpdate(
            "UPDATE "
                + TABLE
                + " t SET tenant_id = i.tenant_id FROM injects_statuses s"
                + " JOIN injects i ON i.inject_id = s.status_inject"
                + " WHERE t.tenant_id IS NULL AND t.execution_inject_status_id = s.status_id");
        statement.executeUpdate(
            "UPDATE "
                + TABLE
                + " t SET tenant_id = i.tenant_id FROM injects_tests_statuses s"
                + " JOIN injects i ON i.inject_id = s.status_inject"
                + " WHERE t.tenant_id IS NULL AND t.execution_inject_test_status_id = s.status_id");
        statement.executeUpdate(
            "UPDATE "
                + TABLE
                + " SET tenant_id = '"
                + DEFAULT_TENANT_UUID
                + "' WHERE tenant_id IS NULL");
        statement.execute("ALTER TABLE " + TABLE + " ALTER COLUMN tenant_id SET NOT NULL");
        statement.execute(
            "ALTER TABLE "
                + TABLE
                + " ADD CONSTRAINT "
                + FOREIGN_KEY
                + " FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id) ON DELETE CASCADE");
        statement.execute("CREATE INDEX IF NOT EXISTS " + INDEX + " ON " + TABLE + "(tenant_id)");
        connection.commit();
      } finally {
        connection.setAutoCommit(true);
      }
    }
  }

  @FunctionalInterface
  private interface SqlRunnable {
    void run() throws Exception;
  }

  // -- assertions --

  private void assertFullSchema() throws SQLException {
    assertThat(
            rows(
                "SELECT is_nullable, column_default FROM information_schema.columns"
                    + " WHERE table_schema = current_schema() AND table_name = '"
                    + TABLE
                    + "' AND column_name = 'tenant_id'"))
        .as("tenant_id is NOT NULL with no default")
        .containsExactly(new Object[] {"NO", null});
    assertThat(
            count(
                "SELECT count(*) FROM pg_constraint WHERE conrelid = '"
                    + TABLE
                    + "'::regclass AND contype = 'f' AND conname = '"
                    + FOREIGN_KEY
                    + "' AND confrelid = 'tenants'::regclass AND confdeltype = 'c'"))
        .as("the foreign key to tenants is in place, ON DELETE CASCADE")
        .isEqualTo(1);
    assertThat(
            count(
                "SELECT count(*) FROM pg_index i JOIN pg_class c ON c.oid = i.indexrelid"
                    + " JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = i.indkey[0]"
                    + " WHERE c.relname = '"
                    + INDEX
                    + "' AND i.indrelid = '"
                    + TABLE
                    + "'::regclass AND i.indisvalid AND a.attname = 'tenant_id'"))
        .as("the index on tenant_id is in place")
        .isEqualTo(1);
  }

  // -- seeding --

  /** Puts the table in the state the migration starts from: nullable column, no FK, no index. */
  private void makePending() throws SQLException {
    execute("ALTER TABLE " + TABLE + " ALTER COLUMN tenant_id DROP NOT NULL");
    execute("ALTER TABLE " + TABLE + " DROP CONSTRAINT IF EXISTS " + FOREIGN_KEY);
    execute("DROP INDEX IF EXISTS " + INDEX);
  }

  private String seedTenant(String name) throws SQLException {
    String id = UUID.randomUUID().toString();
    execute(
        "INSERT INTO tenants (tenant_id, tenant_name) VALUES ('"
            + id
            + "', '"
            + name
            + "-"
            + id.substring(0, 8)
            + "')");
    seededTenants.add(id);
    return id;
  }

  private String seedInject(String tenant) throws SQLException {
    String id = UUID.randomUUID().toString();
    execute(
        "INSERT INTO injects (inject_id, inject_title, inject_all_teams, inject_enabled,"
            + " inject_depends_duration, tenant_id) VALUES ('"
            + id
            + "', 'seeded', false, true, 0, '"
            + tenant
            + "')");
    seededInjects.add(id);
    return id;
  }

  private String seedStatus(String inject) throws SQLException {
    String id = UUID.randomUUID().toString();
    execute(
        "INSERT INTO injects_statuses (status_id, status_name, status_inject) VALUES ('"
            + id
            + "', 'PENDING', "
            + quoted(inject)
            + ")");
    seededStatuses.add(id);
    return id;
  }

  private String seedTestStatus(String inject) throws SQLException {
    String id = UUID.randomUUID().toString();
    execute(
        "INSERT INTO injects_tests_statuses (status_id, status_name, status_inject) VALUES ('"
            + id
            + "', 'PENDING', '"
            + inject
            + "')");
    seededTestStatuses.add(id);
    return id;
  }

  /**
   * Inserts a trace carrying enough random filler, beyond {@link #SEED_MESSAGE}, that only a
   * handful of rows already spans more than one heap page.
   */
  private void seedTrace(String key, String status, String testStatus, String presetTenant)
      throws SQLException {
    execute(
        "INSERT INTO "
            + TABLE
            + " (execution_trace_id, execution_inject_status_id, execution_inject_test_status_id,"
            + " tenant_id, execution_message, execution_status) VALUES ('"
            + key
            + "', "
            + quoted(status)
            + ", "
            + quoted(testStatus)
            + ", "
            + quoted(presetTenant)
            + ", '"
            + SEED_MESSAGE
            + "-"
            + randomFiller()
            + "', 'INFO')");
  }

  /** Random, barely-compressible filler: distinct UUIDs, never a repeated block. */
  private static String randomFiller() {
    StringBuilder filler = new StringBuilder();
    for (int i = 0; i < 44; i++) {
      filler.append(UUID.randomUUID());
    }
    return filler.toString();
  }

  private static String quoted(String value) {
    return value == null ? "NULL" : "'" + value + "'";
  }

  private void deleteAll(String table, String column, List<String> ids) throws SQLException {
    for (String id : ids) {
      execute("DELETE FROM " + table + " WHERE " + column + " = '" + id + "'");
    }
  }

  // -- a connection that records the SQL text of every ad-hoc statement it runs --

  /**
   * Wraps {@code delegate} so that every {@code Statement.execute*(String)} call made through it
   * (never through a {@code PreparedStatement}, whose text is already a compile-time constant) is
   * recorded into {@code statements}, in order, before running on the real connection.
   */
  private static Connection recordingConnection(Connection delegate, List<String> statements) {
    return (Connection)
        Proxy.newProxyInstance(
            Connection.class.getClassLoader(),
            new Class<?>[] {Connection.class},
            (proxy, method, args) -> {
              if ("createStatement".equals(method.getName())
                  && (args == null || args.length == 0)) {
                return recordingStatement(delegate.createStatement(), statements);
              }
              return invoke(delegate, method, args);
            });
  }

  private static Statement recordingStatement(Statement delegate, List<String> statements) {
    return (Statement)
        Proxy.newProxyInstance(
            Statement.class.getClassLoader(),
            new Class<?>[] {Statement.class},
            (proxy, method, args) -> {
              if (args != null
                  && args.length > 0
                  && args[0] instanceof String sql
                  && method.getName().startsWith("execute")) {
                statements.add(sql);
              }
              return invoke(delegate, method, args);
            });
  }

  private static Object invoke(Object delegate, java.lang.reflect.Method method, Object[] args)
      throws Throwable {
    try {
      return method.invoke(delegate, args);
    } catch (InvocationTargetException e) {
      throw e.getCause();
    }
  }

  // -- plain JDBC helpers --

  private void execute(String sql) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }

  private String tenantOf(String key) throws SQLException {
    List<Object[]> rows =
        rows("SELECT tenant_id FROM " + TABLE + " WHERE execution_trace_id = '" + key + "'");
    assertThat(rows).as("trace %s", key).hasSize(1);
    return (String) rows.getFirst()[0];
  }

  private long count(String sql) throws SQLException {
    return ((Number) rows(sql).getFirst()[0]).longValue();
  }

  private List<Object[]> rows(String sql) throws SQLException {
    List<Object[]> rows = new ArrayList<>();
    try (Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery(sql)) {
      int columns = resultSet.getMetaData().getColumnCount();
      while (resultSet.next()) {
        Object[] row = new Object[columns];
        for (int column = 0; column < columns; column++) {
          row[column] = resultSet.getObject(column + 1);
        }
        rows.add(row);
      }
    }
    return rows;
  }

  private static Context contextOn(Connection connection) {
    return new Context() {
      @Override
      public Configuration getConfiguration() {
        return null;
      }

      @Override
      public Connection getConnection() {
        return connection;
      }
    };
  }
}
