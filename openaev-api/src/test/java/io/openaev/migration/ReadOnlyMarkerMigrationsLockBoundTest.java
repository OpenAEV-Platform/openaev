package io.openaev.migration;

import static io.openaev.migration.MigrationSessions.backendPid;
import static io.openaev.migration.MigrationSessions.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.openaev.IntegrationTest;
import io.openaev.utils.MigrationTableLocks.Retry;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.migration.JavaMigration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Both read-only marker migrations run by Flyway, configured as the platform configures it, against
 * a platform on the earlier version that holds {@code payloads} for longer than the retry bound.
 * The tables live in a schema of their own, so the platform schema is never altered.
 */
@DisplayName("Read-only marker migrations: lock held past the retry bound, run by Flyway")
class ReadOnlyMarkerMigrationsLockBoundTest extends IntegrationTest {

  private static final String SCHEMA = "read_only_marker_lock_bound";
  private static final String HISTORY = "migrations";
  private static final Retry SHORT_BOUND = new Retry(3, 10, 10);
  private static final String CONTRACT_COLUMN = "injector_contract_read_only";
  private static final String PAYLOAD_COLUMN = "payload_read_only";

  @Autowired private DataSource dataSource;

  @Value("${spring.datasource.url}")
  private String url;

  @Value("${spring.datasource.username}")
  private String user;

  @Value("${spring.datasource.password}")
  private String password;

  private JdbcTemplate jdbc;

  @BeforeEach
  void createSchema() {
    jdbc = new JdbcTemplate(dataSource);
    dropSchema();
    jdbc.execute("CREATE SCHEMA " + SCHEMA);
    jdbc.execute("CREATE TABLE " + SCHEMA + ".payloads (payload_id VARCHAR(255) PRIMARY KEY)");
    jdbc.execute(
        "CREATE TABLE "
            + SCHEMA
            + ".injectors_contracts (injector_contract_id VARCHAR(255) PRIMARY KEY)");
    jdbc.execute(
        "CREATE TABLE "
            + SCHEMA
            + ".parameters (parameter_id VARCHAR(255) PRIMARY KEY, parameter_key VARCHAR(255))");
  }

  @AfterEach
  void dropSchema() {
    jdbc.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
  }

  /** Flyway with the platform's settings, applying {@code migration} alone. */
  private Flyway flyway(JavaMigration migration) {
    return Flyway.configure()
        .dataSource(url, user, password)
        .schemas(SCHEMA)
        .table(HISTORY)
        .locations("classpath:" + SCHEMA)
        .baselineOnMigrate(true)
        .baselineVersion("0")
        .configuration(Map.of("flyway.postgresql.transactional.lock", "false"))
        .javaMigrations(migration)
        .load();
  }

