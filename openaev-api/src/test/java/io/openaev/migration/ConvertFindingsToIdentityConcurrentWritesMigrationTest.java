package io.openaev.migration;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import io.openaev.IntegrationTest;
import io.openaev.migration.V6_20261117160000000__Convert_findings_to_identity.BatchRetry;
import io.openaev.migration.V6_20261117160000000__Convert_findings_to_identity.WalGuard;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The batch loop of the findings identity migration against a pod of the older version, during a
 * rolling update, on the real database with one connection each: the migration ({@code migrating}),
 * the old pod ({@code oldPod}) and, where needed, a session holding a row lock that pauses the
 * batch ({@code holder}). Not {@code @Transactional}: the batches commit, and every connection must
 * see them.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Migration V6_20261117160000000: batch loop under writes of the older version")
class ConvertFindingsToIdentityConcurrentWritesMigrationTest extends IntegrationTest {

  private static final String TRIGGER = "conversion_concurrency_trigger";
  private static final String ATTEMPTS = "conversion_concurrency_attempts";

  /** One page per range, no pause, no WAL-lag wait, a short pause before a batch runs again. */
  private final V6_20261117160000000__Convert_findings_to_identity conversion =
      new V6_20261117160000000__Convert_findings_to_identity(
          1L, 0, Long.MAX_VALUE, new BatchRetry(5, Duration.ofMillis(20)));

  @Autowired private DataSource dataSource;

  private ExecutorService pool;
  private Connection connection;
  private Connection migrating;
  private Connection oldPod;
  private Connection holder;
  private String marker;
  private String value;
  private final List<String> assets = new ArrayList<>();
  private final List<String> injects = new ArrayList<>();

  @BeforeEach
  void seedParents() throws SQLException {
    pool = Executors.newFixedThreadPool(2);
    connection = dataSource.getConnection();
    connection.setAutoCommit(true);
    migrating = dataSource.getConnection();
    migrating.setAutoCommit(false);
    oldPod = dataSource.getConnection();
    oldPod.setAutoCommit(false);
    holder = dataSource.getConnection();
    holder.setAutoCommit(false);
    marker = "conversion-concurrency-" + UUID.randomUUID();
    value = marker + "-value";
    // A database that has not run the migration has the label column and the unique constraint the
    // older version writes through; the migration leaves both in place
    execute("ALTER TABLE findings ADD COLUMN IF NOT EXISTS finding_labels TEXT[]");
    execute(
        "DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint"
            + " WHERE conname = 'unique_finding_constraint') THEN ALTER TABLE findings"
            + " ADD CONSTRAINT unique_finding_constraint"
            + " UNIQUE (finding_inject_id, finding_type, finding_value, finding_field);"
            + " END IF; END $$");
    for (int i = 0; i < 2; i++) {
      String asset = marker + "-asset-" + i;
      execute(
          "INSERT INTO assets (asset_id, asset_type, asset_name, tenant_id)"
              + " VALUES (?, 'Endpoint', ?, ?)",
          asset,
          asset,
          DEFAULT_TENANT_UUID);
      assets.add(asset);
      String inject = marker + "-inject-" + i;
      execute(
          "INSERT INTO injects (inject_id, inject_title, inject_all_teams, inject_enabled,"
              + " inject_depends_duration, tenant_id) VALUES (?, 'seeded', false, true, 0, ?)",
          inject,
          DEFAULT_TENANT_UUID);
      injects.add(inject);
    }
  }

  @AfterEach
  void removeSeed() throws Exception {
    try {
      holder.rollback();
      oldPod.rollback();
      pool.shutdown();
      if (!pool.awaitTermination(30, SECONDS)) {
        pool.shutdownNow();
      }
      migrating.rollback();
      execute("DROP TRIGGER IF EXISTS " + TRIGGER + " ON finding_occurrences");
      execute("DROP FUNCTION IF EXISTS " + TRIGGER + "()");
      execute("DROP SEQUENCE IF EXISTS " + ATTEMPTS);
      execute("DELETE FROM phishing_results WHERE phishing_result_token = ?", marker);
      execute("DELETE FROM findings WHERE finding_value LIKE ?", marker + "%");
      execute(
          "DELETE FROM finding_locations WHERE finding_location_asset_id = ANY (?)",
          connection.createArrayOf("varchar", assets.toArray()));
      for (String asset : assets) {
        execute("DELETE FROM assets WHERE asset_id = ?", asset);
      }
      for (String inject : injects) {
        execute("DELETE FROM injects WHERE inject_id = ?", inject);
      }
    } finally {
      assets.clear();
      injects.clear();
      for (Connection opened : List.of(holder, oldPod, migrating)) {
        opened.setAutoCommit(true);
        opened.close();
      }
      connection.close();
    }
  }

