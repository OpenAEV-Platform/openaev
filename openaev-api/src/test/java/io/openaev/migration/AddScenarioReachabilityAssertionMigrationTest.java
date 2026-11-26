package io.openaev.migration;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static io.openaev.migration.MigrationSessions.contextOn;
import static io.openaev.migration.MigrationSessions.withConnection;
import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * The reachability assertion migration adds its column with false on every existing scenario, and a
 * rerun changes nothing. Rows are seeded and read over the session connection; the test transaction
 * rolls them back.
 */
@Transactional
@DisplayName("Migration V6_20261122040000000: add scenario reachability assertion")
class AddScenarioReachabilityAssertionMigrationTest extends IntegrationTest {

  @Autowired private V6_20261122040000000__Add_scenario_reachability_assertion migration;

  @Test
  @DisplayName("Given an existing scenario should read false, a rerun is a no-op")
  void given_existingScenario_should_readFalse() {
    // Arrange
    String scenarioId = UUID.randomUUID().toString();
    runMigration();
    execute(
        "INSERT INTO scenarios (scenario_id, scenario_name, tenant_id)"
            + " VALUES ('%s', 'assertion-mig', '%s')",
        scenarioId, DEFAULT_TENANT_UUID);

    // Act
    runMigration();
    runMigration();

    // Assert
    withConnection(
        entityManager,
        connection -> {
          try (var read =
              connection.prepareStatement(
                  "SELECT scenario_reachability_assertion FROM scenarios WHERE scenario_id = ?")) {
            read.setString(1, scenarioId);
            try (var row = read.executeQuery()) {
              assertThat(row.next()).isTrue();
              assertThat(row.getBoolean(1)).isFalse();
            }
          }
        });
  }

  private void runMigration() {
    withConnection(entityManager, connection -> migration.migrate(contextOn(connection)));
  }

  private void execute(String sql, Object... args) {
    withConnection(
        entityManager,
        connection -> {
          try (var statement = connection.createStatement()) {
            statement.execute(sql.formatted(args));
          }
        });
  }
}
