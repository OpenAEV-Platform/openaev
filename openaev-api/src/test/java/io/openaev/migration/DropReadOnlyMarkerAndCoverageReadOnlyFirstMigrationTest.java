package io.openaev.migration;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import java.sql.Connection;
import java.util.UUID;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.hibernate.Session;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * The migration drops the two read-only marker columns and the coverage_scenario_read_only_first
 * tenant setting rows an instance that ran V6_20261121160000000 holds, and a rerun changes nothing.
 * The columns and the row are seeded over the session connection; the test transaction rolls them
 * back.
 */
@Transactional
@DisplayName("Migration V6_20261124100000000: drop read-only marker and read-only-first setting")
class DropReadOnlyMarkerAndCoverageReadOnlyFirstMigrationTest extends IntegrationTest {

  @Autowired
  private V6_20261124100000000__Drop_read_only_marker_and_coverage_read_only_first migration;

  @Test
  @DisplayName("Given the columns and the setting row should drop them, a rerun is a no-op")
  void given_columnsAndSettingRow_should_dropThem() {
    // Arrange: the state V6_20261121160000000 left on a deployed instance
    String keptId = UUID.randomUUID().toString();
    execute(
        "ALTER TABLE injectors_contracts"
            + " ADD COLUMN IF NOT EXISTS injector_contract_read_only BOOLEAN NOT NULL DEFAULT false");
    execute(
        "ALTER TABLE payloads"
            + " ADD COLUMN IF NOT EXISTS payload_read_only BOOLEAN NOT NULL DEFAULT false");
    execute(
        "INSERT INTO parameters (parameter_id, parameter_key, parameter_value, tenant_id)"
            + " VALUES ('%s', 'coverage_scenario_read_only_first', 'true', '%s')",
        UUID.randomUUID(), DEFAULT_TENANT_UUID);
    execute(
        "INSERT INTO parameters (parameter_id, parameter_key, parameter_value, tenant_id)"
            + " VALUES ('%s', 'asset_discovery_tag_migration_test', 'true', '%s')",
        keptId, DEFAULT_TENANT_UUID);

    // Act
    runMigration();
    runMigration();

    // Assert
    assertThat(count(columnQuery("injectors_contracts", "injector_contract_read_only")))
        .isZero();
    assertThat(count(columnQuery("payloads", "payload_read_only"))).isZero();
    assertThat(
            count(
                "SELECT COUNT(*) FROM parameters"
                    + " WHERE parameter_key = 'coverage_scenario_read_only_first'"))
        .isZero();
    assertThat(count("SELECT COUNT(*) FROM parameters WHERE parameter_id = '" + keptId + "'"))
        .isOne();
  }

  private static String columnQuery(String table, String column) {
    return "SELECT COUNT(*) FROM information_schema.columns"
        + " WHERE table_name = '"
        + table
        + "' AND column_name = '"
        + column
        + "'";
  }

  private void runMigration() {
    withConnection(connection -> migration.migrate(contextOn(connection)));
  }

  private long count(String sql) {
    long[] result = new long[1];
    withConnection(
        connection -> {
          try (var statement = connection.createStatement();
              var row = statement.executeQuery(sql)) {
            assertThat(row.next()).isTrue();
            result[0] = row.getLong(1);
          }
        });
    return result[0];
  }

  @FunctionalInterface
  private interface ConnectionWork {
    void run(Connection connection) throws Exception;
  }

  private void withConnection(ConnectionWork work) {
    entityManager
        .unwrap(Session.class)
        .doWork(
            connection -> {
              try {
                work.run(connection);
              } catch (RuntimeException | AssertionError e) {
                throw e;
              } catch (Exception e) {
                throw new IllegalStateException(e);
              }
            });
  }

  private void execute(String sql, Object... args) {
    withConnection(
        connection -> {
          try (var statement = connection.createStatement()) {
            statement.execute(sql.formatted(args));
          }
        });
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
