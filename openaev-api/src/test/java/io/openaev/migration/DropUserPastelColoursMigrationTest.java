package io.openaev.migration;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import java.sql.Connection;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.hibernate.Session;
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
    withConnection(connection -> addMigration.migrate(contextOn(connection)));

    // Act
    withConnection(connection -> dropMigration.migrate(contextOn(connection)));
    withConnection(connection -> dropMigration.migrate(contextOn(connection)));

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
        connection -> {
          try (var statement = connection.createStatement();
              var resultSet = statement.executeQuery(sql)) {
            value[0] = resultSet.next() ? resultSet.getString(1) : null;
          }
        });
    return value[0];
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
