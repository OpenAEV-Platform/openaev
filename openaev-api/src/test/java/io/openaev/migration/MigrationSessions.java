package io.openaev.migration;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.fail;

import jakarta.persistence.EntityManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.hibernate.Session;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Runs migrations on a given connection in the tests: on the session connection of the test
 * transaction, or, for the concurrency tests, on sessions of a migration and of a platform on the
 * earlier version, each on its own connection to the real database.
 */
public final class MigrationSessions {

  private MigrationSessions() {}

  @FunctionalInterface
  interface ConnectionWork {
    void run(Connection connection) throws Exception;
  }

  public static Context contextOn(Connection connection) {
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

  /** Runs the work on the connection of the session, so it sees the rows the test has flushed. */
  static void withConnection(EntityManager entityManager, ConnectionWork work) {
    entityManager.unwrap(Session.class).doWork(connection -> run(work, connection));
  }

  static void withConnection(JdbcTemplate jdbcTemplate, ConnectionWork work) {
    jdbcTemplate.execute(
        (Connection connection) -> {
          run(work, connection);
          return null;
        });
  }

  private static void run(ConnectionWork work, Connection connection) {
    try {
      work.run(connection);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /** Runs the migration as Flyway does: in a transaction when the migration accepts one. */
  static Void migrate(BaseJavaMigration migration, Connection connection) throws Exception {
    Context context = contextOn(connection);
    if (migration.canExecuteInTransaction()) {
      connection.setAutoCommit(false);
      try {
        migration.migrate(context);
        connection.commit();
      } catch (Exception e) {
        connection.rollback();
        throw e;
      } finally {
        connection.setAutoCommit(true);
      }
    } else {
      migration.migrate(context);
    }
    return null;
  }

  static void read(Connection connection, String table) throws SQLException {
    execute(connection, "SELECT count(*) FROM " + table);
  }

  static void execute(Connection connection, String sql) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }

  static int backendPid(Connection connection) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement("SELECT pg_backend_pid()");
        ResultSet resultSet = statement.executeQuery()) {
      resultSet.next();
      return resultSet.getInt(1);
    }
  }

  /**
   * Polls until the session of this backend has reached a lock it cannot take: it waits on it, or
   * it has given way and rolled back, its transaction or the step's savepoint.
   */
  static void awaitLockConflict(JdbcTemplate jdbc, int pid) throws InterruptedException {
    long deadline = System.nanoTime() + SECONDS.toNanos(30);
    while (System.nanoTime() < deadline) {
      Long conflicts =
          jdbc.queryForObject(
              "SELECT (SELECT count(*) FROM pg_locks WHERE pid = ? AND NOT granted)"
                  + " + (SELECT count(*) FROM pg_stat_activity"
                  + " WHERE pid = ? AND state LIKE 'idle%'"
                  + " AND (query LIKE 'ROLLBACK%' OR state = 'idle in transaction (aborted)'))",
              Long.class, pid, pid);
      if (conflicts != null && conflicts > 0) {
        return;
      }
      Thread.sleep(20);
    }
    fail("the session %d never reached a lock it cannot take", pid);
  }

  static boolean columnExists(JdbcTemplate jdbc, String table, String column) {
    Long count =
        jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.columns"
                + " WHERE table_name = ? AND column_name = ?",
            Long.class,
            table,
            column);
    return count != null && count == 1;
  }
}
