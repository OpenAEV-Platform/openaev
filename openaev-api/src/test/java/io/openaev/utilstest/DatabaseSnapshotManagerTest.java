package io.openaev.utilstest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.openaev.IntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The startup snapshot restore runs after every test class, on a database that scheduled threads of
 * long-lived Spring contexts may still be writing to. Its contract: after a restore the database
 * holds exactly the snapshot, and when a row cannot be restored the failure names the table and the
 * row, leaves the database as it was, and leaves the foreign key guard on.
 *
 * <p>Deliberately not {@code @Transactional}: the restore commits on its own connection and the
 * racing writer below runs on another one, so both must see committed state.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Startup snapshot restore")
class DatabaseSnapshotManagerTest extends IntegrationTest {

  private static final String PARAMETERS_ROWS = "SELECT * FROM parameters ORDER BY parameter_id";
  private static final String INSERT_PARAMETER =
      "INSERT INTO parameters (parameter_id, parameter_key, parameter_value, tenant_id)"
          + " VALUES (?, ?, ?, ?)";

  @Autowired private DatabaseSnapshotManager snapshotManager;
  @Autowired private JdbcTemplate jdbcTemplate;

  /** Converges the database on the snapshot and returns the parameters rows it holds. */
  private List<Map<String, Object>> snapshotOfParameters() {
    snapshotManager.restoreToSnapshotState();
    List<Map<String, Object>> rows = jdbcTemplate.queryForList(PARAMETERS_ROWS);
    // The instance-information migration seeds two platform rows, so the snapshot is never empty.
    assertThat(rows).hasSizeGreaterThanOrEqualTo(2);
    return rows;
  }

  @Nested
  @DisplayName("Rows left behind by a test")
  class RowsLeftBehindByATest {

    @Test
    @DisplayName("an added row is removed and a modified row is put back")
    void given_rowsAddedAndModifiedByATest_should_restoreExactlyTheSnapshot() {
      // Arrange
      List<Map<String, Object>> snapshot = snapshotOfParameters();
      jdbcTemplate.update(
          INSERT_PARAMETER,
          UUID.randomUUID().toString(),
          "snapshot_restore_test_leftover",
          "left by a test",
          null);
      jdbcTemplate.update(
          "UPDATE parameters SET parameter_value = ? WHERE parameter_id = ?",
          "changed by a test",
          snapshot.get(0).get("parameter_id"));

      // Act
      snapshotManager.restoreToSnapshotState();

      // Assert
      assertThat(jdbcTemplate.queryForList(PARAMETERS_ROWS)).containsExactlyElementsOf(snapshot);
    }
  }

  @Nested
  @DisplayName("A writer racing the restore")
  class AWriterRacingTheRestore {

    /**
     * Mirrors a scheduled service that finds its platform setting missing and re-creates it: the
     * writer polls the table and re-inserts a snapshot row the moment it sees the table empty. A
     * restore that empties the table in one committed step and refills it in another exposes that
     * window and then cannot re-insert the row it snapshotted.
     */
    @Test
    @DisplayName("a snapshot row re-created while the table is empty does not break the restore")
    void given_aWriterRecreatingASnapshotRowDuringTheRestore_should_restoreExactlyTheSnapshot()
        throws Exception {
      // Arrange
      List<Map<String, Object>> snapshot = snapshotOfParameters();
      Map<String, Object> target = snapshot.get(0);
      AtomicBoolean stop = new AtomicBoolean(false);
      AtomicBoolean sawEmptyTable = new AtomicBoolean(false);
      ExecutorService writer = Executors.newSingleThreadExecutor();
      Future<?> racing =
          writer.submit(
              () -> {
                while (!stop.get()) {
                  Integer count =
                      jdbcTemplate.queryForObject("SELECT count(*) FROM parameters", Integer.class);
                  if (count != null && count == 0) {
                    sawEmptyTable.set(true);
                    jdbcTemplate.update(
                        INSERT_PARAMETER,
                        target.get("parameter_id"),
                        target.get("parameter_key"),
                        "written during the restore",
                        target.get("tenant_id"));
                    return;
                  }
                }
              });

      // Act
      try {
        snapshotManager.restoreToSnapshotState();
      } finally {
        stop.set(true);
        writer.shutdown();
      }
      racing.get(10, TimeUnit.SECONDS);

      // Assert
      assertThat(sawEmptyTable)
          .as("the restore exposed an empty parameters table to another connection")
          .isFalse();
      assertThat(jdbcTemplate.queryForList(PARAMETERS_ROWS)).containsExactlyElementsOf(snapshot);
    }
  }

  @Nested
  @DisplayName("A row the restore cannot insert")
  class ARowTheRestoreCannotInsert {

    private static final String REJECTING_TRIGGER = "snapshot_restore_test_reject_row";

    @Test
    @DisplayName("the failure names the table and the row, and the database is left as it was")
    void given_aTriggerRejectingASnapshotRow_should_failNamingTheTableAndTheRow() {
      // Arrange
      List<Map<String, Object>> snapshot = snapshotOfParameters();
      String rejectedKey = (String) snapshot.get(0).get("parameter_key");
      // A fork killed mid-test must not leave the trigger behind for the next run of the stack.
      dropRejectingTrigger();
      // ENABLE ALWAYS: a regular trigger stays silent under the replica role the restore uses.
      jdbcTemplate.execute(
          "CREATE FUNCTION "
              + REJECTING_TRIGGER
              + "() RETURNS trigger AS $$ BEGIN RAISE EXCEPTION 'rejected by the test'; END $$"
              + " LANGUAGE plpgsql");
      jdbcTemplate.execute(
          "CREATE TRIGGER "
              + REJECTING_TRIGGER
              + " BEFORE INSERT ON parameters FOR EACH ROW WHEN (NEW.parameter_key = '"
              + rejectedKey
              + "') EXECUTE FUNCTION "
              + REJECTING_TRIGGER
              + "()");
      jdbcTemplate.execute("ALTER TABLE parameters ENABLE ALWAYS TRIGGER " + REJECTING_TRIGGER);
      try {
        // Act
        RuntimeException failure =
            assertThrows(RuntimeException.class, snapshotManager::restoreToSnapshotState);

        // Assert
        assertThat(failure.getMessage()).contains("parameters").contains(rejectedKey);
        assertThat(jdbcTemplate.queryForObject("SHOW session_replication_role", String.class))
            .as("the foreign key guard is back on after a failed restore")
            .isEqualTo("origin");
        assertThat(jdbcTemplate.queryForList(PARAMETERS_ROWS))
            .as("a failed restore leaves the database as it was")
            .containsExactlyElementsOf(snapshot);
      } finally {
        dropRejectingTrigger();
        snapshotManager.restoreToSnapshotState();
      }
    }

    private void dropRejectingTrigger() {
      jdbcTemplate.execute("DROP TRIGGER IF EXISTS " + REJECTING_TRIGGER + " ON parameters");
      jdbcTemplate.execute("DROP FUNCTION IF EXISTS " + REJECTING_TRIGGER + "()");
    }
  }
}
