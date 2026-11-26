package io.openaev.migration;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.openaev.IntegrationTest;
import io.openaev.migration.V6_20261117160000000__Convert_findings_to_identity.BatchRetry;
import io.openaev.migration.V6_20261117160000000__Convert_findings_to_identity.WalGuard;
import io.openaev.migration.V6_20261117160000000__Convert_findings_to_identity.WalLag;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
 * Not {@code @Transactional}: the conversion commits each batch, so each test seeds on its own
 * connection and removes its rows afterwards. The tests call the walk and the drain, not {@code
 * migrate}, which also requests a search index reset at the next startup.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Migration V6_20261117160000000: findings identity conversion")
class ConvertFindingsToIdentityMigrationTest extends IntegrationTest {

  private static final Instant DAY_1 = Instant.parse("2026-10-01T10:00:00Z");
  private static final Instant DAY_2 = Instant.parse("2026-10-02T10:00:00Z");
  private static final Instant DAY_3 = Instant.parse("2026-10-03T10:00:00Z");
  private static final Instant DAY_4 = Instant.parse("2026-10-04T10:00:00Z");

  /** One page per range, no pause, no WAL-lag wait. */
  private final V6_20261117160000000__Convert_findings_to_identity onePagePerRange =
      new V6_20261117160000000__Convert_findings_to_identity(1L, 0, Long.MAX_VALUE);

  @Autowired private V6_20261117160000000__Convert_findings_to_identity migration;
  @Autowired private DataSource dataSource;

  private Connection connection;
  private String marker;
  private String asset;
  private final List<String> injects = new ArrayList<>();

  @BeforeEach
  void seedParents() throws SQLException {
    connection = dataSource.getConnection();
    connection.setAutoCommit(true);
    marker = "conversion-test-" + UUID.randomUUID();
    asset = marker + "-asset";
    execute(
        "INSERT INTO assets (asset_id, asset_type, asset_name, tenant_id) VALUES (?, 'Endpoint', ?,"
            + " ?)",
        asset,
        asset,
        DEFAULT_TENANT_UUID);
    for (int i = 0; i < 4; i++) {
      String inject = marker + "-inject-" + i;
      execute(
          "INSERT INTO injects (inject_id, inject_title, inject_all_teams, inject_enabled,"
              + " inject_depends_duration, tenant_id) VALUES (?, 'seeded', false, true, 0, ?)",
          inject,
          DEFAULT_TENANT_UUID);
      injects.add(inject);
    }
  }

  @AfterEach
  void removeSeed() throws SQLException {
    try {
      connection.setAutoCommit(true);
      execute("DROP TRIGGER IF EXISTS conversion_test_trigger ON findings");
      execute("DROP FUNCTION IF EXISTS conversion_test_trigger()");
      execute("DROP TABLE IF EXISTS conversion_test_log");
      execute("DELETE FROM findings WHERE finding_value LIKE ?", marker + "%");
      execute("DELETE FROM finding_locations WHERE finding_location_asset_id = ?", asset);
      execute("DELETE FROM assets WHERE asset_id = ?", asset);
      for (String inject : injects) {
        execute("DELETE FROM injects WHERE inject_id = ?", inject);
      }
    } finally {
      injects.clear();
      connection.close();
    }
  }

  @Nested
  @DisplayName("Writes")
  class Writes {

    @Test
    @DisplayName("A new finding is inserted with its final dates and never updated afterwards")
    void given_oldRowsOfANewIdentity_should_writeTheirFindingOnce() throws Exception {
      // Arrange
      String value = marker + "-value";
      seedOldFinding("1", value, DAY_1, DAY_4, occurrence(0, DAY_2), occurrence(1, DAY_3));
      seedOldFinding("2", value, DAY_1, DAY_4, occurrence(1, DAY_1), occurrence(2, DAY_4));
      logUpdatesOfConvertedFindings();
      Instant before = databaseNow();

      // Act
      drain(migration);

      // Assert
      assertThat(count("SELECT count(*) FROM conversion_test_log")).isZero();
      List<Object[]> findings =
          rows(
              "SELECT finding_first_seen, finding_last_seen, finding_updated_at FROM findings"
                  + " WHERE finding_value = ?",
              value);
      assertThat(findings).hasSize(1);
      assertThat(((Timestamp) findings.getFirst()[0]).toInstant()).isEqualTo(DAY_2);
      assertThat(((Timestamp) findings.getFirst()[1]).toInstant()).isEqualTo(DAY_4);
      assertThat(((Timestamp) findings.getFirst()[2]).toInstant()).isAfterOrEqualTo(before);
      // inject 1 is held from the first row: the second row's occurrence of it is skipped
      assertThat(occurrencesOf(value))
          .containsExactly(
              injects.get(0) + "@" + DAY_2,
              injects.get(1) + "@" + DAY_3,
              injects.get(2) + "@" + DAY_4);
    }

