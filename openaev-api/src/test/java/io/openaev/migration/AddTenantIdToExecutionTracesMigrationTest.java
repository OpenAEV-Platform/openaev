package io.openaev.migration;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static io.openaev.migration.V6_20261112060000000__Add_tenant_id_to_execution_traces.FOREIGN_KEY;
import static io.openaev.migration.V6_20261112060000000__Add_tenant_id_to_execution_traces.INDEX;
import static io.openaev.migration.V6_20261112060000000__Add_tenant_id_to_execution_traces.NEXT_IDS;
import static io.openaev.migration.V6_20261112060000000__Add_tenant_id_to_execution_traces.TABLE;
import static io.openaev.migration.V6_20261112060000000__Add_tenant_id_to_execution_traces.vacuumInterval;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openaev.IntegrationTest;
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
 * <p>The migration runs outside a transaction and uses {@code VACUUM}, which refuses to run in one,
 * so this class is not {@code @Transactional}: it seeds and runs the migration on its own
 * autocommit connection, and {@code @AfterEach} removes what it seeded and runs the migration once
 * more so the shared database ends as the rest of the suite expects it (column {@code NOT NULL},
 * foreign key and index in place).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Migration V6_20261112060000000: tenant_id on execution_traces")
class AddTenantIdToExecutionTracesMigrationTest extends IntegrationTest {

  private static final String SEED_MESSAGE = "migration-test-seed";

  /** Two rows a batch and a vacuum every two batches, so that a handful of rows crosses both. */
  private final V6_20261112060000000__Add_tenant_id_to_execution_traces small =
      new V6_20261112060000000__Add_tenant_id_to_execution_traces(2, 2);

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
    @DisplayName("More rows than one batch are all filled, over several commits")
    void given_moreRowsThanOneBatch_should_fillEveryRow() throws SQLException {
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
        "A run that fails after some batches leaves them committed, and a second run finishes")
    void given_failureAfterSomeBatches_should_leaveCommittedBatchesAndFinishOnRerun()
        throws SQLException {
      // Arrange
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

      // Assert: the batches before the failing one are committed, the failing one is not
      long filledAfterFailure =
          count(
              "SELECT count(*) FROM "
                  + TABLE
                  + " WHERE execution_message = '"
                  + SEED_MESSAGE
                  + "' AND tenant_id IS NOT NULL");
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
  @DisplayName("Vacuum interval")
  class VacuumInterval {

    @Test
    @DisplayName("An unknown estimate gives the fixed interval")
    void given_unknownEstimate_should_useTheFixedInterval() {
      // Act + Assert
      assertThat(vacuumInterval(-1, 10_000, 50)).isEqualTo(50);
      assertThat(vacuumInterval(0, 10_000, 50)).isEqualTo(50);
    }

    @Test
    @DisplayName("A small table gives the fixed interval")
    void given_smallTable_should_useTheFixedInterval() {
      // Act + Assert
      assertThat(vacuumInterval(2_000_000, 10_000, 50)).isEqualTo(50);
    }

    @Test
    @DisplayName("A large table gives the batches that cover five percent of its rows")
    void given_largeTable_should_coverFivePercentOfRowsPerInterval() {
      // Act + Assert
      assertThat(vacuumInterval(100_000_000, 10_000, 50)).isEqualTo(500);
      assertThat(vacuumInterval(100_000_001, 10_000, 50)).isEqualTo(501);
    }
  }

  @Nested
  @DisplayName("Batch picker")
  class BatchPicker {

    @Test
    @DisplayName("The picker reads the primary key index in order, with no sort and no table scan")
    void given_picker_should_walkThePrimaryKeyIndex() throws SQLException {
      // Arrange
      execute("SET enable_seqscan = off");
      try {
        // Act
        String plan =
            rows("EXPLAIN " + NEXT_IDS.replaceFirst("\\?", "''").replaceFirst("\\?", "10")).stream()
                .map(row -> (String) row[0])
                .reduce("", (left, right) -> left + "\n" + right);

        // Assert
        assertThat(plan).contains("Index").contains("execution_traces_pkey").doesNotContain("Sort");
      } finally {
        execute("RESET enable_seqscan");
      }
    }
  }

  @Nested
  @DisplayName("Rows inserted behind the cursor")
  class BehindTheCursor {

    @Test
    @DisplayName(
        "Recent rows inserted while the loop runs are filled by the drain, in several batches, and the locked step writes none")
    void given_rowsInsertedDuringTheLoop_should_beFilledByTheDrainInBatches() throws SQLException {
      // Arrange: with a batch of one row, the trigger inserts five rows that sort before the
      // cursor when the cursor reaches m-3; it then refuses any write made under the table lock
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
      new V6_20261112060000000__Add_tenant_id_to_execution_traces(1, 50).migrate(connection);

      // Assert
      for (int i = 1; i <= 5; i++) {
        assertThat(tenantOf("m-0-" + i)).isEqualTo(DEFAULT_TENANT_UUID);
      }
      assertThat(tenantOf("m-2")).isEqualTo(tenantB);
      assertThat(tenantOf("m-3")).isEqualTo(tenantB);
      assertFullSchema();
    }

    @Test
    @DisplayName(
        "A row with an old creation time inserted behind the cursor is filled by the locked step")
    void given_oldRowInsertedBehindTheCursor_should_beFilledByTheLockedStep() throws SQLException {
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
      new V6_20261112060000000__Add_tenant_id_to_execution_traces(1, 50).migrate(connection);

      // Assert
      assertThat(tenantOf("m-1")).isEqualTo(DEFAULT_TENANT_UUID);
      assertThat(tenantOf("m-2")).isEqualTo(tenantB);
      assertThat(tenantOf("m-3")).isEqualTo(tenantB);
      assertFullSchema();
    }

    /**
     * A before-update trigger that runs {@code insertSql} when the row {@code m-3} is updated, and
     * raises when a seeded row other than {@code m-1} is written while the session holds an
     * exclusive lock on the table (the row {@code m-1} is the one the locked step must fill).
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
   * Measures the disk the migration needs on a large table: the old shape (every statement in one
   * transaction) against the batched one. Prints the figures, asserts nothing on them. Run with
   * {@code -Dmigration.measure=true}.
   */
  @Nested
  @DisplayName("Measurement")
  @EnabledIfSystemProperty(named = "migration.measure", matches = "true")
  class Measurement {

    private static final int ROWS = 200_000;

    @Test
    @DisplayName("Peak size of the table, old shape against batched shape")
    void given_largeTable_should_printThePeakSizeOfBothShapes() throws Exception {
      // Arrange
      V6_20261112060000000__Add_tenant_id_to_execution_traces batched =
          new V6_20261112060000000__Add_tenant_id_to_execution_traces(1_000, 20);

      // Act
      long[] oldShape = measure(this::runOldShape);
      long[] newShape = measure(() -> batched.migrate(connection));

      // Assert
      System.out.printf(
          "MEASURE rows=%d old: start=%d MB peak=%d MB end=%d MB wal=%d MB | batched: start=%d MB"
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
            + "', 'INFO')");
  }

  private static String quoted(String value) {
    return value == null ? "NULL" : "'" + value + "'";
  }

  private void deleteAll(String table, String column, List<String> ids) throws SQLException {
    for (String id : ids) {
      execute("DELETE FROM " + table + " WHERE " + column + " = '" + id + "'");
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
