package io.openaev.migration;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.hibernate.Session;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies the migration drops the {@code asset} indexing cursor only, and that a rerun changes
 * nothing. The test transaction rolls back what it touches.
 */
@Transactional
@DisplayName("Migration V6_20261124090000000: reindex assets with classification fields")
class ReindexAssetsWithClassificationFieldsMigrationTest extends IntegrationTest {

  private static final String ASSET_MODEL = "asset";
  private static final String OTHER_MODEL = "asset-classification-test-model";

  @Autowired private V6_20261124090000000__Reindex_assets_with_classification_fields migration;

  @Test
  @DisplayName("Given an asset cursor should drop it and keep the others, a rerun is a no-op")
  void given_anAssetCursor_should_dropItAndKeepTheOthers() {
    // Arrange
    putCursor(ASSET_MODEL);
    putCursor(OTHER_MODEL);

    // Act
    withConnection(connection -> migration.migrate(contextOn(connection)));
    withConnection(connection -> migration.migrate(contextOn(connection)));

    // Assert
    assertThat(countCursors(ASSET_MODEL)).isEqualTo("0");
    assertThat(countCursors(OTHER_MODEL)).isEqualTo("1");
  }

  private void putCursor(String model) {
    withConnection(
        connection -> {
          try (PreparedStatement delete =
              connection.prepareStatement(
                  "DELETE FROM indexing_status WHERE indexing_status_type = ?")) {
            delete.setString(1, model);
            delete.executeUpdate();
          }
          try (PreparedStatement insert =
              connection.prepareStatement(
                  "INSERT INTO indexing_status"
                      + " (indexing_status_type, indexing_status_indexing_date)"
                      + " VALUES (?, ?)")) {
            insert.setString(1, model);
            insert.setTimestamp(2, Timestamp.from(Instant.parse("2026-11-01T00:00:00Z")));
            insert.executeUpdate();
          }
        });
  }

  private String countCursors(String model) {
    String[] value = new String[1];
    withConnection(
        connection -> {
          try (PreparedStatement statement =
              connection.prepareStatement(
                  "SELECT CAST(COUNT(*) AS text) FROM indexing_status"
                      + " WHERE indexing_status_type = ?")) {
            statement.setString(1, model);
            try (var resultSet = statement.executeQuery()) {
              value[0] = resultSet.next() ? resultSet.getString(1) : null;
            }
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