    @Test
    @DisplayName("A finding with no occurrence takes the dates of its rows")
    void given_oldRowWithoutOccurrence_should_takeTheDatesOfItsRow() throws Exception {
      // Arrange
      String value = marker + "-value";
      seedOldFinding("1", value, DAY_2, DAY_3);

      // Act
      walk(onePagePerRange);

      // Assert
      List<Object[]> findings =
          rows(
              "SELECT finding_first_seen, finding_last_seen, finding_created_at FROM findings"
                  + " WHERE finding_value = ? AND finding_value_hash IS NOT NULL",
              value);
      assertThat(findings).hasSize(1);
      assertThat(((Timestamp) findings.getFirst()[0]).toInstant()).isEqualTo(DAY_2);
      assertThat(((Timestamp) findings.getFirst()[1]).toInstant()).isEqualTo(DAY_3);
      assertThat(((Timestamp) findings.getFirst()[2]).toInstant()).isEqualTo(DAY_2);
    }

    @Test
    @DisplayName(
        "A row of a release without occurrences gets the occurrence of its inject, dated at the send"
            + " date of the inject, else at the creation date of the row, and the first written wins")
    void given_oldRowsOnAnInjectWithoutOccurrence_should_convertTheirInject() throws Exception {
      // Arrange: rows written by a release that records no occurrence
      String value = marker + "-value";
      seedOldFinding("1", value, DAY_1, DAY_1, occurrence(1, DAY_2));
      onInject(seedOldFinding("2", value, DAY_1, DAY_1), 0);
      sentAt(0, DAY_3);
      onInject(seedOldFinding("3", value, DAY_1, DAY_1), 1);
      onInject(seedOldFinding("4", value, DAY_4, DAY_4), 2);

      // Act
      drain(migration);

      // Assert
      assertThat(occurrencesOf(value))
          .containsExactly(
              injects.get(0) + "@" + DAY_3,
              injects.get(1) + "@" + DAY_2,
              injects.get(2) + "@" + DAY_4);
      assertThat(
              rows(
                  "SELECT DISTINCT o.finding_occurrence_field FROM finding_occurrences o"
                      + " JOIN findings f ON f.finding_id = o.finding_occurrence_finding_id"
                      + " WHERE f.finding_value = ?",
                  value))
          .extracting(row -> row[0])
          .containsExactly("output");
      List<Object[]> findings =
          rows(
              "SELECT finding_first_seen, finding_last_seen FROM findings WHERE finding_value = ?",
              value);
      assertThat(findings).hasSize(1);
      assertThat(((Timestamp) findings.getFirst()[0]).toInstant()).isEqualTo(DAY_2);
      assertThat(((Timestamp) findings.getFirst()[1]).toInstant()).isEqualTo(DAY_4);
    }
  }

  @Nested
  @DisplayName("Order and resume")
  class OrderAndResume {

