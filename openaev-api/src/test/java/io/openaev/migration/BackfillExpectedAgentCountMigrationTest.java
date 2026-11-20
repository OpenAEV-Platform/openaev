package io.openaev.migration;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static io.openaev.migration.V6_20261120120000000__Backfill_expected_agent_count_on_closed_inject_statuses.UNKNOWN_DENSITY_PAGES_PER_BATCH;
import static io.openaev.migration.V6_20261120120000000__Backfill_expected_agent_count_on_closed_inject_statuses.pagesPerBatch;
import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.ExecutionStatus;
import io.openaev.database.model.ExecutionTraceStatus;
import io.openaev.utils.InjectStatusUtils;
import io.openaev.utils.InjectStatusUtils.CompleteTrace;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Not {@code @Transactional}: the migration commits one range at a time, so each test seeds on its
 * own autocommit connection and {@code @AfterEach} removes the seeded rows.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Migration V6_20261120120000000: expected agent count on closed inject statuses")
class BackfillExpectedAgentCountMigrationTest extends IntegrationTest {

  private static final String SEED = "backfill-expected-agent-count";

  /** One page a batch, no pause, a short lock timeout: deterministic and fast for tests. */
  private final V6_20261120120000000__Backfill_expected_agent_count_on_closed_inject_statuses
      small =
          new V6_20261120120000000__Backfill_expected_agent_count_on_closed_inject_statuses(
              1L, 0, "200ms", 50);

  @Autowired
  private V6_20261120120000000__Backfill_expected_agent_count_on_closed_inject_statuses migration;

  @Autowired private DataSource dataSource;

  private Connection connection;
  private final List<String> seededInjects = new ArrayList<>();
  private String asset;
  private String agentA;
  private String agentB;

  @BeforeEach
  void openConnectionAndSeedAgents() throws SQLException {
    connection = dataSource.getConnection();
    connection.setAutoCommit(true);
    asset = UUID.randomUUID().toString();
    execute(
        "INSERT INTO assets (asset_id, asset_name, asset_type) VALUES ('"
            + asset
            + "', '"
            + SEED
            + "', 'Endpoint')");
    agentA = seedAgent();
    agentB = seedAgent();
  }

  @AfterEach
  void removeSeed() throws SQLException {
    try {
      execute("DELETE FROM execution_traces WHERE execution_message = '" + SEED + "'");
      for (String inject : seededInjects) {
        execute("DELETE FROM injects_statuses WHERE status_inject = '" + inject + "'");
        execute("DELETE FROM injects_assets WHERE inject_id = '" + inject + "'");
        execute("DELETE FROM injects WHERE inject_id = '" + inject + "'");
      }
      execute("DELETE FROM agents WHERE agent_asset = '" + asset + "'");
      execute("DELETE FROM assets WHERE asset_id = '" + asset + "'");
    } finally {
      connection.close();
    }
  }

  @Test
  @DisplayName("The migration does not ask Flyway for a wrapping transaction")
  void given_migration_should_runOutsideATransaction() {
    // Act + Assert
    assertThat(migration.canExecuteInTransaction()).isFalse();
  }

  @Test
  @DisplayName("A closed status with no count gets its distinct agents with a COMPLETE trace")
  void given_legacyClosedStatus_should_countItsCompletedAgents() throws SQLException {
    // Arrange: agent A also has a non-COMPLETE trace and a second COMPLETE one
    String status = seedStatus("PARTIAL", null);
    seedTrace(status, agentA, "EXECUTION", "INFO");
    seedTrace(status, agentA, "COMPLETE", "ERROR");
    seedTrace(status, agentA, "COMPLETE", "ERROR");
    seedTrace(status, agentB, "COMPLETE", "EXECUTED");

    // Act
    small.migrate(connection);

    // Assert
    assertThat(expectedAgentCount(status)).isEqualTo(2);
  }

  @Test
  @DisplayName("A closed status dispatched to no agent gets 0")
  void given_agentlessClosedStatus_should_getZero() throws SQLException {
    // Arrange: closed by the platform, with no agent
    String status = seedStatus("ERROR", null);
    seedTrace(status, null, "COMPLETE", "ERROR");

    // Act
    small.migrate(connection);

    // Assert
    assertThat(expectedAgentCount(status)).isZero();
  }

