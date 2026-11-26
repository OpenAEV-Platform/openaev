package io.openaev.migration;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Tenant;
import io.openaev.migration.V6_20261117160000000__Convert_findings_to_identity.LockPolicy;
import java.sql.Array;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The catalog steps of the findings identity migration against a pod of the older version, during a
 * rolling update, on the real database with one connection each: the old pod's transaction ({@code
 * oldPod}), the migration ({@code migrating}) and, where needed, any other query ({@code
 * otherQuery}). Not {@code @Transactional}: the steps commit, and every connection must see them.
 * The steps are idempotent, so the schema ends as the migration leaves it.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Migration V6_20261117160000000: catalog steps under concurrent locks")
class ConvertFindingsToIdentityLocksMigrationTest extends IntegrationTest {

  private static final String LOCK_NOT_AVAILABLE = "55P03";

  /** Far under the lock timeout of the steps: a query that queues behind them fails on it. */
  private static final Duration NO_WAIT_ALLOWED = Duration.ofMillis(300);

  /**
   * Waits longer than the deadlock check of the database (one second by default) on any lock it
   * queues for, and retries every 50 ms.
   */
  private static final LockPolicy PAST_DEADLOCK_CHECK =
      new LockPolicy(Duration.ofSeconds(3), 20, Duration.ofMillis(50));

  @Autowired private V6_20261117160000000__Convert_findings_to_identity migration;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private ExecutorService pool;
  private Connection oldPod;
  private Connection migrating;

  @BeforeEach
  void openConnections() throws SQLException {
    jdbc = new JdbcTemplate(dataSource);
    pool = Executors.newSingleThreadExecutor();
    oldPod = dataSource.getConnection();
    oldPod.setAutoCommit(false);
    migrating = dataSource.getConnection();
    migrating.setAutoCommit(false);
    // The last query of a pooled backend can be a ROLLBACK: start from a COMMIT
    read(migrating, "tenants");
    migrating.commit();
  }

  /**
   * A database that has not run the migration has the label column and the unique constraint the
   * older version writes through. The steps leave both in place.
   */
  @BeforeEach
  void restoreTheOlderVersionColumns() {
    jdbc.execute("ALTER TABLE findings ADD COLUMN IF NOT EXISTS finding_labels TEXT[]");
    jdbc.execute(
        "DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint"
            + " WHERE conname = 'unique_finding_constraint') THEN ALTER TABLE findings"
            + " ADD CONSTRAINT unique_finding_constraint"
            + " UNIQUE (finding_inject_id, finding_type, finding_value, finding_field);"
            + " END IF; END $$");
  }

  @AfterEach
  void closeConnections() throws SQLException {
    try {
      oldPod.rollback();
      pool.shutdown();
      if (!pool.awaitTermination(30, SECONDS)) {
        pool.shutdownNow();
      }
      migrating.rollback();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } finally {
      oldPod.setAutoCommit(true);
      oldPod.close();
      migrating.setAutoCommit(true);
      migrating.close();
    }
  }

  @Nested
  @DisplayName("Old pod transaction holding a lock the steps need")
  class OldPodTransaction {

    @Test
    @DisplayName(
        "An old pod reading injects then findings reads findings without waiting, and the steps"
            + " finish once it commits")
    void given_oldPodReadingInjectsThenFindings_should_readFindingsWithoutWaitingAndFinish()
        throws Exception {
      // Arrange: the old pod's asset indexing reads injects first
      setLocalLockTimeout(oldPod, NO_WAIT_ALLOWED);
      read(oldPod, "injects");
      int migratingPid = backendPid(migrating);
      Future<?> steps = changeCatalog(PAST_DEADLOCK_CHECK);
      awaitBlockedOn(migratingPid, "injects");

      // Act
      read(oldPod, "findings");
      oldPod.commit();

      // Assert
      steps.get(30, SECONDS);
      assertFinalCatalog();
    }

    @Test
    @DisplayName(
        "An old pod reading findings then injects reads injects without waiting, and the steps"
            + " finish once it commits")
    void given_oldPodReadingFindingsThenInjects_should_readInjectsWithoutWaitingAndFinish()
        throws Exception {
      // Arrange: the old pod's finding write and finding indexing take findings first. Another
      // session holds injects, so the steps reach the foreign key step before the old pod reads
      // findings. A step holding injects while it waits for findings makes the old pod's injects
      // read wait on it: a deadlock.
      setLocalLockTimeout(oldPod, NO_WAIT_ALLOWED);
      int migratingPid = backendPid(migrating);
      Future<?> steps;
      try (Connection otherQuery = dataSource.getConnection()) {
        otherQuery.setAutoCommit(false);
        try {
          read(otherQuery, "injects");
          steps = changeCatalog(PAST_DEADLOCK_CHECK);
          awaitBlockedOn(migratingPid, "injects");
          read(oldPod, "findings");
          otherQuery.commit();
          awaitBlockedOn(migratingPid, "findings");
        } finally {
          otherQuery.rollback();
          otherQuery.setAutoCommit(true);
        }
      }

      // Act
      read(oldPod, "injects");
      oldPod.commit();

      // Assert
      steps.get(30, SECONDS);
      assertFinalCatalog();
    }
  }

