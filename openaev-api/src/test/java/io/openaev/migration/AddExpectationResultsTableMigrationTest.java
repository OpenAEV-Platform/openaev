package io.openaev.migration;

import static io.openaev.migration.MigrationSessions.contextOn;
import static io.openaev.migration.V6_20261023184049806__Add_expectation_results_table.CREATE_TABLE;
import static io.openaev.migration.V6_20261023184049806__Add_expectation_results_table.EXPECTATION_INJECT_MODEL;
import static io.openaev.migration.V6_20261023184049806__Add_expectation_results_table.FOREIGN_KEY_VIOLATION;
import static io.openaev.migration.V6_20261023184049806__Add_expectation_results_table.INDEX;
import static io.openaev.migration.V6_20261023184049806__Add_expectation_results_table.JSON_COLUMN;
import static io.openaev.migration.V6_20261023184049806__Add_expectation_results_table.RANGE_INSERT;
import static io.openaev.migration.V6_20261023184049806__Add_expectation_results_table.RESULTS_TABLE;
import static io.openaev.migration.V6_20261023184049806__Add_expectation_results_table.SOURCE_TABLE;
import static io.openaev.migration.V6_20261023184049806__Add_expectation_results_table.UNKNOWN_DENSITY_PAGES_PER_BATCH;
import static io.openaev.migration.V6_20261023184049806__Add_expectation_results_table.pagesPerBatch;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.openaev.IntegrationTest;
import io.openaev.migration.V6_20261023184049806__Add_expectation_results_table.TableSize;
import io.openaev.migration.V6_20261023184049806__Add_expectation_results_table.WalGuard;
import io.openaev.service.EsIndexingUtils;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Not {@code @Transactional}: the migration runs outside a transaction, so each test seeds on its
 * own autocommit connection, and {@code @AfterEach} runs the migration again to leave the shared
 * schema complete.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Migration V6_20261023184049806: inject_expectation_results table")
class AddExpectationResultsTableMigrationTest extends IntegrationTest {

  /** One page a batch, no pause, no WAL-lag wait: deterministic and fast for tests. */
  private final V6_20261023184049806__Add_expectation_results_table small =
      new V6_20261023184049806__Add_expectation_results_table(1L, 0, Long.MAX_VALUE);

  @Autowired private V6_20261023184049806__Add_expectation_results_table migration;
  @Autowired private DataSource dataSource;

  private Connection connection;
  private final List<String> seededExpectations = new ArrayList<>();
  private final List<String> seededInjects = new ArrayList<>();
  private final List<String> seededTenants = new ArrayList<>();

  private String tenant;
  private String inject;

  @BeforeEach
  void openConnectionAndSeedParents() throws SQLException {
    connection = dataSource.getConnection();
    connection.setAutoCommit(true);

    tenant = seedTenant("expectation-results-owner");
    inject = seedInject(tenant);
  }

  @AfterEach
  void removeSeedAndRestoreSchema() throws SQLException {
    try {
      execute("DROP TRIGGER IF EXISTS test_result_fail ON " + RESULTS_TABLE);
      execute("DROP FUNCTION IF EXISTS test_result_fail()");
      deleteAll("injects_expectations", "inject_expectation_id", seededExpectations);
      deleteAll("injects", "inject_id", seededInjects);
      deleteAll("tenants", "tenant_id", seededTenants);
      migration.migrate(connection);
    } finally {
      // The migration always (re)requests a reindex: clean the sentinel row and the in-process
      // registration so no other test class sees a pending reset for this model.
      execute(
          "DELETE FROM indexing_status WHERE indexing_status_type = '"
              + EXPECTATION_INJECT_MODEL
              + "'");
      EsIndexingUtils.reindexRequestFulfilled(EXPECTATION_INJECT_MODEL);
      seededExpectations.clear();
      seededInjects.clear();
      seededTenants.clear();
      connection.close();
    }
  }

  @Nested
  @DisplayName("Final state")
  class FinalState {

    @Test
    @DisplayName("The migration does not ask Flyway for a wrapping transaction")
    void given_migration_should_runOutsideATransaction() {
      // Act + Assert
      assertThat(migration.canExecuteInTransaction()).isFalse();
    }

    @Test
    @DisplayName(
        "A platform where the table does not exist yet ends with the table, its index, and no JSON column")
    void given_pendingPlatform_should_endWithTheFullSchema() throws Exception {
      // Arrange
      makePending();
      String expectation = seedExpectation(inject, "[{\"result\":\"SUCCESS\"}]");

      // Act
      migration.migrate(contextOn(connection));

      // Assert
      assertFullSchema();
      assertThat(resultsOf(expectation)).hasSize(1);
    }

    @Test
    @DisplayName("Running the migration again on an already-migrated platform changes nothing")
    void given_migratedPlatform_should_rerunWithoutChange() throws Exception {
      // Arrange
      makePending();
      String expectation = seedExpectation(inject, "[{\"result\":\"SUCCESS\"}]");
      migration.migrate(connection);

      // Act
      migration.migrate(connection);

      // Assert
      assertFullSchema();
      assertThat(resultsOf(expectation)).hasSize(1);
    }
  }

  @Nested
  @DisplayName("Row mapping")
  class RowMapping {

