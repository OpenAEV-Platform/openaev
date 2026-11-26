package io.openaev.migration;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.service.EsIndexingUtils;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * The migration requesting an in-place re-feed of the {@code asset} index so that every document
 * carries the classification fields, whatever a pod of the previous version did to the row during a
 * rolling deploy (issue #9888).
 *
 * <p>Rows are seeded through the raw JDBC connection, as Flyway runs the migration, and roll back
 * with the {@code @Transactional} test transaction.
 */
@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Asset classification re-feed migration")
class AssetClassificationRefeedMigrationTest extends IntegrationTest {

  private static final String ASSET_MODEL = "asset";
  private static final String OTHER_MODEL = "finding";
  private static final Instant REGULAR_CURSOR = Instant.parse("2026-11-25T18:00:00Z");

  @Autowired
  private V6_20261126090000000__Request_asset_refeed_for_classification_fields migration;

  @AfterEach
  void tearDown() {
    // The registrations are static: one left behind would leak into the next test
    EsIndexingUtils.drainRefeedRequests();
  }

  // -- Helpers --

  private void execute(String sql, Object... parameters) {
    entityManager
        .unwrap(Session.class)
        .doWork(
            connection -> {
              try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int index = 0; index < parameters.length; index++) {
                  statement.setObject(index + 1, parameters[index]);
                }
                statement.executeUpdate();
              }
            });
  }

  private void putCursor(String model, Instant cursor, String lastId) {
    execute("DELETE FROM indexing_status WHERE indexing_status_type = ?", model);
    execute(
        "INSERT INTO indexing_status"
            + " (indexing_status_type, indexing_status_indexing_date, indexing_status_last_id)"
            + " VALUES (?, ?, ?)",
        model,
        Timestamp.from(cursor),
        lastId);
  }

  private void removeCursor(String model) {
    execute("DELETE FROM indexing_status WHERE indexing_status_type = ?", model);
  }

  /** The stored cursor and keyset id of a model, or empty when its row is missing. */
  private Optional<StoredCursor> storedCursor(String model) {
    StoredCursor[] found = new StoredCursor[1];
    entityManager
        .unwrap(Session.class)
        .doWork(
            connection -> {
              try (PreparedStatement statement =
                  connection.prepareStatement(
                      "SELECT indexing_status_indexing_date, indexing_status_last_id"
                          + " FROM indexing_status WHERE indexing_status_type = ?")) {
                statement.setString(1, model);
                try (ResultSet resultSet = statement.executeQuery()) {
                  if (resultSet.next()) {
                    found[0] =
                        new StoredCursor(
                            resultSet.getTimestamp(1).toInstant(), resultSet.getString(2));
                  }
                }
              }
            });
    return Optional.ofNullable(found[0]);
  }

  private record StoredCursor(Instant cursor, String lastId) {}

  private void runMigration() {
    entityManager
        .unwrap(Session.class)
        .doWork(
            connection -> {
              try {
                migration.migrate(
                    new Context() {
                      @Override
                      public Configuration getConfiguration() {
                        return null;
                      }

                      @Override
                      public java.sql.Connection getConnection() {
                        return connection;
                      }
                    });
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });
  }

  // -- Tests --

  @Nested
  @DisplayName("When a pod of the previous version re-created the asset row")
  class WhenTheCursorWasAdvancedByAnOldPod {

    @Test
    @DisplayName("Moves the cursor back to epoch, clears the keyset id and registers the re-feed")
    void given_cursorAdvancedByOldPod_should_moveItToEpochAndRegisterTheRefeed() {
      // Arrange: the row an old pod re-created and advanced past the documents it wrote
      putCursor(ASSET_MODEL, REGULAR_CURSOR, "some-asset-id");

      // Act
      runMigration();

      // Assert
      assertThat(storedCursor(ASSET_MODEL)).contains(new StoredCursor(Instant.EPOCH, null));
      assertThat(EsIndexingUtils.drainRefeedRequests()).containsExactly(ASSET_MODEL);
    }

    @Test
    @DisplayName("Leaves the cursors of the other models untouched")
    void given_otherModelCursor_should_leaveItUntouched() {
      // Arrange
      putCursor(ASSET_MODEL, REGULAR_CURSOR, null);
      putCursor(OTHER_MODEL, REGULAR_CURSOR, "some-finding-id");

      // Act
      runMigration();

      // Assert
      assertThat(storedCursor(OTHER_MODEL))
          .contains(new StoredCursor(REGULAR_CURSOR, "some-finding-id"));
    }
  }

  @Nested
  @DisplayName("When the asset cursor is not a regular one")
  class WhenTheCursorIsNotRegular {

    @Test
    @DisplayName("Leaves a pending reset request untouched, and still registers the re-feed")
    void given_pendingResetRequest_should_leaveItAndRegisterTheRefeed() {
      // Arrange
      putCursor(ASSET_MODEL, EsIndexingUtils.REINDEX_REQUESTED_CURSOR, null);

      // Act
      runMigration();

      // Assert
      assertThat(storedCursor(ASSET_MODEL))
          .contains(new StoredCursor(EsIndexingUtils.REINDEX_REQUESTED_CURSOR, null));
      assertThat(EsIndexingUtils.drainRefeedRequests()).containsExactly(ASSET_MODEL);
    }

    @Test
    @DisplayName("Leaves a missing row missing, and still registers the re-feed")
    void given_missingRow_should_notInsertItAndRegisterTheRefeed() {
      // Arrange: the previous migration deleted the row in the same startup
      removeCursor(ASSET_MODEL);

      // Act
      runMigration();

      // Assert
      assertThat(storedCursor(ASSET_MODEL)).isEmpty();
      assertThat(EsIndexingUtils.drainRefeedRequests())
          .as("an old pod may re-create the row before the boot check")
          .containsExactly(ASSET_MODEL);
    }
  }
}
