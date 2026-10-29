package io.openaev.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import io.openaev.IntegrationTest;
import io.openaev.config.EngineConfig;
import io.openaev.database.repository.IndexingStatusRepository;
import io.openaev.engine.IndexResetEpochReassertion;
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
 * The migration requesting an in-place re-feed of the {@code finding} index, and the delayed second
 * re-feed it hands over to {@link IndexResetEpochReassertion}.
 *
 * <p>Rows are seeded through the raw JDBC connection, as Flyway runs the migration, and roll back
 * with the {@code @Transactional} test transaction. The end-to-end re-feed against the engine lives
 * in {@code FindingRefeedEndToEndTest}, which cannot run in a test transaction.
 */
@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Finding re-feed migration")
class FindingRefeedMigrationTest extends IntegrationTest {

  private static final String FINDING_MODEL = "finding";
  private static final String OTHER_MODEL = "asset";
  private static final Instant REGULAR_CURSOR = Instant.parse("2026-10-01T12:00:00Z");

  /**
   * A model with no {@code indexing_status} row: a re-assertion scheduled on it updates nothing, so
   * no task left behind can move a real cursor in the middle of other test classes.
   */
  private static final String FICTITIOUS_MODEL = "finding-refeed-test-model";

  @Autowired
  private V6_20261022140626000__Request_finding_refeed_after_sensitive_value_masking migration;

  @Autowired private IndexResetEpochReassertion epochReassertion;
  @Autowired private IndexingStatusRepository indexingStatusRepository;
  @Autowired private EngineConfig engineConfig;

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
  @DisplayName("When the finding index has a regular cursor")
  class WhenTheCursorIsRegular {

    @Test
    @DisplayName("Moves the cursor back to epoch, clears the keyset id and registers the re-feed")
    void given_regularCursor_should_moveItToEpochAndRegisterTheRefeed() {
      // Arrange
      putCursor(FINDING_MODEL, REGULAR_CURSOR, "some-finding-id");

      // Act
      runMigration();

      // Assert
      assertThat(storedCursor(FINDING_MODEL)).contains(new StoredCursor(Instant.EPOCH, null));
      assertThat(EsIndexingUtils.drainRefeedRequests()).containsExactly(FINDING_MODEL);
    }

    @Test
    @DisplayName("Leaves the cursors of the other models untouched")
    void given_otherModelCursor_should_leaveItUntouched() {
      // Arrange
      putCursor(FINDING_MODEL, REGULAR_CURSOR, null);
      putCursor(OTHER_MODEL, REGULAR_CURSOR, "some-asset-id");

      // Act
      runMigration();

      // Assert
      assertThat(storedCursor(OTHER_MODEL))
          .contains(new StoredCursor(REGULAR_CURSOR, "some-asset-id"));
    }
  }

  @Nested
  @DisplayName("When the finding cursor is not a regular one")
  class WhenTheCursorIsNotRegular {

    @Test
    @DisplayName("Leaves a pending reset request untouched, and still registers the re-feed")
    void given_pendingResetRequest_should_leaveItAndRegisterTheRefeed() {
      // Arrange
      putCursor(FINDING_MODEL, EsIndexingUtils.REINDEX_REQUESTED_CURSOR, null);

      // Act
      runMigration();

      // Assert
      assertThat(storedCursor(FINDING_MODEL))
          .contains(new StoredCursor(EsIndexingUtils.REINDEX_REQUESTED_CURSOR, null));
      assertThat(EsIndexingUtils.drainRefeedRequests())
          .as("the reset's own re-assertion does not outlast the old pods")
          .containsExactly(FINDING_MODEL);
    }

    @Test
    @DisplayName("Leaves a missing row missing, and still registers the re-feed")
    void given_missingRow_should_notInsertItAndRegisterTheRefeed() {
      // Arrange
      removeCursor(FINDING_MODEL);

      // Act
      runMigration();

      // Assert
      assertThat(storedCursor(FINDING_MODEL)).isEmpty();
      assertThat(EsIndexingUtils.drainRefeedRequests())
          .as("an old pod may re-create the row at epoch before the boot check")
          .containsExactly(FINDING_MODEL);
    }
  }

  @Nested
  @DisplayName("When the second re-feed is scheduled")
  class WhenTheSecondRefeedIsScheduled {

    @Test
    @DisplayName("A positive delay schedules the epoch re-assertion")
    void given_positiveDelay_should_schedule() {
      // Act
      boolean scheduled = epochReassertion.schedule(FICTITIOUS_MODEL, 3600);

      // Assert
      assertThat(scheduled).isTrue();
    }

    @Test
    @DisplayName("A delay of 0 disables the epoch re-assertion")
    void given_zeroDelay_should_notSchedule() {
      // Act
      boolean scheduled = epochReassertion.schedule(FICTITIOUS_MODEL, 0);

      // Assert
      assertThat(scheduled).isFalse();
    }

    @Test
    @DisplayName("Once ready, every registered model is scheduled with the re-feed delay, once")
    void given_registeredRefeed_should_scheduleItWithTheRefeedDelayAndDrainIt() {
      // Arrange
      long defaultDelay = engineConfig.getIndexingRefeedReassertDelaySeconds();
      IndexResetEpochReassertion reassertion =
          spy(new IndexResetEpochReassertion(engineConfig, indexingStatusRepository));
      EsIndexingUtils.requestRefeedAtStartup(FICTITIOUS_MODEL);
      try {
        engineConfig.setIndexingRefeedReassertDelaySeconds(1234);

        // Act
        reassertion.scheduleRequestedRefeeds();

        // Assert
        verify(reassertion).schedule(FICTITIOUS_MODEL, 1234);
        assertThat(EsIndexingUtils.drainRefeedRequests()).isEmpty();
      } finally {
        engineConfig.setIndexingRefeedReassertDelaySeconds(defaultDelay);
      }
    }
  }
}