  @Nested
  @DisplayName("Deletes of the older version")
  class Deletes {

    @Test
    @DisplayName(
        "A simulation deleted by the older version while a batch converts its findings: the"
            + " batch finishes and converts the rows left")
    void given_simulationDeletedDuringABatch_should_convertTheRowsLeft() throws Exception {
      // Arrange: two rows of one identity, the second on the inject the old pod deletes; the
      // native cascade of a simulation delete detaches its findings and occurrences
      seedOldFinding(0, List.of(assets.get(0)));
      seedOldFinding(1, List.of(assets.get(0)));
      execute(oldPod, "DELETE FROM injects WHERE inject_id = ?", injects.get(1));
      Future<?> batches = submit(this::convertAll);
      awaitLockWait(backendPid(migrating), "");

      // Act
      oldPod.commit();

      // Assert
      batches.get(30, SECONDS);
      assertThat(count("SELECT count(*) FROM findings WHERE finding_value = ?", value))
          .isEqualTo(1);
      assertThat(convertedAt(assets.get(0))).isEqualTo(1);
    }

    @Test
    @DisplayName(
        "An asset deleted by the older version while a batch links a finding to it: the batch"
            + " is read again and converts the row on the assets left")
    void given_assetDeletedDuringABatch_should_readTheBatchAgain() throws Exception {
      // Arrange: the batch locks the row, reads both links, then waits on the deleted asset
      seedOldFinding(0, assets);
      execute(oldPod, "DELETE FROM assets WHERE asset_id = ?", assets.get(1));
      Future<?> batches = submit(this::convertAll);
      awaitLockWait(backendPid(migrating), "");

      // Act
      oldPod.commit();

      // Assert
      batches.get(30, SECONDS);
      assertThat(count("SELECT count(*) FROM findings WHERE finding_value = ?", value))
          .isEqualTo(1);
      assertThat(convertedAt(assets.get(0))).isEqualTo(1);
    }

    private Void convertAll() throws SQLException {
      conversion.walk(migrating, new WalGuard(null));
      conversion.drain(migrating, new WalGuard(null));
      return null;
    }
  }

  @Nested
  @DisplayName("Writes of the older version")
  class Writes {

    @Test
    @DisplayName(
        "A detection the older version writes on a row the batch converts waits for the batch"
            + " and is converted afterwards, not lost")
    void given_oldPodUpsertOnARowInTheBatch_should_waitAndBeConverted() throws Exception {
      // Arrange: a phishing result row lock pauses the batch after it copied the row's links
      String row = seedOldFinding(0, List.of(assets.get(0)));
      String result = seedPhishingResult(row);
      execute(
          holder,
          "UPDATE phishing_results SET phishing_result_updated_at = now()"
              + " WHERE phishing_result_id = ?",
          result);
      Future<?> batches =
          submit(
              () -> {
                conversion.walk(migrating, new WalGuard(null));
                return null;
              });
      awaitLockWait(backendPid(migrating), "UPDATE phishing_results");

      // Act: the old pod detects the same value on the same inject, from the second asset
      int oldPodPid = backendPid(oldPod);
      Future<?> detection =
          submit(
              () -> {
                String id = olderVersionUpsert();
                execute(
                    oldPod,
                    "INSERT INTO findings_assets (finding_id, asset_id) VALUES (?, ?)",
                    id,
                    assets.get(1));
                oldPod.commit();
                return null;
              });
      awaitDoneOrLockWait(detection, oldPodPid);
      holder.rollback();
      detection.get(30, SECONDS);
      batches.get(30, SECONDS);
      drain();

      // Assert: the detection from the second asset is converted, on that asset
      assertThat(convertedAt(assets.get(1))).isEqualTo(1);
      assertThat(convertedAt(assets.get(0))).isEqualTo(1);
    }