    @Test
    @DisplayName("Every field of a result is copied, and the date falls back to the expectation")
    void given_fullResult_should_copyEveryField() throws Exception {
      // Arrange
      makePending();
      String expectation =
          seedExpectation(
              inject,
              "[{\"sourceId\":\"src-1\",\"sourceType\":\"security-platform\","
                  + "\"sourceName\":\"CrowdStrike\",\"sourceAssetId\":\"asset-1\","
                  + "\"sourcePlatform\":\"Windows\",\"result\":\"SUCCESS\",\"score\":\"42\","
                  + "\"metadata\":{\"k\":\"v\"}}]");
      execute(
          "UPDATE "
              + SOURCE_TABLE
              + " SET inject_expectation_updated_at = '2020-01-01T00:00:00Z'"
              + " WHERE inject_expectation_id = '"
              + expectation
              + "'");

      // Act
      migration.migrate(connection);

      // Assert
      List<Object[]> rows = resultsOf(expectation);
      assertThat(rows).hasSize(1);
      Object[] row = rows.getFirst();
      assertThat(row)
          .containsExactly(
              "src-1",
              "security-platform",
              "CrowdStrike",
              "asset-1",
              "Windows",
              "SUCCESS",
              new java.math.BigDecimal("42"));
      assertThat(
              count(
                  "SELECT count(*) FROM "
                      + RESULTS_TABLE
                      + " WHERE inject_expectation_result_inject_expectation_id = '"
                      + expectation
                      + "' AND inject_expectation_result_date = TIMESTAMPTZ '2020-01-01T00:00:00Z'"))
          .as("the date falls back to the expectation's updated_at")
          .isEqualTo(1);
      assertThat(
              count(
                  "SELECT count(*) FROM "
                      + RESULTS_TABLE
                      + " WHERE inject_expectation_result_inject_expectation_id = '"
                      + expectation
                      + "' AND inject_expectation_result_metadata->>'k' = 'v'"))
          .as("the metadata object is copied")
          .isEqualTo(1);
    }

    @Test
    @DisplayName("Several results of one expectation are all copied, each with its own result id")
    void given_severalResults_should_copyEachWithADistinctId() throws Exception {
      // Arrange
      makePending();
      String expectation =
          seedExpectation(inject, "[{\"result\":\"SUCCESS\"},{\"result\":\"FAILED\"}]");

      // Act
      migration.migrate(connection);

      // Assert
      List<Object[]> rows = resultsOf(expectation);
      // hasSize(2) already proves two distinct primary keys: the insert would have collapsed a
      // duplicate id into one row through ON CONFLICT DO NOTHING.
      assertThat(rows).hasSize(2);
      assertThat(rows.stream().map(r -> r[5])).containsExactlyInAnyOrder("SUCCESS", "FAILED");
    }

    @Test
    @DisplayName(
        "A non-array JSON value or an empty array is left out, with no error from the walk")
    void given_nonArrayOrEmptyArray_should_beLeftOut() throws Exception {
      // Arrange
      makePending();
      String objectShaped = seedExpectation(inject, "{\"result\":\"SUCCESS\"}");
      String nullShaped = seedExpectation(inject, null);
      String emptyArray = seedExpectation(inject, "[]");

      // Act
      migration.migrate(connection);

      // Assert
      assertThat(resultsOf(objectShaped)).isEmpty();
      assertThat(resultsOf(nullShaped)).isEmpty();
      assertThat(resultsOf(emptyArray)).isEmpty();
      assertFullSchema();
    }

    @Test
    @DisplayName("An explicit date in the result is used as is, never falling back")
    void given_explicitDate_should_beUsedAsIs() throws Exception {
      // Arrange: the fallback target is set to a different value, so a wrong branch would show
      makePending();
      String expectation =
          seedExpectation(inject, "[{\"result\":\"SUCCESS\",\"date\":\"2021-06-15T10:00:00Z\"}]");
      execute(
          "UPDATE "
              + SOURCE_TABLE
              + " SET inject_expectation_updated_at = '2020-01-01T00:00:00Z'"
              + " WHERE inject_expectation_id = '"
              + expectation
              + "'");

      // Act
      migration.migrate(connection);

      // Assert
      assertThat(
              count(
                  "SELECT count(*) FROM "
                      + RESULTS_TABLE
                      + " WHERE inject_expectation_result_inject_expectation_id = '"
                      + expectation
                      + "' AND inject_expectation_result_date = TIMESTAMPTZ '2021-06-15T10:00:00Z'"))
          .isEqualTo(1);
    }

    @Test
    @DisplayName("An empty date string falls back to the expectation's updated_at, with no error")
    void given_emptyStringDate_should_fallBackToUpdatedAt() throws Exception {
      // Arrange: a bare "::timestamptz" cast of '' would error without the NULLIF guard
      makePending();
      String expectation = seedExpectation(inject, "[{\"result\":\"SUCCESS\",\"date\":\"\"}]");
      execute(
          "UPDATE "
              + SOURCE_TABLE
              + " SET inject_expectation_updated_at = '2020-02-02T00:00:00Z'"
              + " WHERE inject_expectation_id = '"
              + expectation
              + "'");

      // Act
      migration.migrate(connection);

      // Assert
      assertThat(
              count(
                  "SELECT count(*) FROM "
                      + RESULTS_TABLE
                      + " WHERE inject_expectation_result_inject_expectation_id = '"
                      + expectation
                      + "' AND inject_expectation_result_date = TIMESTAMPTZ '2020-02-02T00:00:00Z'"))
          .isEqualTo(1);
    }

    @Test
    @DisplayName("An empty score string is stored as NULL, with no cast error")
    void given_emptyStringScore_should_beStoredAsNull() throws Exception {
      // Arrange: a bare "::numeric" cast of '' would error without the NULLIF guard
      makePending();
      String expectation = seedExpectation(inject, "[{\"result\":\"SUCCESS\",\"score\":\"\"}]");

      // Act
      migration.migrate(connection);

      // Assert
      assertThat(
              count(
                  "SELECT count(*) FROM "
                      + RESULTS_TABLE
                      + " WHERE inject_expectation_result_inject_expectation_id = '"
                      + expectation
                      + "' AND inject_expectation_result_score IS NULL"))
          .isEqualTo(1);
    }