  @Test
  @DisplayName("An in-flight status and a status with a count are left as they are")
  void given_inFlightOrCountedStatus_should_keepIt() throws SQLException {
    // Arrange
    String inFlight = seedStatus("PENDING", null);
    seedTrace(inFlight, agentA, "COMPLETE", "EXECUTED");
    String counted = seedStatus("EXECUTED", 3);
    seedTrace(counted, agentA, "COMPLETE", "EXECUTED");

    // Act
    small.migrate(connection);

    // Assert
    assertThat(expectedAgentCount(inFlight)).isNull();
    assertThat(expectedAgentCount(counted)).isEqualTo(3);
  }

  @Test
  @DisplayName(
      "A PARTIAL status whose succeeding agent was deleted before the upgrade gets one more agent"
          + " than is left, so no reader sees its stored verdict")
  void given_agentDeletedBeforeUpgrade_should_countOneMoreThanLeft() throws SQLException {
    // Arrange: A (GREEN) failed, the succeeding RED agent was deleted with its traces
    String status = seedStatus("PARTIAL", null, true);
    seedTrace(status, agentA, "COMPLETE", "ERROR");

    // Act
    small.migrate(connection);

    // Assert: a reader who sees A gets A's outcome, not the deleted success
    assertThat(expectedAgentCount(status)).isEqualTo(2);
    assertThat(readerSees(ExecutionStatus.PARTIAL, status, completeOf(agentA, "ERROR")))
        .isEqualTo(ExecutionStatus.ERROR);
  }

  @Test
  @DisplayName("A status sent to an asset whose traces were all purged shows NOT_VISIBLE")
  void given_tracesPurgedBeforeUpgrade_should_countOneAgent() throws SQLException {
    // Arrange: EXECUTED on an asset, every trace purged by the retention job
    String status = seedStatus("EXECUTED", null, true);

    // Act
    small.migrate(connection);

    // Assert
    assertThat(expectedAgentCount(status)).isEqualTo(1);
    assertThat(readerSees(ExecutionStatus.EXECUTED, status, List.of()))
        .isEqualTo(ExecutionStatus.NOT_VISIBLE);
  }

  @Test
  @DisplayName("A status set by hand, or sent to no asset, with no trace keeps 0 and its verdict")
  void given_statusWithNoTraceAndNothingToHide_should_getZero() throws SQLException {
    // Arrange: set by hand on an inject with an asset (no sent date), and sent to teams only
    String setByHand = seedStatus("EXECUTED", null, false);
    execute(
        "INSERT INTO injects_assets (inject_id, asset_id) SELECT status_inject, '"
            + asset
            + "' FROM injects_statuses WHERE status_id = '"
            + setByHand
            + "'");
    String toTeams = seedStatus("EXECUTED", null, false);
    execute(
        "UPDATE injects_statuses SET tracking_sent_date = now() WHERE status_id = '"
            + toTeams
            + "'");

    // Act
    small.migrate(connection);

    // Assert
    assertThat(expectedAgentCount(setByHand)).isZero();
    assertThat(expectedAgentCount(toTeams)).isZero();
    assertThat(readerSees(ExecutionStatus.EXECUTED, setByHand, List.of()))
        .isEqualTo(ExecutionStatus.EXECUTED);
  }

  @Test
  @DisplayName("A row locked by another transaction is filled once the lock is released")
  void given_lockedRow_should_retryUntilReleased() throws Exception {
    // Arrange
    String status = seedStatus("EXECUTED", null);
    seedTrace(status, agentA, "COMPLETE", "EXECUTED");
    CompletableFuture<Void> run;
    try (Connection writer = dataSource.getConnection()) {
      writer.setAutoCommit(false);
      try (Statement statement = writer.createStatement()) {
        statement.execute(
            "SELECT 1 FROM injects_statuses WHERE status_id = '" + status + "' FOR UPDATE");
        run =
            CompletableFuture.runAsync(
                () -> {
                  try (Connection migrating = dataSource.getConnection()) {
                    small.migrate(migrating);
                  } catch (SQLException e) {
                    throw new IllegalStateException(e);
                  }
                });
        // Longer than the lock timeout: the migration has timed out and retried at least once
        TimeUnit.MILLISECONDS.sleep(600);
        assertThat(run).isNotDone();
      } finally {
        writer.rollback();
      }
    }

    // Act
    run.get(30, TimeUnit.SECONDS);

    // Assert
    assertThat(expectedAgentCount(status)).isEqualTo(1);
  }

