package io.openaev.migration;

import static io.openaev.migration.V6_20261023184049806__Add_expectation_results_table.INDEX;
import static io.openaev.migration.V6_20261023184049806__Add_expectation_results_table.JSON_COLUMN;
import static io.openaev.migration.V6_20261023184049806__Add_expectation_results_table.RESULTS_TABLE;
import static io.openaev.migration.V6_20261023184049806__Add_expectation_results_table.SOURCE_TABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openaev.IntegrationTest;
import io.openaev.migration.V6_20261023184049806__Add_expectation_results_table.WalGuard;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
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
  }

  @Nested
  @DisplayName("Walk")
  class Walk {

    @Test
    @DisplayName("The page-range walk alone fills every row, before the final catch-up pass")
    void given_pendingRows_should_beFilledByTheWalkAlone() throws SQLException {
      // Arrange: the filler makes a handful of rows span more than one page. walk() alone never
      // creates the table, so it is created here the way migrate() would.
      makePending();
      execute(V6_20261023184049806__Add_expectation_results_table.CREATE_TABLE);
      List<String> expectations = new ArrayList<>();
      for (int i = 0; i < 7; i++) {
        expectations.add(seedExpectation(inject, "[{\"result\":\"" + randomFiller() + "\"}]"));
      }

      // Act
      long written = small.walk(connection, new WalGuard(null));

      // Assert
      assertThat(written).isGreaterThanOrEqualTo(7);
      for (String expectation : expectations) {
        assertThat(resultsOf(expectation)).hasSize(1);
      }
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
      // Arrange: six expectations over several pages; the trigger fails the result of the sixth.
      // PLAIN storage keeps the filler inline (never TOASTed out of the row), so six rows reliably
      // span more than one page regardless of what other tests already put in this shared table.
      makePending();
      execute(
          "ALTER TABLE " + SOURCE_TABLE + " ALTER COLUMN " + JSON_COLUMN + " SET STORAGE PLAIN");
      List<String> expectations = new ArrayList<>();
      for (int i = 1; i <= 6; i++) {
        expectations.add(seedExpectation(inject, "[{\"result\":\"" + randomFiller() + "\"}]"));
      }
      String failing = expectations.get(5);
      execute(
          "CREATE FUNCTION test_result_fail() RETURNS trigger AS $$ BEGIN"
              + " IF NEW.inject_expectation_result_inject_expectation_id = '"
              + failing
              + "' THEN RAISE EXCEPTION 'seeded failure'; END IF;"
              + " RETURN NEW; END $$ LANGUAGE plpgsql");
      // Create the table ourselves (makePending() already dropped it) so the trigger can be
      // attached to it before the migration runs.
      execute(
          """
          CREATE TABLE inject_expectation_results(
              inject_expectation_result_id VARCHAR(255) PRIMARY KEY,
              inject_expectation_result_source_id VARCHAR(255) NULL,
              inject_expectation_result_source_type VARCHAR(255) NULL,
              inject_expectation_result_source_name VARCHAR(255) NULL,
              inject_expectation_result_source_asset_id VARCHAR(255) NULL,
              inject_expectation_result_source_platform VARCHAR(255) NULL,
              inject_expectation_result_date TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()::timestamptz,
              inject_expectation_result_result TEXT NULL,
              inject_expectation_result_score NUMERIC NULL,
              inject_expectation_result_metadata JSONB,
              inject_expectation_result_created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()::timestamptz,
              inject_expectation_result_updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()::timestamptz,
              inject_expectation_result_inject_expectation_id VARCHAR(255) NOT NULL REFERENCES injects_expectations(inject_expectation_id) ON DELETE CASCADE
          )
          """);
      execute(
          "CREATE TRIGGER test_result_fail BEFORE INSERT ON "
              + RESULTS_TABLE
              + " FOR EACH ROW EXECUTE FUNCTION test_result_fail()");

      // Act: the first run fails on the range holding the sixth expectation
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
                  "SELECT count(*) FROM information_schema.columns WHERE table_name = '"
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

  // -- assertions --

  private void assertFullSchema() throws SQLException {
    assertThat(
            count(
                "SELECT count(*) FROM information_schema.columns WHERE table_name = '"
                    + SOURCE_TABLE
                    + "' AND column_name = '"
                    + JSON_COLUMN
                    + "'"))
        .as("the JSON column is dropped")
        .isZero();
    assertThat(count("SELECT count(*) FROM pg_class WHERE relname = '" + RESULTS_TABLE + "'"))
        .as("the results table exists")
        .isEqualTo(1);
    assertThat(
            count(
                "SELECT count(*) FROM pg_index i JOIN pg_class c ON c.oid = i.indexrelid"
                    + " WHERE c.relname = '"
                    + INDEX
                    + "' AND i.indrelid = '"
                    + RESULTS_TABLE
                    + "'::regclass AND i.indisvalid"))
        .as("the lookup index is in place")
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
    String id = UUID.randomUUID().toString();
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
