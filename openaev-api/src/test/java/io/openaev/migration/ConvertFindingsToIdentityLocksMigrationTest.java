package io.openaev.migration;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import io.openaev.IntegrationTest;
import io.openaev.migration.V6_20261117160000000__Convert_findings_to_identity.LockPolicy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Map;
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

  /** Short waits so a test retries many times in a few seconds. */
  private static final LockPolicy QUICK_RETRIES =
      new LockPolicy(Duration.ofMillis(300), 200, Duration.ofMillis(50));

  /**
   * Waits longer than the deadlock check of the database (one second by default), as the default
   * policy does: a deadlock is found before the lock timeout ends it.
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
      // Arrange: the old pod's asset indexing reads injects first; its findings read must not
      // wait on the migration, or the two wait on each other.
      setLocalLockTimeout(oldPod, Duration.ofMillis(500));
      read(oldPod, "injects");
      int migratingPid = backendPid(migrating);

      // Act
      Future<?> steps = changeCatalog(PAST_DEADLOCK_CHECK);
      awaitWaitingOn(migratingPid, "injects");
      read(oldPod, "findings");
      oldPod.commit();

      // Assert
      steps.get(30, SECONDS);
      assertFinalCatalog();
    }

    @Test
    @DisplayName(
        "An old pod reading findings then injects makes the steps lose the deadlock, retry and"
            + " finish once it commits")
    void given_oldPodReadingFindingsThenInjects_should_retryTheDeadlockAndFinish()
        throws Exception {
      // Arrange: the old pod's finding write and finding indexing take findings first. Another
      // session holds injects, so the steps reach the foreign key step before the old pod reads
      // findings. The deadlock check fires on the steps, which waited first.
      int migratingPid = backendPid(migrating);
      setLocalLockTimeout(oldPod, Duration.ofSeconds(5));
      try (Connection otherQuery = dataSource.getConnection()) {
        otherQuery.setAutoCommit(false);
        try {
          read(otherQuery, "injects");

          // Act
          Future<?> steps = changeCatalog(PAST_DEADLOCK_CHECK);
          awaitWaitingOn(migratingPid, "injects");
          read(oldPod, "findings");
          otherQuery.commit();
          awaitWaitingOn(migratingPid, "findings");
          read(oldPod, "injects");
          oldPod.commit();

          // Assert
          steps.get(30, SECONDS);
        } finally {
          otherQuery.rollback();
          otherQuery.setAutoCommit(true);
        }
      }
      assertFinalCatalog();
    }
  }

  @Nested
  @DisplayName("Long transaction on findings")
  class LongTransaction {

    @Test
    @DisplayName(
        "While a long transaction holds findings, other queries on findings run between the"
            + " attempts, and the steps finish once it commits")
    void given_longTransactionOnFindings_should_letOtherQueriesThroughAndFinishAfterIt()
        throws Exception {
      // Arrange
      read(oldPod, "findings");
      int migratingPid = backendPid(migrating);
      Future<?> steps = changeCatalog(QUICK_RETRIES);
      awaitWaitingOn(migratingPid, "findings");

      // Act: a query queued behind a waiting ACCESS EXCLUSIVE request waits as long as it does
      try (Connection otherQuery = dataSource.getConnection()) {
        otherQuery.setAutoCommit(false);
        try {
          for (int round = 0; round < 5; round++) {
            setLocalLockTimeout(otherQuery, Duration.ofSeconds(2));
            read(otherQuery, "findings");
            otherQuery.commit();
            Thread.sleep(100);
          }
        } finally {
          otherQuery.rollback();
          otherQuery.setAutoCommit(true);
        }
      }
      oldPod.commit();

      // Assert
      steps.get(30, SECONDS);
      assertFinalCatalog();
    }

    @Test
    @DisplayName("A lock never released makes the steps fail after their last attempt")
    void given_lockNeverReleased_should_failAfterTheLastAttempt() throws Exception {
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
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.columns WHERE table_name = 'findings'"
                    + " AND column_name = 'finding_labels'",
                Long.class))
        .isZero();
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

  /** Polls {@code pg_locks} until this backend waits on a lock of {@code table}. */
  private void awaitWaitingOn(int pid, String table) throws InterruptedException {
    long deadline = System.nanoTime() + SECONDS.toNanos(30);
    while (System.nanoTime() < deadline) {
      Long waiting =
          jdbc.queryForObject(
              "SELECT count(*) FROM pg_locks WHERE pid = ? AND NOT granted"
                  + " AND relation = to_regclass(?)",
              Long.class,
              pid,
              table);
      if (waiting != null && waiting > 0) {
        return;
      }
      Thread.sleep(10);
    }
    fail("the session %d never waited on a lock of %s", pid, table);
  }
}
