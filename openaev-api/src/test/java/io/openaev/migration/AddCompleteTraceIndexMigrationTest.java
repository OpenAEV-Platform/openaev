package io.openaev.migration;

import static io.openaev.migration.V6_20261120110000000__Add_complete_trace_index_on_execution_traces.INDEX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openaev.IntegrationTest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Verifies, on the live schema, that the partial COMPLETE index of {@code execution_traces} is
 * built outside a transaction, that a second run keeps it, and that an index left invalid by an
 * interrupted concurrent build is built again.
 *
 * <p>A concurrent build refuses to run in a transaction, so this class is not {@code
 * {@literal @}Transactional}: it runs the migration on its own autocommit connection, and {@code
 * {@literal @}AfterEach} runs it once more so the shared database ends with a valid index.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Migration V6_20261120110000000: partial COMPLETE index on execution_traces")
class AddCompleteTraceIndexMigrationTest extends IntegrationTest {

  @Autowired private V6_20261120110000000__Add_complete_trace_index_on_execution_traces migration;
  @Autowired private DataSource dataSource;

  private Connection connection;

  @BeforeEach
  void openConnection() throws SQLException {
    connection = dataSource.getConnection();
    connection.setAutoCommit(true);
  }

  @AfterEach
  void restoreIndex() throws SQLException {
    try {
      execute("RESET statement_timeout");
      migration.migrate(connection);
    } finally {
      connection.close();
    }
  }

  @Test
  @DisplayName("The migration does not ask Flyway for a wrapping transaction")
  void given_migration_should_runOutsideATransaction() {
    // Act + Assert
    assertThat(migration.canExecuteInTransaction()).isFalse();
  }

  @Test
  @DisplayName("A valid index is kept as it is by a second run")
  void given_validIndex_should_keepIt() throws SQLException {
    // Arrange
    long before = indexOid();

    // Act
    migration.migrate(connection);

    // Assert
    assertThat(indexOid()).as("the same index, not a rebuilt one").isEqualTo(before);
    assertThat(isValid()).isTrue();
  }

  @Test
  @DisplayName("An index left invalid by an interrupted concurrent build is built again")
  void given_invalidIndex_should_buildItAgain() throws SQLException {
    // Arrange: a concurrent build creates its index, then waits for the transactions that may write
    // to the table; one holds a write lock, so the build times out and leaves its index invalid
    execute("DROP INDEX CONCURRENTLY IF EXISTS " + INDEX);
    try (Connection writer = dataSource.getConnection()) {
      writer.setAutoCommit(false);
      try (Statement statement = writer.createStatement()) {
        statement.execute("LOCK TABLE execution_traces IN ROW EXCLUSIVE MODE");
        execute("SET statement_timeout = '1s'");
        assertThatThrownBy(
                () ->
                    execute(
                        "CREATE INDEX CONCURRENTLY "
                            + INDEX
                            + " ON execution_traces (execution_inject_status_id)"))
            .isInstanceOf(SQLException.class);
        execute("RESET statement_timeout");
      } finally {
        writer.rollback();
      }
    }
    assertThat(isValid()).as("the interrupted build left an invalid index").isFalse();

    // Act
    migration.migrate(connection);

    // Assert
    assertThat(isValid()).isTrue();
    assertThat(definition())
        .contains("(execution_inject_status_id)")
        .contains("INCLUDE (execution_status, execution_agent_id)")
        .contains("WHERE ((execution_action)::text = 'COMPLETE'::text)");
  }

  // -- HELPERS --

  private void execute(String sql) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }

  private long indexOid() throws SQLException {
    return ((Number) single("SELECT CAST(CAST('" + INDEX + "' AS regclass) AS oid)")).longValue();
  }

  private boolean isValid() throws SQLException {
    return (Boolean)
        single(
            "SELECT i.indisvalid FROM pg_index i JOIN pg_class c ON c.oid = i.indexrelid"
                + " WHERE c.relname = '"
                + INDEX
                + "'");
  }

  private String definition() throws SQLException {
    return (String) single("SELECT pg_get_indexdef(CAST('" + INDEX + "' AS regclass))");
  }

  private Object single(String sql) throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet row = statement.executeQuery(sql)) {
      assertThat(row.next()).as(sql).isTrue();
      return row.getObject(1);
    }
  }
}