    @Test
    @DisplayName("With no updated_at, the date falls back to the expectation's created_at")
    void given_noUpdatedAt_should_fallBackToCreatedAt() throws Exception {
      // Arrange: inject_expectation_updated_at is NOT NULL in production (confirmed here by the
      // DB itself refusing a plain UPDATE to NULL); relaxed for this one test only, to prove the
      // COALESCE's created_at branch, and restored in the finally block below.
      makePending();
      execute(
          "ALTER TABLE "
              + SOURCE_TABLE
              + " ALTER COLUMN inject_expectation_updated_at DROP NOT NULL");
      String expectation = seedExpectation(inject, "[{\"result\":\"SUCCESS\"}]");
      try {
        execute(
            "UPDATE "
                + SOURCE_TABLE
                + " SET inject_expectation_updated_at = NULL,"
                + " inject_expectation_created_at = '2019-03-03T00:00:00Z'"
                + " WHERE inject_expectation_id = '"
                + expectation
                + "'");

        // Act
        migration.migrate(connection);

        // Assert
        assertThat(
                count(
                    "SELECT count(*) FROM "
                        + RESULTS_TABLE
                        + " WHERE inject_expectation_result_inject_expectation_id = '"
                        + expectation
                        + "' AND inject_expectation_result_date = TIMESTAMPTZ '2019-03-03T00:00:00Z'"))
            .isEqualTo(1);
      } finally {
        // The constraint cannot come back while this row still carries the NULL it tests.
        execute(
            "UPDATE "
                + SOURCE_TABLE
                + " SET inject_expectation_updated_at = NOW() WHERE inject_expectation_id = '"
                + expectation
                + "' AND inject_expectation_updated_at IS NULL");
        execute(
            "ALTER TABLE "
                + SOURCE_TABLE
                + " ALTER COLUMN inject_expectation_updated_at SET NOT NULL");
      }
    }
  }

  @Nested
  @DisplayName("Walk")
  class Walk {

    @Test
    @DisplayName(
        "The page-range walk alone fills every row across several pages, before the catch-up pass")
    void given_pendingRowsAcrossSeveralPages_should_beFilledByTheWalkAlone() throws SQLException {
      // Arrange: PLAIN storage keeps the filler inline (never TOASTed out of the row), so a
      // handful of rows reliably span several pages. walk() alone never creates the table, so it
      // is created here the way migrate() would.
      makePending();
      execute(
          "ALTER TABLE " + SOURCE_TABLE + " ALTER COLUMN " + JSON_COLUMN + " SET STORAGE PLAIN");
      execute(CREATE_TABLE);
      List<String> expectations = new ArrayList<>();
      for (int i = 0; i < 7; i++) {
        expectations.add(seedExpectation(inject, "[{\"result\":\"" + randomFiller() + "\"}]"));
      }
      assertThat(distinctPages(expectations))
          .as("the seeded rows span at least 3 pages")
          .isGreaterThanOrEqualTo(3);

      // Act: one page per batch (small), so the walk crosses several ranges to cover them all
      long written = small.walk(connection, new WalGuard(null));

      // Assert
      assertThat(written).isGreaterThanOrEqualTo(7);
      for (String expectation : expectations) {
        assertThat(resultsOf(expectation)).hasSize(1);
      }
    }
  }

  @Nested
  @DisplayName("Catch-up")
  class CatchUp {

    @Test
    @DisplayName(
        "Rows added to the source table after the walk measured its size are filled by the catch-up pass")
    void given_rowsAddedAfterTheWalkMeasuredItsSize_should_beFilledByTheCatchUp()
        throws SQLException {
      // Arrange: PLAIN storage and six packed filler rows already use several pages; the hook
      // then adds more large rows once the walk has already read the page count, so at least one
      // of them lands on a page the walk's fixed range never reaches.
      makePending();
      execute(
          "ALTER TABLE " + SOURCE_TABLE + " ALTER COLUMN " + JSON_COLUMN + " SET STORAGE PLAIN");
      execute(CREATE_TABLE);
      for (int i = 0; i < 6; i++) {
        seedExpectation(inject, "[{\"result\":\"" + randomFiller() + "\"}]");
      }
      V6_20261023184049806__Scripted_growth migration = new V6_20261023184049806__Scripted_growth();

      // Act
      migration.migrate(connection);

      // Assert
      assertThat(migration.lateExpectations).hasSize(3);
      for (String late : migration.lateExpectations) {
        assertThat(resultsOf(late)).hasSize(1);
      }
      assertFullSchema();
    }

    @Test
    @DisplayName(
        "More missing rows than one batch are filled in several batches, each committed and paced")
    void given_moreMissingRowsThanABatch_should_commitAndPaceEachBatch() throws SQLException {
      // Arrange: five expectations, two per batch; each pace reads the committed count from a
      // second connection, so a batch that is not committed before its pace shows as a short count
      makePending();
      execute(CREATE_TABLE);
      List<String> expectations = new ArrayList<>();
      for (int i = 0; i < 5; i++) {
        expectations.add(seedExpectation(inject, "[{\"result\":\"SUCCESS\"}]"));
      }
      List<Long> committedAtPace = new ArrayList<>();
      V6_20261023184049806__Scripted_batches batches =
          new V6_20261023184049806__Scripted_batches(
              2,
              () -> {
                committedAtPace.add(committedResults(expectations));
                return 0;
              });

      // Act
      long written = batches.catchUp(connection, new WalGuard(null));

      // Assert
      assertThat(written).isEqualTo(5);
      assertThat(committedAtPace).hasSizeGreaterThanOrEqualTo(3);
      assertThat(committedAtPace).isSorted();
      assertThat(committedAtPace).anyMatch(committed -> committed > 0 && committed < 5);
      assertThat(committedAtPace.getLast()).isEqualTo(5);
    }

