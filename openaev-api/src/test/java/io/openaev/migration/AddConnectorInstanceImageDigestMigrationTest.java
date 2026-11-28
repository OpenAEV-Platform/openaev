package io.openaev.migration;

import static io.openaev.migration.MigrationSessions.contextOn;
import static io.openaev.migration.MigrationSessions.withConnection;
import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies the image digest column of connector instances: present and nullable after the
 * migration, and a rerun is a no-op. The test transaction ({@code @Transactional}) rolls back.
 */
@Transactional
@DisplayName("Migration V6_20261125180000000: add connector instance image digest")
class AddConnectorInstanceImageDigestMigrationTest extends IntegrationTest {

  @Autowired private V6_20261125180000000__Add_connector_instance_image_digest migration;

  @Test
  @DisplayName("Given the migration runs twice should leave one nullable digest column")
  void given_migrationRunTwice_should_leaveOneNullableDigestColumn() {
    // Act
    runMigration();
    runMigration();

    // Assert
    String[] nullable = new String[1];
    int[] count = new int[1];
    withConnection(
        entityManager,
        connection -> {
          try (var statement =
              connection.prepareStatement(
                  "SELECT is_nullable FROM information_schema.columns"
                      + " WHERE table_name = 'connector_instances'"
                      + " AND column_name = 'connector_instance_image_digest'")) {
            try (var resultSet = statement.executeQuery()) {
              while (resultSet.next()) {
                count[0]++;
                nullable[0] = resultSet.getString(1);
              }
            }
          }
        });
    assertThat(count[0]).isEqualTo(1);
    assertThat(nullable[0]).isEqualTo("YES");
  }

  private void runMigration() {
    withConnection(entityManager, connection -> migration.migrate(contextOn(connection)));
  }
}