  @Nested
  @DisplayName("Steps retrying behind a long transaction")
  class RetryingSteps {

    @Test
    @DisplayName(
        "While the steps retry behind a long transaction on findings, reads and writes of injects"
            + " and reads of findings never wait on them")
    void given_stepsRetrying_should_letInjectsAndFindingsQueriesThrough() throws Exception {
      // Arrange
      read(oldPod, "findings");
      int migratingPid = backendPid(migrating);
      Future<?> steps = changeCatalog(PAST_DEADLOCK_CHECK);
      awaitBlockedOn(migratingPid, "findings");

      // Act: a query queued behind a waiting ACCESS EXCLUSIVE request waits as long as it does
      try (Connection otherQuery = dataSource.getConnection()) {
        otherQuery.setAutoCommit(false);
        try {
          for (int round = 0; round < 10; round++) {
            setLocalLockTimeout(otherQuery, NO_WAIT_ALLOWED);
            read(otherQuery, "injects");
            try (Statement statement = otherQuery.createStatement()) {
              statement.executeUpdate(
                  "UPDATE injects SET inject_updated_at = inject_updated_at"
                      + " WHERE inject_id = 'no-such-inject'");
            }
            read(otherQuery, "findings");
            otherQuery.commit();
            Thread.sleep(50);
          }
        } finally {
          otherQuery.rollback();
          otherQuery.setAutoCommit(true);
        }
      }

      // Assert
      assertThat(steps).isNotDone();
      oldPod.commit();
      steps.get(30, SECONDS);
      assertFinalCatalog();
    }

    @Test
    @DisplayName(
        "A lock never released makes the steps fail after their last attempt, naming the statement,"
            + " the tables and the way out")
    void given_lockNeverReleased_should_failWithTheStatementTheTablesAndTheWayOut()
        throws Exception {
      // Arrange
      read(oldPod, "findings");
      LockPolicy threeAttempts = new LockPolicy(Duration.ofMillis(100), 3, Duration.ofMillis(10));

      // Act
      Future<?> steps = changeCatalog(threeAttempts);

      // Assert
      assertThatThrownBy(() -> steps.get(30, SECONDS))
          .isInstanceOf(ExecutionException.class)
          .cause()
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("gave up after 3 attempts")
          .hasMessageContaining("\"LOCK TABLE findings IN ACCESS EXCLUSIVE MODE NOWAIT\"")
          .hasMessageContaining("a lock on injects, findings")
          .hasMessageContaining("`flyway repair`")
          .hasMessageContaining(
              "DELETE FROM migrations WHERE version = '6.20261117160000000' AND NOT success")
          .cause()
          .isInstanceOf(SQLException.class)
          .extracting(cause -> ((SQLException) cause).getSQLState())
          .isEqualTo(LOCK_NOT_AVAILABLE);
    }
  }

  @Nested
  @DisplayName("Final state")
  class FinalState {

    @Test
    @DisplayName("Steps run again on a migrated database end with the same catalog")
    void given_migratedDatabase_should_endWithTheSameCatalog() throws Exception {
      // Act
      migration.changeCatalog(migrating, LockPolicy.DEFAULT);
      migration.changeCatalog(migrating, LockPolicy.DEFAULT);

      // Assert
      assertFinalCatalog();
    }
  }

  @Nested
  @DisplayName("Older version on the changed catalog")
  class OlderVersion {

    private final String injectId = UUID.randomUUID().toString();

    @BeforeEach
    void createInject() {
      jdbc.update(
          "INSERT INTO injects (inject_id, inject_title, inject_all_teams, inject_enabled,"
              + " inject_depends_duration, tenant_id) VALUES (?, 'locks test', false, true, 0, ?)",
          injectId,
          Tenant.DEFAULT_TENANT_UUID);
    }

    @AfterEach
    void deleteRows() {
      jdbc.update("DELETE FROM findings WHERE finding_inject_id = ?", injectId);
      jdbc.update("DELETE FROM injects WHERE inject_id = ?", injectId);
    }