    @Test
    @DisplayName(
        "The catch-up leaves alone an expectation that already has results, an empty array and a"
            + " non-array value")
    void given_filledEmptyAndNonArrayRows_should_beSkippedByTheCatchUp() throws SQLException {
      // Arrange: the filled one holds two results in its JSON but one row in the table
      makePending();
      execute(CREATE_TABLE);
      String filled = seedExpectation(inject, "[{\"result\":\"A\"},{\"result\":\"B\"}]");
      execute(
          "INSERT INTO "
              + RESULTS_TABLE
              + " (inject_expectation_result_id, inject_expectation_result_inject_expectation_id)"
              + " VALUES ('"
              + UUID.randomUUID()
              + "', '"
              + filled
              + "')");
      String empty = seedExpectation(inject, "[]");
      String nonArray = seedExpectation(inject, "{\"result\":\"A\"}");
      String missing = seedExpectation(inject, "[{\"result\":\"C\"},{\"result\":\"D\"}]");

      // Act
      small.catchUp(connection, new WalGuard(null));

      // Assert
      assertThat(resultsOf(filled)).hasSize(1);
      assertThat(resultsOf(empty)).isEmpty();
      assertThat(resultsOf(nonArray)).isEmpty();
      assertThat(resultsOf(missing)).hasSize(2);
    }

    @Test
    @DisplayName("The catch-up ends when rows are added during it, and fills them")
    void given_rowsAddedDuringTheCatchUp_should_endAndFillThem() throws SQLException {
      // Arrange: one expectation a batch; the first pace adds one row after every id and one
      // before every id, as a concurrent writer could
      makePending();
      execute(CREATE_TABLE);
      List<String> expectations = new ArrayList<>();
      for (int i = 0; i < 3; i++) {
        expectations.add(seedExpectation(inject, "[{\"result\":\"SUCCESS\"}]"));
      }
      String ahead = "ffffffff-ffff-ffff-ffff-ffffffffffff";
      String behind = "00000000-0000-0000-0000-000000000000";
      AtomicInteger paces = new AtomicInteger();
      V6_20261023184049806__Scripted_batches batches =
          new V6_20261023184049806__Scripted_batches(
              1,
              () -> {
                if (paces.getAndIncrement() == 0) {
                  seedExpectation(ahead, inject, "[{\"result\":\"SUCCESS\"}]");
                  seedExpectation(behind, inject, "[{\"result\":\"SUCCESS\"}]");
                }
                return 0;
              });

      // Act
      batches.catchUp(connection, new WalGuard(null));

      // Assert: it returned; the row ahead is read by the first pass, the one behind by the next
      assertThat(paces.get()).isGreaterThanOrEqualTo(5);
      for (String expectation : expectations) {
        assertThat(resultsOf(expectation)).hasSize(1);
      }
      assertThat(resultsOf(ahead)).hasSize(1);
      assertThat(resultsOf(behind)).hasSize(1);
    }
  }

  @Nested
  @DisplayName("Batch retry")
  class BatchRetry {

    @Test
    @DisplayName("A batch that hits a concurrent delete once (23503) is retried, then succeeds")
    void given_foreignKeyViolationOnce_should_retryThenSucceed() throws SQLException {
      // Arrange
      makePending();
      String expectation = seedExpectation(inject, "[{\"result\":\"SUCCESS\"}]");
      V6_20261023184049806__Scripted_conflict migration =
          new V6_20261023184049806__Scripted_conflict();

      // Act
      migration.migrate(connection);

      // Assert
      assertThat(migration.thrown).isTrue();
      assertThat(resultsOf(expectation)).hasSize(1);
      assertFullSchema();
    }
  }

  @Nested
  @DisplayName("Lock retry")
  class LockRetry {

    @Test
    @DisplayName("A lock timeout dropping the JSON column is retried, then succeeds")
    void given_lockTimeoutOnDropColumn_should_retryThenSucceed() throws SQLException {
      // Arrange
      makePending();
      String expectation = seedExpectation(inject, "[{\"result\":\"SUCCESS\"}]");
      V6_20261023184049806__Scripted_lock migration = new V6_20261023184049806__Scripted_lock();

      // Act
      migration.migrate(connection);

      // Assert
      assertThat(migration.timedOut).isTrue();
      assertThat(resultsOf(expectation)).hasSize(1);
      assertFullSchema();
    }
  }

  @Nested
  @DisplayName("WAL lag")
  class WalLag {