    /** The agent finding upsert of the older version, column for column. */
    private String olderVersionUpsert() throws SQLException {
      try (PreparedStatement statement =
          oldPod.prepareStatement(
              """
              INSERT INTO findings
                (finding_id, finding_field, finding_type, finding_value, finding_value_display,
                 finding_labels, finding_inject_id, finding_name, tenant_id)
              VALUES
                (gen_random_uuid(), 'output', 'Text', ?, ?, CAST('{label}' AS text[]), ?,
                 'detected again', ?)
              ON CONFLICT (finding_inject_id, finding_field, finding_type, finding_value)
              DO UPDATE SET finding_name = EXCLUDED.finding_name,
                finding_value_display = EXCLUDED.finding_value_display,
                finding_updated_at = now()
              RETURNING finding_id
              """)) {
        statement.setString(1, value);
        statement.setString(2, value);
        statement.setString(3, injects.get(0));
        statement.setString(4, DEFAULT_TENANT_UUID);
        try (ResultSet resultSet = statement.executeQuery()) {
          resultSet.next();
          return resultSet.getString(1);
        }
      }
    }
  }

  @Nested
  @DisplayName("Bound")
  class Bound {

    @ParameterizedTest(name = "{0}")
    @CsvSource({"foreign_key_violation, 23503", "deadlock_detected, 40P01"})
    @DisplayName(
        "A batch that meets a conflict on every attempt stops after the last one, rolled back,"
            + " with the way out")
    void given_conflictOnEveryAttempt_should_stopWithTheWayOut(String condition, String sqlState)
        throws Exception {
      // Arrange
      String row = seedOldFinding(0, List.of(assets.get(0)));
      failOccurrencesOf(injects.get(0), condition);
      V6_20261117160000000__Convert_findings_to_identity threeAttempts =
          new V6_20261117160000000__Convert_findings_to_identity(
              1L, 0, Long.MAX_VALUE, new BatchRetry(3, Duration.ofMillis(10)));

      // Act / Assert
      assertThatThrownBy(() -> threeAttempts.walk(migrating, new WalGuard(null)))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("gave up after 3 attempts on a batch")
          .hasMessageContaining("flyway repair")
          .hasMessageContaining("DELETE FROM migrations WHERE version = '6.20261117160000000'")
          .cause()
          .isInstanceOf(SQLException.class)
          .extracting(cause -> ((SQLException) cause).getSQLState())
          .isEqualTo(sqlState);
      assertThat(count("SELECT last_value FROM " + ATTEMPTS)).isEqualTo(3);
      assertThat(
              count(
                  "SELECT count(*) FROM findings WHERE finding_id = ? AND finding_value_hash IS"
                      + " NULL",
                  row))
          .isEqualTo(1);
    }

    private void failOccurrencesOf(String inject, String condition) throws SQLException {
      execute("CREATE SEQUENCE " + ATTEMPTS);
      execute(
          "CREATE FUNCTION "
              + TRIGGER
              + "() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN PERFORM nextval('"
              + ATTEMPTS
              + "'); RAISE EXCEPTION 'concurrent write' USING ERRCODE = '"
              + condition
              + "'; END $$");
      execute(
          "CREATE TRIGGER "
              + TRIGGER
              + " BEFORE INSERT ON finding_occurrences FOR EACH ROW"
              + " WHEN (NEW.finding_occurrence_inject_id = '"
              + inject
              + "') EXECUTE FUNCTION "
              + TRIGGER
              + "()");
    }
  }

  // -- Helpers --

