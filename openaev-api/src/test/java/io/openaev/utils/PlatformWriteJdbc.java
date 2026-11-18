package io.openaev.utils;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Consumer;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * Committed JDBC writes in a transaction marked platform-write ({@code app.platform_write}), the
 * only kind of transaction in which the database accepts a write of a platform role ({@code
 * roles.tenant_id IS NULL}) or of one of its {@code roles_capabilities} rows.
 */
public final class PlatformWriteJdbc {

  private PlatformWriteJdbc() {}

  /**
   * Runs the writes in one transaction of its own that sets {@code app.platform_write} first, then
   * commits. A write that fails rolls the transaction back and rethrows.
   */
  public static void inPlatformWriteTransaction(
      DataSource dataSource, Consumer<JdbcTemplate> writes) {
    try (Connection connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      try {
        JdbcTemplate writer = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
        writer.queryForObject(
            "SELECT set_config('app.platform_write', 'true', true)", String.class);
        writes.accept(writer);
        connection.commit();
      } catch (RuntimeException e) {
        connection.rollback();
        throw e;
      }
    } catch (SQLException e) {
      throw new IllegalStateException("Could not run the platform write", e);
    }
  }
}