    @Test
    @DisplayName("The WAL lag query runs on the server and reads zero or more bytes")
    void given_server_should_readTheWalLag() throws SQLException {
      // Act
      long lag = small.walLagBytes(connection, null);

      // Assert
      assertThat(lag).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("A batch waits while the WAL lag is above the bound, and goes on once it falls")
    void given_lagAboveTheBound_should_waitUntilItFalls() throws SQLException {
      // Arrange: one range for the whole table, so the walk paces once
      makePending();
      execute(CREATE_TABLE);
      String expectation = seedExpectation(inject, "[{\"result\":\"SUCCESS\"}]");
      Deque<Long> lags = new ArrayDeque<>(List.of(500L, 500L, 0L));
      AtomicInteger reads = new AtomicInteger();
      V6_20261023184049806__Add_expectation_results_table migration =
          new V6_20261023184049806__Scripted_lag(
              () -> {
                reads.incrementAndGet();
                Long lag = lags.poll();
                return lag == null ? 0 : lag;
              });

      // Act
      migration.walk(connection, new WalGuard(null));

      // Assert: once after the batch, twice more while above the bound
      assertThat(reads).hasValue(3);
      assertThat(lags).isEmpty();
      assertThat(resultsOf(expectation)).hasSize(1);
    }

    @Test
    @DisplayName("The wait for the WAL logs at WARN, so a WARN log level shows it")
    void given_lagAboveTheBound_should_logTheWaitAtWarn() throws SQLException {
      // Arrange
      makePending();
      execute(CREATE_TABLE);
      seedExpectation(inject, "[{\"result\":\"SUCCESS\"}]");
      Deque<Long> lags = new ArrayDeque<>(List.of(500L, 0L));
      V6_20261023184049806__Add_expectation_results_table migration =
          new V6_20261023184049806__Scripted_lag(
              () -> {
                Long lag = lags.poll();
                return lag == null ? 0 : lag;
              });
      Logger logger =
          (Logger)
              LoggerFactory.getLogger(V6_20261023184049806__Add_expectation_results_table.class);
      Level previousLevel = logger.getLevel();
      logger.setLevel(Level.WARN);
      ListAppender<ILoggingEvent> appender = new ListAppender<>();
      appender.start();
      logger.addAppender(appender);

      // Act: at WARN, the level an INFO message does not reach
      try {
        migration.walk(connection, new WalGuard(null));
      } finally {
        logger.detachAppender(appender);
        logger.setLevel(previousLevel);
      }

      // Assert
      assertThat(appender.list)
          .anyMatch(
              event ->
                  event.getLevel() == Level.WARN
                      && event.getFormattedMessage().contains("waiting for WAL"));
    }

    @Test
    @DisplayName("A lag that cannot be read stops the lag reads for the run, and the walk goes on")
    void given_unreadableLag_should_fallBackToThePauseAlone() throws SQLException {
      // Arrange
      makePending();
      execute(CREATE_TABLE);
      String expectation = seedExpectation(inject, "[{\"result\":\"SUCCESS\"}]");
      AtomicInteger reads = new AtomicInteger();
      V6_20261023184049806__Add_expectation_results_table migration =
          new V6_20261023184049806__Scripted_lag(
              () -> {
                reads.incrementAndGet();
                throw new SQLException("permission denied for view pg_stat_replication");
              });
      WalGuard guard = new WalGuard(null);

      // Act: one guard for both, as in a run
      migration.walk(connection, guard);
      migration.walk(connection, guard);

      // Assert
      assertThat(reads).hasValue(1);
      assertThat(resultsOf(expectation)).hasSize(1);
    }
  }

  @Nested
  @DisplayName("Pages per batch")
  class PagesPerBatch {

    @Test
    @DisplayName("An unknown row or page estimate gives a fixed range of pages per batch")
    void given_unknownEstimate_should_useTheFixedPageRange() {
      // Act + Assert
      assertThat(pagesPerBatch(-1, 100, 10_000)).isEqualTo(UNKNOWN_DENSITY_PAGES_PER_BATCH);
      assertThat(pagesPerBatch(0, 100, 10_000)).isEqualTo(UNKNOWN_DENSITY_PAGES_PER_BATCH);
      assertThat(pagesPerBatch(100_000, 0, 10_000)).isEqualTo(UNKNOWN_DENSITY_PAGES_PER_BATCH);
    }

    @Test
    @DisplayName("The batch covers about the target row count, given the table's row density")
    void given_knownDensity_should_coverAboutTheTargetRows() {
      // Act + Assert: 10 rows a page
      assertThat(pagesPerBatch(1_000_000, 100_000, 10_000)).isEqualTo(1_000);
    }

