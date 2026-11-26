package io.openaev.migration;

import static io.openaev.migration.MigrationSessions.contextOn;
import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Not {@code @Transactional}: the index is built {@code CONCURRENTLY}, which PostgreSQL refuses
 * inside a transaction, so each test runs on its own autocommit connection and {@code @AfterEach}
 * runs the migration again to leave the shared schema complete.
 */
@DisplayName("Migration V6_20261119013000000: graph landing indexes")
class AddGraphLandingIndexesMigrationTest extends IntegrationTest {

  private static final String INDEX =
      V6_20261119013000000__Add_graph_landing_indexes.FAILED_ASSET_INDEX;
  private static final String GROUP_MEMBER_INDEX = "idx_asset_groups_assets_asset";

  @Autowired private V6_20261119013000000__Add_graph_landing_indexes migration;
  @Autowired private DataSource dataSource;

  private Connection connection;

  @BeforeEach
  void openConnectionAndDropTheIndex() throws SQLException {
    connection = dataSource.getConnection();
    connection.setAutoCommit(true);
    execute("DROP INDEX CONCURRENTLY IF EXISTS " + INDEX);
  }

  @AfterEach
  void restoreSchema() throws Exception {
    try {
      migration.migrate(contextOn(connection));
    } finally {
      connection.close();
    }
  }

  @Test
  @DisplayName("The migration does not ask Flyway for a wrapping transaction")
  void given_migration_should_runOutsideATransaction() {
    assertThat(migration.canExecuteInTransaction()).isFalse();
  }

  @Test
  @DisplayName("Creates the partial index once, valid, and a rerun is a no-op")
  void given_noIndex_should_createItOnceAndRerunCleanly() throws Exception {
    // Act
    migration.migrate(contextOn(connection));
    migration.migrate(contextOn(connection));

    // Assert
    assertThat(rows("SELECT indexname FROM pg_indexes WHERE indexname = '" + INDEX + "'"))
        .containsExactly(INDEX);
    assertThat(rows("SELECT indexdef FROM pg_indexes WHERE indexname = '" + INDEX + "'"))
        .singleElement()
        .asString()
        .contains("WHERE")
        .contains("inject_expectation_score");
    assertThat(
            rows(
                "SELECT indisvalid::text FROM pg_index WHERE indexrelid = to_regclass('"
                    + INDEX
                    + "')"))
        .containsExactly("true");
  }

  @Test
  @DisplayName("Leaves the group membership index of V2_68 as it is")
  void given_groupMemberIndex_should_keepIt() throws Exception {
    // Act
    migration.migrate(contextOn(connection));

    // Assert
    assertThat(
            rows("SELECT indexname FROM pg_indexes WHERE indexname = '" + GROUP_MEMBER_INDEX + "'"))
        .containsExactly(GROUP_MEMBER_INDEX);
  }

  private void execute(String sql) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }

  private List<String> rows(String sql) throws SQLException {
    List<String> values = new ArrayList<>();
    try (Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(sql)) {
      while (result.next()) {
        values.add(result.getString(1));
      }
    }
    return values;
  }
}