    @Test
    @DisplayName(
        "Rows are taken page by page: an interrupted walk leaves the later pages, and the next"
            + " run merges them into the findings already written")
    void given_walkInterruptedOnALaterPage_should_resumeAndMerge() throws Exception {
      // Arrange: ids descend while pages ascend, so id order and physical order disagree
      String value = marker + "-value";
      List<String> ids = new ArrayList<>();
      for (int row = 9; row >= 0; row--) {
        ids.add(seedOldFinding(String.valueOf(row), value, DAY_2, DAY_2, occurrence(0, DAY_2)));
      }
      String late =
          seedOldFinding("late", value, DAY_1, DAY_1, occurrence(0, DAY_3), occurrence(1, DAY_1));
      long latePage = pageOf(late);
      List<String> beforeLatePage = new ArrayList<>();
      List<String> fromLatePage = new ArrayList<>(List.of(late));
      for (String id : ids) {
        (pageOf(id) < latePage ? beforeLatePage : fromLatePage).add(id);
      }
      assertThat(beforeLatePage).as("the seed spans several pages").isNotEmpty();
      failWritesBroughtBy(DAY_1);

      // Act
      assertThatThrownBy(() -> walk(onePagePerRange)).isInstanceOf(SQLException.class);

      // Assert: the pages before the failing one are converted, the failing one is not
      assertThat(unconverted(beforeLatePage)).isEmpty();
      assertThat(unconverted(fromLatePage)).containsExactlyInAnyOrderElementsOf(fromLatePage);

      // Act: the next start resumes
      execute("DROP TRIGGER conversion_test_trigger ON findings");
      walk(onePagePerRange);

      // Assert: one finding, its first date widened by the late row, inject 0 held once
      List<Object[]> findings =
          rows(
              "SELECT finding_first_seen, finding_last_seen, finding_value_hash IS NOT NULL"
                  + " FROM findings WHERE finding_value = ?",
              value);
      assertThat(findings).hasSize(1);
      assertThat(((Timestamp) findings.getFirst()[0]).toInstant()).isEqualTo(DAY_1);
      assertThat(((Timestamp) findings.getFirst()[1]).toInstant()).isEqualTo(DAY_2);
      assertThat(findings.getFirst()[2]).isEqualTo(true);
      assertThat(occurrencesOf(value))
          .containsExactly(injects.get(0) + "@" + DAY_2, injects.get(1) + "@" + DAY_1);
    }

    @Test
    @DisplayName("Running again on a converted table writes nothing")
    void given_convertedTable_should_writeNothing() throws Exception {
      // Arrange
      String value = marker + "-value";
      seedOldFinding("1", value, DAY_1, DAY_2, occurrence(0, DAY_1));
      walk(onePagePerRange);
      List<Object[]> versions = versionsOf(value);

      // Act
      walk(onePagePerRange);
      boolean found = onePagePerRange.convertNextBatch(connection);

      // Assert
      assertThat(found).isFalse();
      assertThat(versionsOf(value)).containsExactlyElementsOf(versions);
    }
  }

  @Nested
  @DisplayName("Pacing")
  class Pacing {

    @Test
    @DisplayName("A batch waits while the WAL lag is above the bound")
    void given_lagAboveTheBound_should_waitUntilItFalls() throws Exception {
      // Arrange
      seedOldFinding("1", marker + "-value", DAY_1, DAY_2);
      V6_20261117160000000__Scripted_lag scripted = new V6_20261117160000000__Scripted_lag();

      // Act
      walk(scripted);

      // Assert: above the 10-byte bound once, then below it
      assertThat(scripted.reads).hasValue(2);
    }

    @Test
    @DisplayName("A wait reads the archiver statistics afresh at each round")
    void given_archiverStatisticsChangingDuringAWait_should_seeTheChange() throws Exception {
      // Arrange
      seedOldFinding("1", marker + "-value", DAY_1, DAY_2);
      V6_20261117160000000__Archiver_lag archiverLag =
          new V6_20261117160000000__Archiver_lag(dataSource);

      // Act
      assertTimeoutPreemptively(Duration.ofSeconds(30), () -> walk(archiverLag));

      // Assert: above the bound until another session resets the archiver statistics
      assertThat(archiverLag.reads).hasValue(2);
    }

    @Test
    @DisplayName("A wait stops at the ceiling and the run goes on with the fixed pause alone")
    void given_lagAboveTheBoundPastTheCeiling_should_stopWaitingForTheRestOfTheRun()
        throws Exception {
      // Arrange
      seedOldFinding("1", marker + "-first", DAY_1, DAY_2);
      V6_20261117160000000__Fixed_lag aboveTheBound =
          new V6_20261117160000000__Fixed_lag(new WalLag(11, 0), Duration.ofMillis(300));
      WalGuard guard = new WalGuard(null);
      ListAppender<ILoggingEvent> logs = captureLogs();

      try {
        // Act: a first batch waits up to the ceiling, a batch afterwards does not read the lag
        assertTimeoutPreemptively(Duration.ofSeconds(30), () -> walk(aboveTheBound, guard));
        seedOldFinding("2", marker + "-second", DAY_1, DAY_2);
        walk(aboveTheBound, guard);

        // Assert
        assertThat(aboveTheBound.reads).hasValue(2);
        assertThat(
                count(
                    "SELECT count(*) FROM findings WHERE finding_value LIKE ? AND"
                        + " finding_value_hash IS NULL",
                    marker + "%"))
            .isZero();
        assertThat(warnings(logs)).singleElement().asString().contains("for the rest of the run");
      } finally {
        releaseLogs(logs);
      }
    }