    @Test
    @DisplayName("At least one page per batch, even for a very dense table")
    void given_veryDenseTable_should_useAtLeastOnePage() {
      // Act + Assert
      assertThat(pagesPerBatch(1_000_000_000, 100, 10_000)).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("Range picker")
  class RangePicker {

    @Test
    @DisplayName("A page range is read as a Tid Range Scan, in physical order, with no sort")
    void given_pageRange_should_useATidRangeScan() throws SQLException {
      // Arrange: RANGE_INSERT's predicate on ie.ctid alone; parameters inlined for EXPLAIN
      assertThat(RANGE_INSERT).contains("ie.ctid >= ?::tid AND ie.ctid < ?::tid");
      makePending();
      execute(CREATE_TABLE);
      String sql =
          RANGE_INSERT
              .replaceFirst("\\?::tid", "'(0,0)'::tid")
              .replaceFirst("\\?::tid", "'(10,0)'::tid");
      assertThat(sql).doesNotContain("?");

      // Act: on a near-empty table a seq scan costs the same as any path
      execute("SET enable_seqscan = off");
      String plan;
      try {
        plan =
            rows("EXPLAIN " + sql).stream()
                .map(row -> (String) row[0])
                .reduce("", (left, right) -> left + "\n" + right);
      } finally {
        execute("RESET enable_seqscan");
      }

      // Assert
      assertThat(plan).contains("Tid Range Scan").doesNotContain("Sort");
    }
  }

  @Nested
  @DisplayName("Reindex")
  class Reindex {

    @Test
    @DisplayName(
        "The migration writes the reset sentinel and registers the in-process reset request")
    void given_migration_should_requestAFullReindexAtStartup() throws Exception {
      // Arrange
      makePending();

      // Act
      migration.migrate(connection);

      // Assert
      assertThat(
              count(
                  "SELECT count(*) FROM indexing_status WHERE indexing_status_type = '"
                      + EXPECTATION_INJECT_MODEL
                      + "' AND indexing_status_indexing_date = TIMESTAMPTZ '"
                      + EsIndexingUtils.REINDEX_REQUESTED_CURSOR
                      + "'"))
          .as("the sentinel cursor is written")
          .isEqualTo(1);
      assertThat(EsIndexingUtils.isRollingDeployReset(EXPECTATION_INJECT_MODEL, Optional.empty()))
          .as("the in-process reset request is registered for this startup")
          .isTrue();
    }
  }

  @Nested
  @DisplayName("Rerun")
  class Rerun {

    @Test
    @DisplayName(
        "A run that fails partway leaves the JSON column in place and the ranges before the"
            + " failure committed; a second run finishes with no duplicate")
    void given_failureAfterSomeRanges_should_finishOnRerunWithNoDuplicate() throws SQLException {
      // Arrange: six expectations over several pages; the trigger fails the result of the one
      // with the highest ctid, whichever seeding order left it. PLAIN storage keeps the filler
      // inline (never TOASTed out of the row), so six rows reliably span more than one page
      // regardless of what other tests already put in this shared table.
      makePending();
      execute(
          "ALTER TABLE " + SOURCE_TABLE + " ALTER COLUMN " + JSON_COLUMN + " SET STORAGE PLAIN");
      List<String> expectations = new ArrayList<>();
      for (int i = 1; i <= 6; i++) {
        expectations.add(seedExpectation(inject, "[{\"result\":\"" + randomFiller() + "\"}]"));
      }
      String failing = highestCtid(expectations);
      execute(
          "CREATE FUNCTION test_result_fail() RETURNS trigger AS $$ BEGIN"
              + " IF NEW.inject_expectation_result_inject_expectation_id = '"
              + failing
              + "' THEN RAISE EXCEPTION 'seeded failure'; END IF;"
              + " RETURN NEW; END $$ LANGUAGE plpgsql");
      // Create the table ourselves (makePending() already dropped it) so the trigger can be
      // attached to it before the migration runs.
      execute(CREATE_TABLE);
      execute(
          "CREATE TRIGGER test_result_fail BEFORE INSERT ON "
              + RESULTS_TABLE
              + " FOR EACH ROW EXECUTE FUNCTION test_result_fail()");

      // Act: the first run fails on the range holding the failing expectation
      assertThatThrownBy(() -> small.migrate(connection))
          .isInstanceOf(SQLException.class)
          .hasMessageContaining("seeded failure");

      // Assert: the ranges before the failing one are committed, the JSON column still exists
      long filled =
          count(
              "SELECT count(*) FROM "
                  + RESULTS_TABLE
                  + " WHERE inject_expectation_result_inject_expectation_id = ANY(?)",
              expectations);
      assertThat(filled).isBetween(1L, 5L);
      assertThat(
              count(
                  "SELECT count(*) FROM information_schema.columns WHERE table_schema ="
                      + " current_schema() AND table_name = '"
                      + SOURCE_TABLE
                      + "' AND column_name = '"
                      + JSON_COLUMN
                      + "'"))
          .as("the JSON column is not dropped while a range is still unfilled")
          .isEqualTo(1);

      // Act: the cause is gone, the migration runs again
      execute("DROP TRIGGER test_result_fail ON " + RESULTS_TABLE);
      execute("DROP FUNCTION test_result_fail()");
      small.migrate(connection);

      // Assert: every expectation has exactly one result, no duplicate
      for (String expectation : expectations) {
        assertThat(resultsOf(expectation)).hasSize(1);
      }
      assertFullSchema();
    }
  }

  @Nested
  @DisplayName("No VACUUM")
  class NoVacuum {

    @Test
    @DisplayName(
        "A full run, from a pending platform to the final state, issues no VACUUM statement")
    void given_pendingPlatform_should_issueNoVacuumStatement() throws SQLException {
      // Arrange
      makePending();
      for (int i = 0; i < 5; i++) {
        seedExpectation(inject, "[{\"result\":\"" + randomFiller() + "\"}]");
      }
      List<String> statements = new ArrayList<>();

      // Act
      small.migrate(recordingConnection(connection, statements));

      // Assert
      assertThat(statements)
          .as("statements executed by the migration")
          .isNotEmpty()
          .noneMatch(sql -> sql.toUpperCase().contains("VACUUM"));
      assertFullSchema();
    }
  }

  // -- scripted migrations --

  private interface LagReadings {
    long next() throws SQLException;
  }

  /**
   * One page a batch, no pause, a 100-byte bound, scripted lag; Flyway needs the versioned name.
   */
  private static final class V6_20261023184049806__Scripted_lag
      extends V6_20261023184049806__Add_expectation_results_table {

    private final LagReadings readings;

    V6_20261023184049806__Scripted_lag(LagReadings readings) {
      // A huge override, so the whole table (however much other tests have bloated it) is one
      // range: the test is about pacing, not about the walk's own page-batching logic.
      super(1_000_000L, 0, 100);
      this.readings = readings;
    }

    @Override
    long walLagBytes(Connection ignored, String startWalFile) throws SQLException {
      return readings.next();
    }
  }

  /** Catch-up batches of a given size and no pause; {@code atPace} runs at each pace. */
  private static final class V6_20261023184049806__Scripted_batches
      extends V6_20261023184049806__Add_expectation_results_table {

    private final LagReadings atPace;

    V6_20261023184049806__Scripted_batches(int rowsPerBatch, LagReadings atPace) {
      super(1L, 0, Long.MAX_VALUE, rowsPerBatch);
      this.atPace = atPace;
    }

    @Override
    long walLagBytes(Connection ignored, String startWalFile) throws SQLException {
      return atPace.next();
    }
  }

  /** The first attempt to drop the JSON column times out on a lock; the retry succeeds. */
  private static final class V6_20261023184049806__Scripted_lock
      extends V6_20261023184049806__Add_expectation_results_table {

    private boolean timedOut;

    V6_20261023184049806__Scripted_lock() {
      super(1L, 0, Long.MAX_VALUE);
    }

    @Override
    void dropJsonColumn(Statement statement) throws SQLException {
      if (!timedOut) {
        timedOut = true;
        throw new SQLException("canceling statement due to lock timeout", "55P03");
      }
      super.dropJsonColumn(statement);
    }
  }

  /** The first batch hits a scripted foreign-key violation; the retry runs it for real. */
  private static final class V6_20261023184049806__Scripted_conflict
      extends V6_20261023184049806__Add_expectation_results_table {

    private boolean thrown;

    V6_20261023184049806__Scripted_conflict() {
      super(1L, 0, Long.MAX_VALUE);
    }

    @Override
    long runOnce(PreparedStatement statement) throws SQLException {
      if (!thrown) {
        thrown = true;
        throw new SQLException("concurrent delete of the expectation", FOREIGN_KEY_VIOLATION);
      }
      return super.runOnce(statement);
    }
  }

  /**
   * Inserts more rows once the walk has already measured the table's page count, so the catch-up
   * pass is the only thing that can fill them. Inner (not static): it seeds through the outer
   * test's own helper and parent inject.
   */
  private final class V6_20261023184049806__Scripted_growth
      extends V6_20261023184049806__Add_expectation_results_table {

    private final List<String> lateExpectations = new ArrayList<>();

    V6_20261023184049806__Scripted_growth() {
      super(1L, 0, Long.MAX_VALUE);
    }

    @Override
    TableSize tableSize(Connection connection) throws SQLException {
      TableSize measured = super.tableSize(connection);
      for (int i = 0; i < 3; i++) {
        lateExpectations.add(seedExpectation(inject, "[{\"result\":\"" + randomFiller() + "\"}]"));
      }
      return measured;
    }
  }

  // -- assertions --

  private void assertFullSchema() throws SQLException {
    assertThat(
            count(
                "SELECT count(*) FROM information_schema.columns WHERE table_schema ="
                    + " current_schema() AND table_name = '"
                    + SOURCE_TABLE
                    + "' AND column_name = '"
                    + JSON_COLUMN
                    + "'"))
        .as("the JSON column is dropped")
        .isZero();
    assertThat(
            rows(
                "SELECT column_name, data_type, is_nullable, (column_default IS NOT NULL)"
                    + " FROM information_schema.columns WHERE table_schema = current_schema()"
                    + " AND table_name = '"
                    + RESULTS_TABLE
                    + "' ORDER BY ordinal_position"))
        .as("the final column set, types, nullability and which ones have a default")
        .containsExactly(
            new Object[] {"inject_expectation_result_id", "character varying", "NO", false},
            new Object[] {"inject_expectation_result_source_id", "character varying", "YES", false},
            new Object[] {
              "inject_expectation_result_source_type", "character varying", "YES", false
            },
            new Object[] {
              "inject_expectation_result_source_name", "character varying", "YES", false
            },
            new Object[] {
              "inject_expectation_result_source_asset_id", "character varying", "YES", false
            },
            new Object[] {
              "inject_expectation_result_source_platform", "character varying", "YES", false
            },
            new Object[] {"inject_expectation_result_date", "timestamp with time zone", "NO", true},
            new Object[] {"inject_expectation_result_result", "text", "YES", false},
            new Object[] {"inject_expectation_result_score", "numeric", "YES", false},
            new Object[] {"inject_expectation_result_metadata", "jsonb", "YES", false},
            new Object[] {
              "inject_expectation_result_created_at", "timestamp with time zone", "NO", true
            },
            new Object[] {
              "inject_expectation_result_updated_at", "timestamp with time zone", "NO", true
            },
            new Object[] {
              "inject_expectation_result_inject_expectation_id", "character varying", "NO", false
            });
    assertThat(
            count(
                "SELECT count(*) FROM pg_constraint WHERE conrelid = '"
                    + RESULTS_TABLE
                    + "'::regclass AND contype = 'f' AND confrelid = '"
                    + SOURCE_TABLE
                    + "'::regclass AND confdeltype = 'c' AND convalidated"))
        .as("the foreign key to the source table is in place, validated, ON DELETE CASCADE")
        .isEqualTo(1);
    assertThat(
            count(
                "SELECT count(*) FROM pg_index i JOIN pg_class c ON c.oid = i.indexrelid"
                    + " JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = i.indkey[0]"
                    + " WHERE c.relname = '"
                    + INDEX
                    + "' AND i.indrelid = '"
                    + RESULTS_TABLE
                    + "'::regclass AND i.indisvalid"
                    + " AND a.attname = 'inject_expectation_result_inject_expectation_id'"))
        .as("the lookup index is in place, on the expectation id column")
        .isEqualTo(1);
  }

  private List<Object[]> resultsOf(String expectation) throws SQLException {
    return rows(
        "SELECT inject_expectation_result_source_id, inject_expectation_result_source_type,"
            + " inject_expectation_result_source_name, inject_expectation_result_source_asset_id,"
            + " inject_expectation_result_source_platform,"
            + " inject_expectation_result_result, inject_expectation_result_score"
            + " FROM "
            + RESULTS_TABLE
            + " WHERE inject_expectation_result_inject_expectation_id = '"
            + expectation
            + "'");
  }

  private long distinctPages(List<String> expectationIds) throws SQLException {
    return count(
        "SELECT count(DISTINCT (ctid::text::point)[0]) FROM "
            + SOURCE_TABLE
            + " WHERE inject_expectation_id = ANY(?)",
        expectationIds);
  }

  /** The seeded expectation whose row physically sits last, whatever the insertion order left. */
  private String highestCtid(List<String> expectationIds) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "SELECT inject_expectation_id FROM "
                + SOURCE_TABLE
                + " WHERE inject_expectation_id = ANY(?) ORDER BY ctid DESC LIMIT 1")) {
      statement.setArray(1, connection.createArrayOf("varchar", expectationIds.toArray()));
      try (ResultSet result = statement.executeQuery()) {
        result.next();
        return result.getString(1);
      }
    }
  }

