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
 * Verifies the "Pastel colours" column is gone after the drop migration, also when it was added
 * again first, and that a rerun changes nothing. The test transaction rolls back what it touches.
 */
@Transactional
@DisplayName("Migration V6_20261124000000000: drop user pastel colours")
class DropUserPastelColoursMigrationTest extends IntegrationTest {

  @Autowired private V6_20261123030000000__Add_user_pastel_colours addMigration;
  @Autowired private V6_20261124000000000__Drop_user_pastel_colours dropMigration;

  @Test
  @DisplayName("Given the column should drop it, a rerun is a no-op")
  void given_theColumn_should_dropIt() {
    // Arrange
    withConnection(entityManager, connection -> addMigration.migrate(contextOn(connection)));

    // Act
    withConnection(entityManager, connection -> dropMigration.migrate(contextOn(connection)));
    withConnection(entityManager, connection -> dropMigration.migrate(contextOn(connection)));

    // Assert
    String count =
        queryString(
            "SELECT CAST(COUNT(*) AS text) FROM information_schema.columns"
                + " WHERE table_name = 'users' AND column_name = 'user_pastel_colours'");
    assertThat(count).isEqualTo("0");
  }

  private String queryString(String sql) {
    String[] value = new String[1];
    withConnection(
        entityManager,
        connection -> {
          try (var statement = connection.createStatement();
              var resultSet = statement.executeQuery(sql)) {
            value[0] = resultSet.next() ? resultSet.getString(1) : null;
          }
        });
    return value[0];
  }
}