    @Test
    @DisplayName("Replicas whose replay position the role cannot read are logged once")
    void given_replicasWithoutReadablePosition_should_logItOnceAndGoOn() throws Exception {
      // Arrange
      seedOldFinding("1", marker + "-first", DAY_1, DAY_2);
      V6_20261117160000000__Fixed_lag unreadableReplicas =
          new V6_20261117160000000__Fixed_lag(new WalLag(0, 2), Duration.ofMinutes(1));
      WalGuard guard = new WalGuard(null);
      ListAppender<ILoggingEvent> logs = captureLogs();

      try {
        // Act: two batches, two lag reads
        walk(unreadableReplicas, guard);
        seedOldFinding("2", marker + "-second", DAY_1, DAY_2);
        walk(unreadableReplicas, guard);

        // Assert
        assertThat(unreadableReplicas.reads).hasValue(2);
        assertThat(warnings(logs))
            .singleElement()
            .asString()
            .contains("2 replica(s)", "pg_read_all_stats");
      } finally {
        releaseLogs(logs);
      }
    }

    @Test
    @DisplayName("The lag query reads no lag and no hidden replica on a primary without any")
    void given_primaryWithoutReplicaOrArchiving_should_readNoLag() throws Exception {
      // Arrange
      connection.setAutoCommit(false);

      try {
        // Act
        WalLag lag = onePagePerRange.walLag(connection, null);

        // Assert
        assertThat(lag).isEqualTo(new WalLag(0, 0));
      } finally {
        connection.rollback();
        connection.setAutoCommit(true);
      }
    }
  }

  // -- Helpers --

  /** One page per range, no pause, a 10-byte bound; Flyway needs the versioned name. */
  private static final class V6_20261117160000000__Scripted_lag
      extends V6_20261117160000000__Convert_findings_to_identity {

    private final AtomicInteger reads = new AtomicInteger();

    V6_20261117160000000__Scripted_lag() {
      super(1L, 0, 10);
    }

    @Override
    WalLag walLag(Connection ignored, String startWalFile) {
      return new WalLag(reads.incrementAndGet() == 1 ? 11 : 0, 0);
    }
  }

  /** One page per range, no pause, a 10-byte bound, the same lag at each read. */
  private static final class V6_20261117160000000__Fixed_lag
      extends V6_20261117160000000__Convert_findings_to_identity {

    private final WalLag lag;
    private final AtomicInteger reads = new AtomicInteger();

    V6_20261117160000000__Fixed_lag(WalLag lag, Duration waitCeiling) {
      super(1L, 0, 10, BatchRetry.DEFAULT, waitCeiling);
      this.lag = lag;
    }

    @Override
    WalLag walLag(Connection ignored, String startWalFile) {
      reads.incrementAndGet();
      return lag;
    }
  }

  /**
   * Above a 10-byte bound while {@code pg_stat_archiver} shows the statistics of the first read,
   * which resets them from another session: PostgreSQL serves these statistics from a snapshot held
   * until the end of the transaction.
   */
  private static final class V6_20261117160000000__Archiver_lag
      extends V6_20261117160000000__Convert_findings_to_identity {

    private final DataSource otherSession;
    private final AtomicInteger reads = new AtomicInteger();
    private Timestamp firstReset;

    V6_20261117160000000__Archiver_lag(DataSource otherSession) {
      super(1L, 0, 10);
      this.otherSession = otherSession;
    }

    @Override
    WalLag walLag(Connection connection, String startWalFile) throws SQLException {
      reads.incrementAndGet();
      Timestamp reset;
      try (Statement statement = connection.createStatement();
          ResultSet result = statement.executeQuery("SELECT stats_reset FROM pg_stat_archiver")) {
        result.next();
        reset = result.getTimestamp(1);
      }
      if (reads.get() == 1) {
        firstReset = reset;
        try (Connection other = otherSession.getConnection();
            Statement statement = other.createStatement()) {
          statement.execute("SELECT pg_stat_reset_shared('archiver')");
        }
      }
      return new WalLag(Objects.equals(reset, firstReset) ? 11 : 0, 0);
    }
  }

