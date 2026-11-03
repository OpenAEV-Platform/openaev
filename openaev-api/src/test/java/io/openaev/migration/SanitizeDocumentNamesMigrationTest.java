package io.openaev.migration;

import static io.openaev.rest.document.DocumentService.DEFAULT_DOWNLOAD_FILE_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Tenant;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.stream.Stream;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.hibernate.Session;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies the stored document name repair: names carrying a path keep only their last component,
 * names with no last component fall back to the default download name, clean names are left
 * untouched, and re-running the migration is a no-op.
 *
 * <p>Rows are seeded and read over raw JDBC, like the migration itself runs, so the v2 tenant
 * statement inspector of {@code documents} does not apply. {@code @Transactional} so the seeded
 * rows and migration side effects roll back with the test transaction.
 */
@Transactional
@DisplayName("Sanitize document names migration")
class SanitizeDocumentNamesMigrationTest extends IntegrationTest {

  @Autowired private V6_20261015100000000__Sanitize_document_names migration;

  private static Stream<Arguments> namesCarryingAPath() {
    return Stream.of(
        Arguments.of("../../ba03_direct.txt", "ba03_direct.txt"),
        Arguments.of("..\\..\\evil.exe", "evil.exe"),
        Arguments.of("/etc/cron.d/evil", "evil"),
        Arguments.of("C:\\Windows\\System32\\evil.dll", "evil.dll"),
        Arguments.of("folder/sub folder/report v2.pdf", "report v2.pdf"));
  }

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

  private String insertDocument(String name) {
    String documentId = UUID.randomUUID().toString();
    withConnection(
        connection -> {
          try (PreparedStatement statement =
              connection.prepareStatement(
                  "INSERT INTO documents "
                      + "(document_id, document_name, document_type, document_target, tenant_id) "
                      + "VALUES (?, ?, 'text/plain', ?, ?)")) {
            statement.setString(1, documentId);
            statement.setString(2, name);
            statement.setString(3, documentId);
            statement.setString(4, Tenant.DEFAULT_TENANT_UUID);
            return statement.executeUpdate();
          }
        });
    return documentId;
  }

  private String nameOf(String documentId) {
    return withConnection(
        connection -> {
          try (PreparedStatement statement =
              connection.prepareStatement(
                  "SELECT document_name FROM documents WHERE document_id = ?")) {
            statement.setString(1, documentId);
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

  @ParameterizedTest(name = "\"{0}\" should become \"{1}\"")
  @MethodSource("namesCarryingAPath")
  @DisplayName("Given stored names carrying a path should keep only the last component")
  void given_stored_names_carrying_a_path_should_keep_only_the_last_component(
      String name, String expected) {
    // Arrange
    String documentId = insertDocument(name);

    // Act
    runMigration();

    // Assert
    assertEquals(expected, nameOf(documentId));
  }

  @ParameterizedTest(name = "\"{0}\" should become the default name")
  @ValueSource(strings = {"", "   ", "folder/", "..\\"})
  @DisplayName("Given stored names with no last component should fall back to the default name")
  void given_stored_names_with_no_last_component_should_fall_back_to_the_default_name(String name) {
    // Arrange
    String documentId = insertDocument(name);

    // Act
    runMigration();

    // Assert
    assertEquals(DEFAULT_DOWNLOAD_FILE_NAME, nameOf(documentId));
  }

  @ParameterizedTest(name = "\"{0}\" should stay unchanged")
  @ValueSource(strings = {"report.pdf", "..%2F..%2Fba03_encoded.txt", "  padded name.txt "})
  @DisplayName("Given stored names with no path should leave them unchanged")
  void given_stored_names_with_no_path_should_leave_them_unchanged(String name) {
    // Arrange
    String documentId = insertDocument(name);

    // Act
    runMigration();

    // Assert
    assertEquals(name, nameOf(documentId));
  }

  @Test
  @DisplayName("Given an already migrated name should be a no-op on re-run")
  void given_an_already_migrated_name_should_be_a_no_op_on_rerun() {
    // Arrange
    String documentId = insertDocument("../../ba03_direct.txt");
    runMigration();

    // Act
    runMigration();

    // Assert
    assertEquals("ba03_direct.txt", nameOf(documentId));
  }
}
