package io.openaev.migration;

import static io.openaev.migration.MigrationSessions.awaitLockConflict;
import static io.openaev.migration.MigrationSessions.backendPid;
import static io.openaev.migration.MigrationSessions.columnExists;
import static io.openaev.migration.MigrationSessions.execute;
import static io.openaev.migration.MigrationSessions.read;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.openaev.IntegrationTest;
import java.sql.Connection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
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
          Future<?> migrating = pool.submit(() -> MigrationSessions.migrate(migration, upgrade));
          awaitLockConflict(jdbc, upgradePid);

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
      assertThat(columnExists(jdbc, "injectors_contracts", CONTRACT_COLUMN)).isTrue();
      assertThat(columnExists(jdbc, "payloads", PAYLOAD_COLUMN)).isTrue();
    }

    @Test
    @DisplayName("Given the migration cannot take payloads, should let the platform read contracts")
    void given_migrationCannotTakePayloads_should_notBlockThePlatformReadOfInjectorContracts()
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
          Future<?> migrating = pool.submit(() -> MigrationSessions.migrate(migration, upgrade));
          awaitLockConflict(jdbc, upgradePid);

          // Act and Assert: the platform's read is granted while the migration is under way.
          assertThatCode(() -> read(platform, "injectors_contracts"))
              .as("the migration holds no lock on injectors_contracts while payloads is taken")
              .doesNotThrowAnyException();
          platform.commit();
          migrating.get(60, SECONDS);
        } finally {
          platform.rollback();
        }
      } finally {
        pool.shutdownNow();
      }
      assertThat(columnExists(jdbc, "injectors_contracts", CONTRACT_COLUMN)).isTrue();
      assertThat(columnExists(jdbc, "payloads", PAYLOAD_COLUMN)).isTrue();
    }

    @Test
    @DisplayName("Given the migration cannot take payloads, should not queue another payloads read")
    void given_migrationCannotTakePayloads_should_notQueueAnotherReadOfPayloads() throws Exception {
      ExecutorService pool = Executors.newSingleThreadExecutor();
      try (Connection platform = dataSource.getConnection();
          Connection reader = dataSource.getConnection();
          Connection upgrade = dataSource.getConnection()) {
        platform.setAutoCommit(false);
        reader.setAutoCommit(false);
        try {
          // Arrange: the platform holds payloads; another request gives up after 100 ms.
          read(platform, "payloads");
          execute(reader, "SET LOCAL lock_timeout = '100ms'");
          int upgradePid = backendPid(upgrade);
          Future<?> migrating = pool.submit(() -> MigrationSessions.migrate(migration, upgrade));
          awaitLockConflict(jdbc, upgradePid);

          // Act and Assert: the other request reads payloads while the migration is under way.
          assertThatCode(() -> read(reader, "payloads"))
              .as("the migration never waits in the lock queue of payloads")
              .doesNotThrowAnyException();
          reader.commit();
          platform.commit();
          migrating.get(60, SECONDS);
        } finally {
          reader.rollback();
          platform.rollback();
        }
      } finally {
        pool.shutdownNow();
      }
      assertThat(columnExists(jdbc, "payloads", PAYLOAD_COLUMN)).isTrue();
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
          Future<?> migrating = pool.submit(() -> MigrationSessions.migrate(migration, upgrade));
          awaitLockConflict(jdbc, upgradePid);

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
      assertThat(columnExists(jdbc, "injectors_contracts", CONTRACT_COLUMN)).isTrue();
      assertThat(columnExists(jdbc, "payloads", PAYLOAD_COLUMN)).isTrue();
    }
  }
}