  private record OccurrenceSeed(int inject, Instant date) {}

  private static OccurrenceSeed occurrence(int inject, Instant date) {
    return new OccurrenceSeed(inject, date);
  }

  /**
   * An old-shape row on the seeded asset. The filler spreads a handful of rows over several pages.
   */
  private String seedOldFinding(
      String suffix, String value, Instant createdAt, Instant updatedAt, OccurrenceSeed... seeds)
      throws SQLException {
    String id = marker + "-" + suffix;
    execute(
        "INSERT INTO findings (finding_id, finding_field, finding_type, finding_value,"
            + " finding_value_display, finding_name, tenant_id, finding_created_at,"
            + " finding_updated_at) SELECT ?, 'output', 'Text', ?, string_agg(md5(i || ?), ''),"
            + " 'seeded', ?, ?, ? FROM generate_series(1, 50) i",
        id,
        value,
        id,
        DEFAULT_TENANT_UUID,
        Timestamp.from(createdAt),
        Timestamp.from(updatedAt));
    execute("INSERT INTO findings_assets (finding_id, asset_id) VALUES (?, ?)", id, asset);
    for (OccurrenceSeed seed : seeds) {
      execute(
          "INSERT INTO finding_occurrences (finding_occurrence_id, finding_occurrence_finding_id,"
              + " finding_occurrence_inject_id, finding_occurrence_date, finding_occurrence_field)"
              + " VALUES (?, ?, ?, ?, 'output')",
          UUID.randomUUID().toString(),
          id,
          injects.get(seed.inject()),
          Timestamp.from(seed.date()));
    }
    return id;
  }

  private void onInject(String findingId, int inject) throws SQLException {
    execute(
        "UPDATE findings SET finding_inject_id = ? WHERE finding_id = ?",
        injects.get(inject),
        findingId);
  }

  private void sentAt(int inject, Instant sentAt) throws SQLException {
    execute(
        "INSERT INTO injects_statuses (status_id, status_inject, status_name, tracking_sent_date)"
            + " VALUES (?, ?, 'EXECUTED', ?)",
        UUID.randomUUID().toString(),
        injects.get(inject),
        Timestamp.from(sentAt));
  }

  private void walk(V6_20261117160000000__Convert_findings_to_identity conversion)
      throws SQLException {
    walk(conversion, new WalGuard(null));
  }

  // As the migration runs: autocommit off, each batch committed; a failed batch rolls back
  private void walk(V6_20261117160000000__Convert_findings_to_identity conversion, WalGuard guard)
      throws SQLException {
    connection.setAutoCommit(false);
    try {
      conversion.walk(connection, guard);
    } finally {
      connection.rollback();
      connection.setAutoCommit(true);
    }
  }

  private void drain(V6_20261117160000000__Convert_findings_to_identity conversion)
      throws SQLException {
    connection.setAutoCommit(false);
    try {
      while (conversion.convertNextBatch(connection)) {
        connection.commit();
      }
    } finally {
      connection.rollback();
      connection.setAutoCommit(true);
    }
  }

  // The test logging configuration logs errors alone
  private static ListAppender<ILoggingEvent> captureLogs() {
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    migrationLogger().setLevel(Level.WARN);
    migrationLogger().addAppender(appender);
    return appender;
  }

  private static void releaseLogs(ListAppender<ILoggingEvent> appender) {
    migrationLogger().detachAppender(appender);
    migrationLogger().setLevel(null);
    appender.stop();
  }

  private static Logger migrationLogger() {
    return (Logger)
        LoggerFactory.getLogger(V6_20261117160000000__Convert_findings_to_identity.class);
  }

