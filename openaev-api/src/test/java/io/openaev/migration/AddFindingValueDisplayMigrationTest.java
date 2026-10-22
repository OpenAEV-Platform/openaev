package io.openaev.migration;

import static io.openaev.utils.SensitiveValueMaskingUtils.MASK;
import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Tenant;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies the backfill of {@code findings.finding_value_display} (#7893): every finding gets the
 * value the platform displays, sensitive values are masked, an unknown type is masked whole, a row
 * that already has the column is left alone, a re-run changes nothing and the tenant of a row is
 * never touched.
 *
 * <p>Rows are seeded and read over raw JDBC, like the migration itself runs, so the v2 tenant
 * statement inspector of {@code findings} does not apply. {@code @Transactional} so the seeded rows
 * roll back with the test transaction.
 */
@Transactional
@DisplayName("Add finding value display migration")
class AddFindingValueDisplayMigrationTest extends IntegrationTest {

  @Autowired private V6_20261022151200000__Add_finding_value_display migration;

  private String injectId;

  private interface JdbcWork<T> {
    T run(Connection connection) throws Exception;
  }

  private <T> T withConnection(JdbcWork<T> work) {
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try {
                return work.run(connection);
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });
  }

  @BeforeEach
  void seedAnInject() {
    injectId = UUID.randomUUID().toString();
    withConnection(
        connection -> {
          try (PreparedStatement statement =
              connection.prepareStatement(
                  "INSERT INTO injects (inject_id, inject_title, inject_created_at,"
                      + " inject_updated_at, inject_depends_duration, inject_all_teams,"
                      + " inject_enabled, tenant_id)"
                      + " VALUES (?, 'migration inject', now(), now(), 0, false, true, ?)")) {
            statement.setString(1, injectId);
            statement.setString(2, Tenant.DEFAULT_TENANT_UUID);
            return statement.executeUpdate();
          }
        });
  }

  /** Inserts a finding the way a row written before the column existed looks: no display. */
  private String insertFinding(String type, String value) {
    String findingId = UUID.randomUUID().toString();
    withConnection(
        connection -> {
          try (PreparedStatement statement =
              connection.prepareStatement(
                  "INSERT INTO findings (finding_id, finding_field, finding_type, finding_value,"
                      + " finding_inject_id, finding_created_at, finding_updated_at, tenant_id)"
                      + " VALUES (?, 'field', ?, ?, ?, now(), now(), ?)")) {
            statement.setString(1, findingId);
            statement.setString(2, type);
            statement.setString(3, value);
            statement.setString(4, injectId);
            statement.setString(5, Tenant.DEFAULT_TENANT_UUID);
            return statement.executeUpdate();
          }
        });
    return findingId;
  }

  private String columnOf(String findingId, String column) {
    return withConnection(
        connection -> {
          try (PreparedStatement statement =
              connection.prepareStatement(
                  "SELECT " + column + " FROM findings WHERE finding_id = ?")) {
            statement.setString(1, findingId);
            try (ResultSet resultSet = statement.executeQuery()) {
              resultSet.next();
              return resultSet.getString(1);
            }
          }
        });
  }

  private void runMigration() {
    withConnection(
        connection -> {
          migration.migrate(
              new Context() {
                @Override
                public Configuration getConfiguration() {
                  return null;
                }

                @Override
                public Connection getConnection() {
                  return connection;
                }
              });
          return null;
        });
  }

  @Nested
  @DisplayName("When findings have no displayed value yet")
  class WhenFindingsHaveNoDisplayedValueYet {

    @Test
    @DisplayName("Should store the masked form of a sensitive value")
    void given_aSensitiveFinding_should_storeItsMaskedForm() {
      // Arrange
      String findingId = insertFinding("Credentials", "admin:motdepasse");

      // Act
      runMigration();

      // Assert
      assertThat(columnOf(findingId, "finding_value_display")).isEqualTo("admin:mo" + MASK);
      assertThat(columnOf(findingId, "finding_value")).isEqualTo("admin:motdepasse");
    }

    @Test
    @DisplayName("Should store a non sensitive value as it is")
    void given_aNonSensitiveFinding_should_storeTheValueAsItIs() {
      // Arrange
      String findingId = insertFinding("Text", "text_value");

      // Act
      runMigration();

      // Assert
      assertThat(columnOf(findingId, "finding_value_display")).isEqualTo("text_value");
    }

    @Test
    @DisplayName("Should mask the whole value of a type this version does not know")
    void given_anUnknownType_should_maskTheWholeValue() {
      // Arrange
      String findingId = insertFinding("NotAType", "admin:motdepasse");

      // Act
      runMigration();

      // Assert
      assertThat(columnOf(findingId, "finding_value_display"))
          .isEqualTo("ad" + MASK + ":mo" + MASK);
    }

    @Test
    @DisplayName("Should fill more findings than one batch holds")
    void given_moreFindingsThanOneBatch_should_fillThemAll() {
      // Arrange
      withConnection(
          connection -> {
            try (PreparedStatement statement =
                connection.prepareStatement(
                    "INSERT INTO findings (finding_id, finding_field, finding_type, finding_value,"
                        + " finding_inject_id, finding_created_at, finding_updated_at, tenant_id)"
                        + " SELECT gen_random_uuid(), 'field', 'Text', 'bulk-' || n, ?, now(),"
                        + " now(), ? FROM generate_series(1, 2500) AS n")) {
              statement.setString(1, injectId);
              statement.setString(2, Tenant.DEFAULT_TENANT_UUID);
              return statement.executeUpdate();
            }
          });

      // Act
      runMigration();

      // Assert
      Long remaining =
          withConnection(
              connection -> {
                try (PreparedStatement statement =
                        connection.prepareStatement(
                            "SELECT count(*) FROM findings WHERE finding_inject_id = ?"
                                + " AND finding_value_display IS NULL");
                    ResultSet resultSet = executeQuery(statement, injectId)) {
                  resultSet.next();
                  return resultSet.getLong(1);
                }
              });
      assertThat(remaining).isZero();
    }

    @Test
    @DisplayName("Should leave the tenant of every finding untouched")
    void given_aFinding_should_keepItsTenant() {
      // Arrange
      String findingId = insertFinding("Credentials", "admin:motdepasse");

      // Act
      runMigration();

      // Assert
      assertThat(columnOf(findingId, "tenant_id")).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
    }
  }

  @Nested
  @DisplayName("When the migration runs again")
  class WhenTheMigrationRunsAgain {

    @Test
    @DisplayName("Should leave an already filled displayed value untouched")
    void given_anAlreadyFilledValue_should_leaveItUntouched() {
      // Arrange
      String findingId = insertFinding("Credentials", "admin:motdepasse");
      runMigration();

      // Act
      runMigration();

      // Assert
      assertThat(columnOf(findingId, "finding_value_display")).isEqualTo("admin:mo" + MASK);
    }
  }

  private static ResultSet executeQuery(PreparedStatement statement, String parameter)
      throws Exception {
    statement.setString(1, parameter);
    return statement.executeQuery();
  }
}