  @Test
  @DisplayName("A table with no statistics yet is walked in batches of a default size")
  void given_noStatistics_should_useTheDefaultBatchSize() {
    // Act + Assert
    assertThat(pagesPerBatch(-1, 0, 10_000)).isEqualTo(UNKNOWN_DENSITY_PAGES_PER_BATCH);
    assertThat(pagesPerBatch(50_000, 1_000, 10_000)).isEqualTo(200);
  }

  // -- HELPERS --

  private String seedAgent() throws SQLException {
    String id = UUID.randomUUID().toString();
    execute(
        "INSERT INTO agents (agent_id, agent_asset, agent_privilege, agent_deployment_mode,"
            + " agent_executed_by_user) VALUES ('"
            + id
            + "', '"
            + asset
            + "', 'admin', 'service', 'root')");
    return id;
  }

  private String seedStatus(String name, Integer expectedAgentCount) throws SQLException {
    return seedStatus(name, expectedAgentCount, false);
  }

  /**
   * @param sentToAsset sent to the seeded asset: a sent date and an asset target, as a launched
   *     inject has
   */
  private String seedStatus(String name, Integer expectedAgentCount, boolean sentToAsset)
      throws SQLException {
    String inject = UUID.randomUUID().toString();
    execute(
        "INSERT INTO injects (inject_id, inject_title, inject_all_teams, inject_enabled,"
            + " inject_depends_duration, tenant_id) VALUES ('"
            + inject
            + "', '"
            + SEED
            + "', false, true, 0, '"
            + DEFAULT_TENANT_UUID
            + "')");
    seededInjects.add(inject);
    String id = UUID.randomUUID().toString();
    execute(
        "INSERT INTO injects_statuses (status_id, status_name, status_inject,"
            + " status_expected_agent_count) VALUES ('"
            + id
            + "', '"
            + name
            + "', '"
            + inject
            + "', "
            + expectedAgentCount
            + ")");
    if (sentToAsset) {
      execute(
          "UPDATE injects_statuses SET tracking_sent_date = now() WHERE status_id = '" + id + "'");
      execute(
          "INSERT INTO injects_assets (inject_id, asset_id) VALUES ('"
              + inject
              + "', '"
              + asset
              + "')");
    }
    return id;
  }

  /** The status a reader who sees {@code visible} gets, with the backfilled count. */
  private ExecutionStatus readerSees(
      ExecutionStatus stored, String status, List<CompleteTrace> visible) throws SQLException {
    return InjectStatusUtils.visibleStatus(stored, expectedAgentCount(status), visible);
  }

  private static List<CompleteTrace> completeOf(String agent, String traceStatus) {
    return List.of(new CompleteTrace(ExecutionTraceStatus.valueOf(traceStatus), agent));
  }

  private void seedTrace(String status, String agent, String action, String traceStatus)
      throws SQLException {
    execute(
        "INSERT INTO execution_traces (execution_trace_id, execution_inject_status_id,"
            + " execution_agent_id, execution_action, execution_status, execution_message,"
            + " tenant_id) VALUES ('"
            + UUID.randomUUID()
            + "', '"
            + status
            + "', "
            + (agent == null ? "NULL" : "'" + agent + "'")
            + ", '"
            + action
            + "', '"
            + traceStatus
            + "', '"
            + SEED
            + "', '"
            + DEFAULT_TENANT_UUID
            + "')");
  }

  private Integer expectedAgentCount(String status) throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet row =
            statement.executeQuery(
                "SELECT status_expected_agent_count FROM injects_statuses WHERE status_id = '"
                    + status
                    + "'")) {
      assertThat(row.next()).isTrue();
      int count = row.getInt(1);
      return row.wasNull() ? null : count;
    }
  }

  private void execute(String sql) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }
}