  private static List<String> warnings(ListAppender<ILoggingEvent> appender) {
    return appender.list.stream()
        .filter(event -> event.getLevel() == Level.WARN)
        .map(ILoggingEvent::getFormattedMessage)
        .toList();
  }

  private void logUpdatesOfConvertedFindings() throws SQLException {
    execute("CREATE TABLE conversion_test_log (finding_id varchar)");
    execute(
        "CREATE FUNCTION conversion_test_trigger() RETURNS trigger LANGUAGE plpgsql AS $$"
            + " BEGIN INSERT INTO conversion_test_log VALUES (NEW.finding_id); RETURN NEW; END $$");
    execute(
        "CREATE TRIGGER conversion_test_trigger AFTER UPDATE ON findings FOR EACH ROW"
            + " WHEN (NEW.finding_value_hash IS NOT NULL AND NEW.finding_value LIKE '"
            + marker
            + "%') EXECUTE FUNCTION conversion_test_trigger()");
  }

  /** Fails the batch that writes a converted finding first seen at {@code firstSeen}. */
  private void failWritesBroughtBy(Instant firstSeen) throws SQLException {
    execute(
        "CREATE FUNCTION conversion_test_trigger() RETURNS trigger LANGUAGE plpgsql AS $$"
            + " BEGIN RAISE EXCEPTION 'interrupted'; END $$");
    execute(
        "CREATE TRIGGER conversion_test_trigger BEFORE INSERT OR UPDATE ON findings FOR EACH ROW"
            + " WHEN (NEW.finding_value_hash IS NOT NULL AND NEW.finding_value LIKE '"
            + marker
            + "%' AND NEW.finding_first_seen = '"
            + firstSeen
            + "') EXECUTE FUNCTION conversion_test_trigger()");
  }

  private List<String> unconverted(List<String> ids) throws SQLException {
    return rows(
            "SELECT finding_id FROM findings WHERE finding_id = ANY (?)"
                + " AND finding_value_hash IS NULL",
            connection.createArrayOf("varchar", ids.toArray()))
        .stream()
        .map(row -> (String) row[0])
        .toList();
  }

  private List<String> occurrencesOf(String value) throws SQLException {
    return rows(
            "SELECT o.finding_occurrence_inject_id, o.finding_occurrence_date"
                + " FROM finding_occurrences o JOIN findings f"
                + " ON f.finding_id = o.finding_occurrence_finding_id"
                + " WHERE f.finding_value = ? ORDER BY 1",
            value)
        .stream()
        .map(row -> row[0] + "@" + ((Timestamp) row[1]).toInstant())
        .toList();
  }

  private long pageOf(String findingId) throws SQLException {
    return count(
        "SELECT (ctid::text::point)[0]::bigint FROM findings WHERE finding_id = ?", findingId);
  }

  private List<Object[]> versionsOf(String value) throws SQLException {
    return rows(
        "SELECT f.xmin::text, f.ctid::text FROM findings f WHERE f.finding_value = ?"
            + " UNION ALL SELECT o.xmin::text, o.ctid::text FROM finding_occurrences o"
            + " JOIN findings f ON f.finding_id = o.finding_occurrence_finding_id"
            + " WHERE f.finding_value = ? ORDER BY 2",
        value,
        value);
  }

  private Instant databaseNow() throws SQLException {
    return ((Timestamp) rows("SELECT now()").getFirst()[0]).toInstant();
  }

  private void execute(String sql, Object... parameters) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      for (int i = 0; i < parameters.length; i++) {
        statement.setObject(i + 1, parameters[i]);
      }
      statement.execute();
    }
  }

  private long count(String sql, Object... parameters) throws SQLException {
    return ((Number) rows(sql, parameters).getFirst()[0]).longValue();
  }

  private List<Object[]> rows(String sql, Object... parameters) throws SQLException {
    List<Object[]> rows = new ArrayList<>();
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      for (int i = 0; i < parameters.length; i++) {
        statement.setObject(i + 1, parameters[i]);
      }
      try (ResultSet result = statement.executeQuery()) {
        int columns = result.getMetaData().getColumnCount();
        while (result.next()) {
          Object[] row = new Object[columns];
          for (int c = 0; c < columns; c++) {
            row[c] = result.getObject(c + 1);
          }
          rows.add(row);
        }
      }
    }
    return rows;
  }
}
