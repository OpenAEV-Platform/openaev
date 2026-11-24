package io.openaev.migration;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Not {@code @Transactional}: the test changes the column type on its own autocommit connection,
 * and {@code @AfterEach} runs the migration again to leave the shared schema complete.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Migration V6_20261124100000000: precision of injects_statuses.tracking_sent_date")
class FixInjectStatusSentDatePrecisionMigrationTest extends IntegrationTest {

  @Autowired private V6_20261124100000000__Fix_inject_status_sent_date_precision migration;
  @Autowired private DataSource dataSource;

  private Connection connection;

  @BeforeEach
  void openConnectionAndRoundColumnToTheSecond() throws SQLException {
    connection = dataSource.getConnection();
    connection.setAutoCommit(true);
    try (Statement statement = connection.createStatement()) {
      statement.execute(
          "ALTER TABLE injects_statuses"
              + " ALTER COLUMN tracking_sent_date TYPE timestamp(0) with time zone");
    }
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
  @DisplayName(
      "given a column rounded to the second, should keep microseconds, and a rerun changes nothing")
  void given_columnRoundedToTheSecond_should_keepMicroseconds() throws Exception {
    // Arrange
    assertThat(sentDatePrecision()).isZero();

    // Act
    migration.migrate(contextOn(connection));
    migration.migrate(contextOn(connection));

    // Assert
    assertThat(sentDatePrecision()).isEqualTo(6);
  }

  private int sentDatePrecision() throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet result =
            statement.executeQuery(
                "SELECT datetime_precision FROM information_schema.columns"
                    + " WHERE table_name = 'injects_statuses'"
                    + " AND column_name = 'tracking_sent_date'")) {
      result.next();
      return result.getInt(1);
    }
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