  // -- seeding --

  /** Puts the platform in the state the migration starts from: no table, the JSON column back. */
  private void makePending() throws SQLException {
    execute("DROP TABLE IF EXISTS " + RESULTS_TABLE);
    execute("ALTER TABLE " + SOURCE_TABLE + " ADD COLUMN IF NOT EXISTS " + JSON_COLUMN + " json");
  }

  private String seedTenant(String name) throws SQLException {
    String id = UUID.randomUUID().toString();
    execute(
        "INSERT INTO tenants (tenant_id, tenant_name) VALUES ('"
            + id
            + "', '"
            + name
            + "-"
            + id.substring(0, 8)
            + "')");
    seededTenants.add(id);
    return id;
  }

  private String seedInject(String tenant) throws SQLException {
    String id = UUID.randomUUID().toString();
    execute(
        "INSERT INTO injects (inject_id, inject_title, inject_all_teams, inject_enabled,"
            + " inject_depends_duration, tenant_id) VALUES ('"
            + id
            + "', 'seeded', false, true, 0, '"
            + tenant
            + "')");
    seededInjects.add(id);
    return id;
  }

  private String seedExpectation(String inject, String resultsJson) throws SQLException {
    return seedExpectation(UUID.randomUUID().toString(), inject, resultsJson);
  }