  private boolean columnExists(String table, String column) {
    Long count =
        jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.columns"
                + " WHERE table_schema = ? AND table_name = ? AND column_name = ?",
            Long.class,
            SCHEMA,
            table,
            column);
    return count != null && count == 1;
  }

  private List<Map<String, Object>> historyRows(String version) {
    return jdbc.queryForList(
        "SELECT version, success FROM " + SCHEMA + "." + HISTORY + " WHERE version = ?", version);
  }

  private long failedHistoryRows() {
    Long count =
        jdbc.queryForObject(
            "SELECT count(*) FROM " + SCHEMA + "." + HISTORY + " WHERE NOT success", Long.class);
    return count == null ? 0 : count;
  }

  /** Runs Flyway while another session holds {@code payloads}, and returns what it threw. */
  private Throwable migrateWhilePayloadsHeld(JavaMigration migration, int[] holderPid)
      throws SQLException {
    try (Connection platform = dataSource.getConnection()) {
      platform.setAutoCommit(false);
      try {
        read(platform, SCHEMA + ".payloads");
        holderPid[0] = backendPid(platform);
        return catchThrowable(() -> flyway(migration).migrate());
      } finally {
        platform.rollback();
      }
    }
  }

  private static SQLException lockFailure(Throwable thrown, String step) {
    for (Throwable t = thrown; t != null; t = t.getCause()) {
      if (t instanceof SQLException e
          && e.getMessage() != null
          && e.getMessage().startsWith(step)) {
        return e;
      }
    }
    throw new AssertionError("no SQLException from step " + step + " in " + thrown, thrown);
  }

  @Nested
  @DisplayName("V6_20261121160000000: add the read-only marker")
  class AddMarker {

    private static final String VERSION = "6.20261121160000000";

    @Test
    @DisplayName(
        "Given payloads held past the bound, should roll back with no failed row, then pass")
    void given_payloadsHeldPastTheBound_should_addNothingRecordNoFailureThenAddOnceFree()
        throws Exception {
      // Arrange
      int[] holderPid = new int[1];

      // Act
      Throwable thrown =
          migrateWhilePayloadsHeld(
              new V6_20261121160000000__Coverage_scenario_safe_mode_and_approval(SHORT_BOUND),
              holderPid);

      // Assert: nothing recorded, nothing changed, and the failure says why
      assertThat(thrown).isNotNull();
      assertThat(failedHistoryRows()).isZero();
      assertThat(historyRows(VERSION)).isEmpty();
      assertThat(columnExists("injectors_contracts", CONTRACT_COLUMN)).isFalse();
      assertThat(columnExists("payloads", PAYLOAD_COLUMN)).isFalse();
      SQLException failure = lockFailure(thrown, "Read-only marker columns");
      assertThat(failure.getSQLState()).isEqualTo("55P03");
      assertThat(failure.getMessage())
          .contains("3 attempts", "payloads", "earlier version", "pid " + holderPid[0]);

      // Act: the next boot, with the tables free
      flyway(new V6_20261121160000000__Coverage_scenario_safe_mode_and_approval(SHORT_BOUND))
          .migrate();

      // Assert
      assertThat(historyRows(VERSION))
          .singleElement()
          .extracting(r -> r.get("success"))
          .isEqualTo(true);
      assertThat(columnExists("injectors_contracts", CONTRACT_COLUMN)).isTrue();
      assertThat(columnExists("payloads", PAYLOAD_COLUMN)).isTrue();
    }
  }

  @Nested
  @DisplayName("V6_20261124100000000: drop the read-only marker")
  class DropMarker {

    private static final String VERSION = "6.20261124100000000";
    private static final String SETTING_KEY = "coverage_scenario_read_only_first";

    @BeforeEach
    void addMarker() {
      jdbc.execute(
          "ALTER TABLE "
              + SCHEMA
              + ".injectors_contracts ADD COLUMN "
              + CONTRACT_COLUMN
              + " BOOLEAN NOT NULL DEFAULT false");
      jdbc.execute(
          "ALTER TABLE "
              + SCHEMA
              + ".payloads ADD COLUMN "
              + PAYLOAD_COLUMN
              + " BOOLEAN NOT NULL DEFAULT false");
      jdbc.update(
          "INSERT INTO " + SCHEMA + ".parameters (parameter_id, parameter_key) VALUES ('p1', ?)",
          SETTING_KEY);
    }

    private long settingRows() {
      Long count =
          jdbc.queryForObject(
              "SELECT count(*) FROM " + SCHEMA + ".parameters WHERE parameter_key = ?",
              Long.class,
              SETTING_KEY);
      return count == null ? 0 : count;
    }

    @Test
    @DisplayName(
        "Given payloads held past the bound, should roll back with no failed row, then pass")
    void given_payloadsHeldPastTheBound_should_dropNothingRecordNoFailureThenDropOnceFree()
        throws Exception {
      // Arrange
      int[] holderPid = new int[1];

      // Act
      Throwable thrown =
          migrateWhilePayloadsHeld(
              new V6_20261124100000000__Drop_read_only_marker_and_coverage_read_only_first(
                  SHORT_BOUND),
              holderPid);

      // Assert: nothing recorded, nothing changed, and the failure says why
      assertThat(thrown).isNotNull();
      assertThat(failedHistoryRows()).isZero();
      assertThat(historyRows(VERSION)).isEmpty();
      assertThat(columnExists("injectors_contracts", CONTRACT_COLUMN)).isTrue();
      assertThat(columnExists("payloads", PAYLOAD_COLUMN)).isTrue();
      assertThat(settingRows()).isOne();
      SQLException failure = lockFailure(thrown, "Read-only marker drop");
      assertThat(failure.getSQLState()).isEqualTo("55P03");
      assertThat(failure.getMessage())
          .contains("3 attempts", "payloads", "earlier version", "pid " + holderPid[0]);

      // Act: the next boot, with the tables free
      flyway(
              new V6_20261124100000000__Drop_read_only_marker_and_coverage_read_only_first(
                  SHORT_BOUND))
          .migrate();

      // Assert
      assertThat(historyRows(VERSION))
          .singleElement()
          .extracting(r -> r.get("success"))
          .isEqualTo(true);
      assertThat(columnExists("injectors_contracts", CONTRACT_COLUMN)).isFalse();
      assertThat(columnExists("payloads", PAYLOAD_COLUMN)).isFalse();
      assertThat(settingRows()).isZero();
    }
  }
}