    @Test
    @DisplayName(
        "After the steps, the agent finding upsert and the entity read of the older version"
            + " succeed")
    void given_catalogSteps_should_acceptTheOlderVersionUpsertAndRead() throws Exception {
      // Arrange
      migration.changeCatalog(migrating, LockPolicy.DEFAULT);

      // Act
      Map<String, Object> inserted = olderVersionUpsert("first name");
      Map<String, Object> updated = olderVersionUpsert("second name");
      Map<String, Object> read =
          jdbc.queryForMap(
              "SELECT f.finding_id, f.finding_field, f.finding_type, f.finding_value,"
                  + " f.finding_value_display, f.finding_labels, f.finding_name,"
                  + " f.finding_inject_id FROM findings f WHERE f.finding_id = ?",
              inserted.get("id"));
      Object[] labels = (Object[]) ((Array) read.get("finding_labels")).getArray();

      // Assert
      assertThat(inserted).containsEntry("inserted", true);
      assertThat(updated).containsEntry("inserted", false).containsEntry("id", inserted.get("id"));
      assertThat(read)
          .containsEntry("finding_name", "second name")
          .containsEntry("finding_inject_id", injectId);
      assertThat(labels).containsExactly("label");
    }

    /** The agent finding upsert of the older version, column for column. */
    private Map<String, Object> olderVersionUpsert(String name) {
      return jdbc.queryForMap(
          """
          INSERT INTO findings
            (finding_id, finding_field, finding_type, finding_value, finding_value_display,
             finding_labels, finding_inject_id, finding_name, tenant_id)
          VALUES
            (gen_random_uuid(), ?, ?, ?, ?, CAST(? AS text[]), ?, ?, ?)
          ON CONFLICT (finding_inject_id, finding_field, finding_type, finding_value)
          DO UPDATE SET finding_name = EXCLUDED.finding_name,
            finding_value_display = EXCLUDED.finding_value_display,
            finding_updated_at = now()
          RETURNING finding_id AS id, (xmax = 0) AS inserted
          """,
          "ports",
          "PORT",
          "8080",
          "8080",
          "{label}",
          injectId,
          name,
          Tenant.DEFAULT_TENANT_UUID);
    }
  }

  private Future<?> changeCatalog(LockPolicy locks) {
    return pool.submit(
        () -> {
          migration.changeCatalog(migrating, locks);
          return null;
        });
  }

  private void assertFinalCatalog() {
    Map<String, Object> foreignKey =
        jdbc.queryForMap(
            "SELECT confrelid::regclass::text AS referenced, confdeltype AS on_delete,"
                + " convalidated AS validated FROM pg_constraint"
                + " WHERE conname = 'finding_inject_id_fk' AND conrelid = 'findings'::regclass");
    assertThat(foreignKey)
        .containsEntry("referenced", "injects")
        .containsEntry("on_delete", "n")
        .containsEntry("validated", true);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM pg_constraint WHERE conname = 'unique_finding_constraint'",
                Long.class))
        .isOne();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.columns WHERE table_name = 'findings'"
                    + " AND column_name = 'finding_labels'",
                Long.class))
        .isOne();
    assertThat(
            jdbc.queryForObject(
                "SELECT is_generated FROM information_schema.columns"
                    + " WHERE table_name = 'finding_locations'"
                    + " AND column_name = 'finding_location_key'",
                String.class))
        .isEqualTo("NEVER");
  }

  private static void setLocalLockTimeout(Connection connection, Duration timeout)
      throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute("SET LOCAL lock_timeout = '" + timeout.toMillis() + "ms'");
    }
  }

  private static void read(Connection connection, String table) throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery("SELECT count(*) FROM " + table)) {
      resultSet.next();
    }
  }

  private static int backendPid(Connection connection) throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery("SELECT pg_backend_pid()")) {
      resultSet.next();
      return resultSet.getInt(1);
    }
  }

  /**
   * Polls until the steps on this backend are blocked by a lock: queued for a lock of {@code
   * table}, or rolled back after a {@code NOWAIT} lock request failed.
   */
  private void awaitBlockedOn(int pid, String table) throws InterruptedException {
    long deadline = System.nanoTime() + SECONDS.toNanos(30);
    while (System.nanoTime() < deadline) {
      Long blocked =
          jdbc.queryForObject(
              "SELECT count(*) FROM pg_stat_activity a WHERE a.pid = ? AND (a.query = 'ROLLBACK'"
                  + " OR EXISTS (SELECT 1 FROM pg_locks l WHERE l.pid = a.pid AND NOT l.granted"
                  + " AND l.relation = to_regclass(?)))",
              Long.class,
              pid,
              table);
      if (blocked != null && blocked > 0) {
        return;
      }
      Thread.sleep(5);
    }
    fail("the steps on session %d were never blocked by a lock of %s", pid, table);
  }
}
