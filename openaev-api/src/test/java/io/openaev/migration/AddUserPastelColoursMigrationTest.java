package io.openaev.migration;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.User;
import java.sql.Connection;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.hibernate.Session;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies the "Pastel colours" column: nullable, left unset on existing users (unset reads as the
 * pastel default, so nobody is flipped), and a rerun changes nothing. The test transaction rolls
 * back what it touches.
 */
@Transactional
@DisplayName("Migration V6_20261123030000000: add user pastel colours")
class AddUserPastelColoursMigrationTest extends IntegrationTest {

  @Autowired private V6_20261123030000000__Add_user_pastel_colours migration;

  @Test
  @DisplayName("Given existing users should add a nullable column left unset, a rerun is a no-op")
  void given_existingUsers_should_addANullableColumnLeftUnset() {
    // Act
    runMigration();
    runMigration();

    // Assert
    String nullable =
        queryString(
            "SELECT is_nullable FROM information_schema.columns WHERE table_name = 'users'"
                + " AND column_name = 'user_pastel_colours'");
    assertThat(nullable).isEqualTo("YES");
    String adminValue =
        queryString(
            "SELECT CAST(user_pastel_colours AS text) FROM users WHERE user_id = '"
                + User.ADMIN_UUID
                + "'");
    assertThat(adminValue).isNull();
  }

  @Test
  @DisplayName("Given an unset preference should read as pastel, and an absent update keeps it")
  void given_anUnsetPreference_should_readAsPastel() {
    // Arrange
    User user = new User();

    // Assert
    assertThat(user.getPastelColours()).isTrue();
    user.setPastelColours(false);
    assertThat(user.getPastelColours()).isFalse();
    user.setPastelColours(null);
    assertThat(user.getPastelColours()).isFalse();
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
