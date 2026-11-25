package io.openaev.migration;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.fail;

import io.openaev.IntegrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The migration against a platform on the earlier version that reads {@code payloads} and {@code
 * injectors_contracts} in one transaction during a rolling upgrade, on the real database with one
 * connection each. The migration runs the way Flyway runs it: inside a transaction when it accepts
 * one, in autocommit otherwise. The columns it adds are committed, so they are dropped around each
 * test.
 */
@DisplayName("Migration V6_20261121160000000: coverage safe mode, concurrent platform reads")
class CoverageScenarioSafeModeMigrationConcurrencyTest extends IntegrationTest {

  private static final String CONTRACT_COLUMN = "injector_contract_read_only";
  private static final String PAYLOAD_COLUMN = "payload_read_only";

  @Autowired private V6_20261121160000000__Coverage_scenario_safe_mode_and_approval migration;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;

  @BeforeEach
  void setUp() {
    jdbc = new JdbcTemplate(dataSource);
    dropColumns();
  }

  @AfterEach
  void dropColumns() {
    jdbc.execute("ALTER TABLE injectors_contracts DROP COLUMN IF EXISTS " + CONTRACT_COLUMN);
    jdbc.execute("ALTER TABLE payloads DROP COLUMN IF EXISTS " + PAYLOAD_COLUMN);
  }

  @Nested
  @DisplayName("Platform reading payloads, then injector contracts (a collector's payload upsert)")
  class PayloadsFirst {

    @Test
    @DisplayName("Given the platform holds payloads, should add both columns with no deadlock")
    void given_platformHoldsPayloadsThenReadsInjectorContracts_should_addBothColumns()
        throws Exception {
      ExecutorService pool = Executors.newSingleThreadExecutor();
      try (Connection platform = dataSource.getConnection();
          Connection upgrade = dataSource.getConnection()) {
        platform.setAutoCommit(false);
        try {
          // Arrange: the platform's transaction has read payloads, the migration starts.
          read(platform, "payloads");
          int upgradePid = backendPid(upgrade);
          Future<?> migrating = pool.submit(() -> migrate(upgrade));
          awaitWaiting(upgradePid);

          // Act: the same platform transaction reads injector contracts, then commits.
          read(platform, "injectors_contracts");
          platform.commit();
          migrating.get(60, SECONDS);
        } finally {
          platform.rollback();
        }
      } finally {
        pool.shutdownNow();
      }

      // Assert
      assertThat(columnExists("injectors_contracts", CONTRACT_COLUMN)).isTrue();
      assertThat(columnExists("payloads", PAYLOAD_COLUMN)).isTrue();
    }

    @Test
    @DisplayName("Given the migration waits for payloads, should let the platform read contracts")
    void given_migrationWaitsForPayloads_should_notBlockThePlatformReadOfInjectorContracts()
        throws Exception {
      ExecutorService pool = Executors.newSingleThreadExecutor();
      try (Connection platform = dataSource.getConnection();
          Connection upgrade = dataSource.getConnection()) {
        platform.setAutoCommit(false);
        try {
          // Arrange: the platform gives up on any lock it waits more than 100 ms for.
          execute(platform, "SET LOCAL lock_timeout = '100ms'");
          read(platform, "payloads");
          int upgradePid = backendPid(upgrade);
          Future<?> migrating = pool.submit(() -> migrate(upgrade));
          awaitWaiting(upgradePid);

          // Act and Assert: the platform's read is granted while the migration waits.
          assertThatCode(() -> read(platform, "injectors_contracts"))
              .as("the migration holds no lock on injectors_contracts while it waits for payloads")
              .doesNotThrowAnyException();
          platform.commit();
          migrating.get(60, SECONDS);
        } finally {
          platform.rollback();
        }
      } finally {
        pool.shutdownNow();
      }
      assertThat(columnExists("injectors_contracts", CONTRACT_COLUMN)).isTrue();
      assertThat(columnExists("payloads", PAYLOAD_COLUMN)).isTrue();
    }
  }

  @Nested
  @DisplayName("Platform reading injector contracts, then payloads (a contract with its payload)")
  class InjectorContractsFirst {

    @Test
    @DisplayName("Given the platform holds injector contracts, should add both columns")
    void given_platformHoldsInjectorContractsThenReadsPayloads_should_addBothColumns()
        throws Exception {
      ExecutorService pool = Executors.newSingleThreadExecutor();
      try (Connection platform = dataSource.getConnection();
          Connection upgrade = dataSource.getConnection()) {
        platform.setAutoCommit(false);
        try {
          // Arrange: the platform's transaction has read injector contracts, the migration starts.
          read(platform, "injectors_contracts");
          int upgradePid = backendPid(upgrade);
          Future<?> migrating = pool.submit(() -> migrate(upgrade));
          awaitWaiting(upgradePid);

          // Act: the same platform transaction reads payloads, then commits.
          read(platform, "payloads");
          platform.commit();
          migrating.get(60, SECONDS);
        } finally {
          platform.rollback();
        }
      } finally {
        pool.shutdownNow();
      }

      // Assert
      assertThat(columnExists("injectors_contracts", CONTRACT_COLUMN)).isTrue();
      assertThat(columnExists("payloads", PAYLOAD_COLUMN)).isTrue();
    }
  }

  /** Runs the migration as Flyway does: in a transaction when the migration accepts one. */
  private Void migrate(Connection connection) throws Exception {
    Context context =
        new Context() {
          @Override
          public Configuration getConfiguration() {
            return null;
          }

          @Override
          public Connection getConnection() {
            return connection;
          }
        };
    if (migration.canExecuteInTransaction()) {
      connection.setAutoCommit(false);
      try {
        migration.migrate(context);
        connection.commit();
      } catch (Exception e) {
        connection.rollback();
        throw e;
      } finally {
        connection.setAutoCommit(true);
      }
    } else {
      migration.migrate(context);
    }
    return null;
  }

  private static void read(Connection connection, String table) throws SQLException {
    execute(connection, "SELECT count(*) FROM " + table);
  }

  private static void execute(Connection connection, String sql) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }

  private static int backendPid(Connection connection) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement("SELECT pg_backend_pid()");
        ResultSet resultSet = statement.executeQuery()) {
      resultSet.next();
      return resultSet.getInt(1);
    }
  }

  /** Polls {@code pg_locks} until the session of this backend waits on a lock. */
  private void awaitWaiting(int pid) throws InterruptedException {
    long deadline = System.nanoTime() + SECONDS.toNanos(30);
    while (System.nanoTime() < deadline) {
      Long waiting =
          jdbc.queryForObject(
              "SELECT count(*) FROM pg_locks WHERE pid = ? AND NOT granted", Long.class, pid);
      if (waiting != null && waiting > 0) {
        return;
      }
      Thread.sleep(20);
    }
    fail("the session %d never waited on a lock", pid);
  }

  private boolean columnExists(String table, String column) {
    Long count =
        jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.columns"
                + " WHERE table_name = ? AND column_name = ?",
            Long.class,
            table,
            column);
    return count != null && count == 1;
  }
}