  /** An old-shape row of {@link #value} on inject {@code inject}, linked to {@code linked}. */
  private String seedOldFinding(int inject, List<String> linked) throws SQLException {
    String id = marker + "-" + UUID.randomUUID();
    execute(
        "INSERT INTO findings (finding_id, finding_field, finding_type, finding_value,"
            + " finding_value_display, finding_name, finding_inject_id, tenant_id)"
            + " VALUES (?, 'output', 'Text', ?, ?, 'seeded', ?, ?)",
        id,
        value,
        value,
        injects.get(inject),
        DEFAULT_TENANT_UUID);
    for (String asset : linked) {
      execute("INSERT INTO findings_assets (finding_id, asset_id) VALUES (?, ?)", id, asset);
    }
    execute(
        "INSERT INTO finding_occurrences (finding_occurrence_id, finding_occurrence_finding_id,"
            + " finding_occurrence_inject_id, finding_occurrence_date, finding_occurrence_field)"
            + " VALUES (?, ?, ?, now(), 'output')",
        UUID.randomUUID().toString(),
        id,
        injects.get(inject));
    return id;
  }

  private String seedPhishingResult(String findingId) throws SQLException {
    String id = UUID.randomUUID().toString();
    execute(
        "INSERT INTO phishing_results (phishing_result_id, tenant_id, phishing_result_token,"
            + " phishing_result_inject, phishing_result_finding) VALUES (?, ?, ?, ?, ?)",
        id,
        DEFAULT_TENANT_UUID,
        marker,
        injects.get(0),
        findingId);
    return id;
  }

  private long convertedAt(String asset) throws SQLException {
    return count(
        "SELECT count(*) FROM findings f JOIN finding_locations l"
            + " ON l.finding_location_id = f.finding_location_id"
            + " WHERE f.finding_value = ? AND f.finding_value_hash IS NOT NULL"
            + " AND l.finding_location_asset_id = ?",
        value,
        asset);
  }

  // The next batches, once the concurrent writes are over
  private void drain() throws SQLException {
    connection.setAutoCommit(false);
    try {
      conversion.drain(connection, new WalGuard(null));
    } finally {
      connection.rollback();
      connection.setAutoCommit(true);
    }
  }

  private <T> Future<T> submit(Callable<T> task) {
    return pool.submit(task);
  }

  /** Polls until the session waits for a lock, on a statement starting with {@code query}. */
  private void awaitLockWait(int pid, String query) throws InterruptedException, SQLException {
    long deadline = System.nanoTime() + SECONDS.toNanos(30);
    while (System.nanoTime() < deadline) {
      if (waitsForLock(pid, query)) {
        return;
      }
      Thread.sleep(5);
    }
    fail("session %d never waited for a lock on \"%s\"", pid, query);
  }

  /** The old pod either finishes at once or waits for the batch: both outcomes are compared. */
  private void awaitDoneOrLockWait(Future<?> task, int pid)
      throws InterruptedException, SQLException {
    long deadline = System.nanoTime() + SECONDS.toNanos(30);
    while (System.nanoTime() < deadline) {
      if (task.isDone() || waitsForLock(pid, "")) {
        return;
      }
      Thread.sleep(5);
    }
    fail("session %d neither finished nor waited for a lock", pid);
  }

  private boolean waitsForLock(int pid, String query) throws SQLException {
    return count(
            "SELECT count(*) FROM pg_stat_activity WHERE pid = ? AND wait_event_type = 'Lock'"
                + " AND query LIKE ?",
            pid,
            query + "%")
        > 0;
  }

  private static int backendPid(Connection session) throws SQLException {
    try (Statement statement = session.createStatement();
        ResultSet resultSet = statement.executeQuery("SELECT pg_backend_pid()")) {
      resultSet.next();
      return resultSet.getInt(1);
    }
  }

  private void execute(String sql, Object... parameters) throws SQLException {
    execute(connection, sql, parameters);
  }

  private static void execute(Connection session, String sql, Object... parameters)
      throws SQLException {
    try (PreparedStatement statement = session.prepareStatement(sql)) {
      for (int i = 0; i < parameters.length; i++) {
        statement.setObject(i + 1, parameters[i]);
      }
      statement.execute();
    }
  }

  private long count(String sql, Object... parameters) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      for (int i = 0; i < parameters.length; i++) {
        statement.setObject(i + 1, parameters[i]);
      }
      try (ResultSet resultSet = statement.executeQuery()) {
        resultSet.next();
        return resultSet.getLong(1);
      }
    }
  }
}