  private String seedExpectation(String id, String inject, String resultsJson) throws SQLException {
    execute(
        "INSERT INTO injects_expectations (inject_expectation_id, inject_expectation_type,"
            + " inject_id, inject_expectation_expected_score, inject_expiration_time,"
            + " inject_expectation_results) VALUES ('"
            + id
            + "', 'PREVENTION', '"
            + inject
            + "', 100, 3600, "
            + (resultsJson == null ? "NULL" : "'" + resultsJson.replace("'", "''") + "'::json")
            + ")");
    seededExpectations.add(id);
    return id;
  }

  /** Random, barely-compressible filler: makes a handful of rows span more than one page. */
  private static String randomFiller() {
    StringBuilder filler = new StringBuilder();
    for (int i = 0; i < 80; i++) {
      filler.append(UUID.randomUUID());
    }
    return filler.toString();
  }

  private void deleteAll(String table, String column, List<String> ids) throws SQLException {
    for (String id : ids) {
      execute("DELETE FROM " + table + " WHERE " + column + " = '" + id + "'");
    }
  }

  // -- a connection that records the SQL text of every ad-hoc statement it runs --

  private static Connection recordingConnection(Connection delegate, List<String> statements) {
    return (Connection)
        Proxy.newProxyInstance(
            Connection.class.getClassLoader(),
            new Class<?>[] {Connection.class},
            (proxy, method, args) -> {
              if ("createStatement".equals(method.getName())
                  && (args == null || args.length == 0)) {
                return recordingStatement(delegate.createStatement(), statements);
              }
              return invoke(delegate, method, args);
            });
  }

  private static Statement recordingStatement(Statement delegate, List<String> statements) {
    return (Statement)
        Proxy.newProxyInstance(
            Statement.class.getClassLoader(),
            new Class<?>[] {Statement.class},
            (proxy, method, args) -> {
              if (args != null
                  && args.length > 0
                  && args[0] instanceof String sql
                  && method.getName().startsWith("execute")) {
                statements.add(sql);
              }
              return invoke(delegate, method, args);
            });
  }

  private static Object invoke(Object delegate, java.lang.reflect.Method method, Object[] args)
      throws Throwable {
    try {
      return method.invoke(delegate, args);
    } catch (InvocationTargetException e) {
      throw e.getCause();
    }
  }

  // -- plain JDBC helpers --

  private void execute(String sql) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }

  /** Counted on a second connection: only what is committed. */
  private long committedResults(List<String> expectations) throws SQLException {
    try (Connection other = dataSource.getConnection()) {
      try (PreparedStatement statement =
          other.prepareStatement(
              "SELECT count(*) FROM "
                  + RESULTS_TABLE
                  + " WHERE inject_expectation_result_inject_expectation_id = ANY (?)")) {
        statement.setArray(1, other.createArrayOf("varchar", expectations.toArray()));
        try (ResultSet resultSet = statement.executeQuery()) {
          resultSet.next();
          return resultSet.getLong(1);
        }
      }
    }
  }

  private long count(String sql) throws SQLException {
    return ((Number) rows(sql).getFirst()[0]).longValue();
  }

  private long count(String sql, List<String> ids) throws SQLException {
    try (java.sql.PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setArray(1, connection.createArrayOf("varchar", ids.toArray()));
      try (ResultSet resultSet = statement.executeQuery()) {
        resultSet.next();
        return resultSet.getLong(1);
      }
    }
  }

  private List<Object[]> rows(String sql) throws SQLException {
    List<Object[]> rows = new ArrayList<>();
    try (Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery(sql)) {
      int columns = resultSet.getMetaData().getColumnCount();
      while (resultSet.next()) {
        Object[] row = new Object[columns];
        for (int column = 0; column < columns; column++) {
          row[column] = resultSet.getObject(column + 1);
        }
        rows.add(row);
      }
    }
    return rows;
  }
}
