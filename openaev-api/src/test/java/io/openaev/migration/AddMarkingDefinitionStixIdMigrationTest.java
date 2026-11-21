package io.openaev.migration;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.MarkingDefinitionStixIds;
import io.openaev.database.model.Tenant;
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
 * Verifies the STIX id backfill: a definition without one gets the id OpenCTI gives it, a
 * definition whose id another definition of the tenant already carries stays without one, an id
 * already stored is left as it is, and a rerun changes nothing. Rows are seeded and read over the
 * session connection, like the migration runs; the test transaction rolls them back.
 */
@Transactional
@DisplayName("Migration V6_20261121210000000: add marking definition stix id")
class AddMarkingDefinitionStixIdMigrationTest extends IntegrationTest {

  @Autowired private V6_20261121210000000__Add_marking_definition_stix_id migration;

  @Test
  @DisplayName(
      "Given a definition without a stix id should backfill OpenCTI's id, a rerun is a no-op")
  void given_definitionWithoutStixId_should_backfillIt() {
    // Arrange
    String definition = "PAP:TEST-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    String id = seed("PAP", definition, null);

    // Act
    runMigration();
    runMigration();

    // Assert
    assertThat(stixIdOf(id)).isEqualTo(MarkingDefinitionStixIds.generate("PAP", definition));
  }

  @Test
  @DisplayName("Given an id another definition of the tenant carries should leave it without one")
  void given_idCarriedByAnotherDefinition_should_leaveItWithoutOne() {
    // Arrange - the default tenant's seeded TLP:CLEAR carries OpenCTI's id; TLP:WHITE is the same
    // OpenCTI marking, so it cannot carry it too.
    String sharedId = MarkingDefinitionStixIds.generate("TLP", "TLP:WHITE");
    assertThat(countCarrying(sharedId)).isEqualTo(1);
    String white = seed("TLP", "TLP:WHITE", null);

    // Act
    runMigration();

    // Assert
    assertThat(stixIdOf(white)).isNull();
  }

  @Test
  @DisplayName("Given a stored stix id should leave it as it is")
  void given_storedStixId_should_leaveIt() {
    // Arrange
    String imported = "marking-definition--11111111-2222-4333-8444-555555555555";
    String id = seed("PAP", "PAP:KEPT-" + UUID.randomUUID().toString().substring(0, 8), imported);

    // Act
    runMigration();

    // Assert
    assertThat(stixIdOf(id)).isEqualTo(imported);
  }

  private String seed(String type, String definition, String stixId) {
    String id = UUID.randomUUID().toString();
    execute(
        "INSERT INTO marking_definitions (marking_definition_id, marking_definition_type,"
            + " marking_definition_definition, marking_definition_color, marking_definition_order,"
            + " marking_definition_protected, marking_definition_created_at,"
            + " marking_definition_updated_at, marking_definition_stix_id, tenant_id)"
            + " VALUES ('%s', '%s', '%s', '#FFFFFF', 90, false, now(), now(), %s, '%s')",
        id,
        type,
        definition,
        stixId == null ? "NULL" : "'" + stixId + "'",
        Tenant.DEFAULT_TENANT_UUID);
    return id;
  }

  private int countCarrying(String stixId) {
    int[] count = new int[1];
    withConnection(
        connection -> {
          try (var statement =
              connection.prepareStatement(
                  "SELECT count(*) FROM marking_definitions WHERE marking_definition_stix_id = ?"
                      + " AND tenant_id = ?")) {
            statement.setString(1, stixId);
            statement.setString(2, Tenant.DEFAULT_TENANT_UUID);
            try (var resultSet = statement.executeQuery()) {
              resultSet.next();
              count[0] = resultSet.getInt(1);
            }
          }
        });
    return count[0];
  }

  private String stixIdOf(String id) {
    String[] stixId = new String[1];
    withConnection(
        connection -> {
          try (var statement =
              connection.prepareStatement(
                  "SELECT marking_definition_stix_id FROM marking_definitions"
                      + " WHERE marking_definition_id = ?")) {
            statement.setString(1, id);
            try (var resultSet = statement.executeQuery()) {
              resultSet.next();
              stixId[0] = resultSet.getString(1);
            }
          }
        });
    return stixId[0];
  }

  private void runMigration() {
    withConnection(connection -> migration.migrate(contextOn(connection)));
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
              } catch (RuntimeException e) {
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
