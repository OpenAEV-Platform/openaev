package io.openaev.utils;

import io.openaev.annotation.AllowRawJdbc;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/**
 * Runs a migration step in its own transaction, after taking its table locks in the given order
 * with {@code NOWAIT}.
 *
 * <p>During a rolling upgrade the platform on the earlier version holds locks on the same tables. A
 * lock request that waits joins the table's lock queue, so every later read of that table waits
 * behind it, and it can close a deadlock with a transaction that holds one table and reads the
 * other. With {@code NOWAIT} the step never waits on its table locks: when one is held elsewhere,
 * the step rolls back, pauses and tries again, with a growing pause and a bounded number of
 * attempts. Any other lock the step needs waits at most a short {@code lock_timeout}, with the same
 * retry.
 */
@AllowRawJdbc(
    reason =
        "runs a Flyway migration step on the migration's own connection, as the classes of"
            + " io.openaev.migration do; the statements are the calling migration's, which"
            + " covers every tenant on purpose")
@Slf4j
public final class MigrationTableLocks {

  static final String LOCK_TIMEOUT = "500ms";
  static final Retry RETRY = new Retry(60, 100, 5_000);

  private static final Set<String> LOCK_CONFLICTS =
      Set.of(
          "55P03", // lock_not_available: NOWAIT, or lock_timeout expired
          "40P01"); // deadlock_detected
  private static final Pattern TABLE_NAME = Pattern.compile("[a-z_][a-z0-9_]*");

  private MigrationTableLocks() {}

  /** PostgreSQL table lock modes, from the weakest one a statement needs. */
  public enum LockMode {
    /** {@code ALTER TABLE ... ADD COLUMN} or {@code DROP COLUMN}. */
    ACCESS_EXCLUSIVE("ACCESS EXCLUSIVE");

    private final String sql;

    LockMode(String sql) {
      this.sql = sql;
    }
  }

  public record TableLock(String table, LockMode mode) {
    public TableLock {
      if (!TABLE_NAME.matcher(table).matches()) {
        throw new IllegalArgumentException("Not a table name: " + table);
      }
    }
  }

  @AllowRawJdbc(reason = "the statements of a migration step, run by runLocked")
  @FunctionalInterface
  public interface SqlWork {
    void run(Statement statement) throws SQLException;
  }

  @FunctionalInterface
  interface Pause {
    void sleep(long millis) throws InterruptedException;
  }

  /** The pause after attempt {@code n} doubles from {@code firstPauseMillis} up to the maximum. */
  record Retry(int maxAttempts, long firstPauseMillis, long maxPauseMillis) {
    long pauseAfter(int attempt) {
      return Math.min(maxPauseMillis, firstPauseMillis << Math.min(attempt - 1, 20));
    }
  }

  /**
   * Commits {@code work} once every lock of {@code locks} is taken, in that order.
   *
   * @throws IllegalStateException when the connection is inside a transaction: the migration must
   *     return false from {@code canExecuteInTransaction()}
   * @throws SQLException the last lock conflict once the attempts are spent, or any other error
   */
  public static void runLocked(
      Connection connection, String step, List<TableLock> locks, SqlWork work)
      throws SQLException, InterruptedException {
    runLocked(connection, step, locks, work, RETRY, Thread::sleep);
  }

  static void runLocked(
      Connection connection,
      String step,
      List<TableLock> locks,
      SqlWork work,
      Retry retry,
      Pause pause)
      throws SQLException, InterruptedException {
    if (!connection.getAutoCommit()) {
      throw new IllegalStateException(
          step + " commits its own transaction and cannot run inside another one");
    }
    connection.setAutoCommit(false);
    try {
      for (int attempt = 1; ; attempt++) {
        try (Statement statement = connection.createStatement()) {
          statement.execute("SET LOCAL lock_timeout = '" + LOCK_TIMEOUT + "'");
          for (TableLock lock : locks) {
            statement.execute(
                "LOCK TABLE " + lock.table() + " IN " + lock.mode().sql + " MODE NOWAIT");
          }
          work.run(statement);
          connection.commit();
          return;
        } catch (SQLException e) {
          connection.rollback();
          if (!LOCK_CONFLICTS.contains(e.getSQLState()) || attempt >= retry.maxAttempts()) {
            throw e;
          }
          long millis = retry.pauseAfter(attempt);
          log.info(
              "{}: attempt {} of {} gave way to a lock held elsewhere, retrying in {} ms",
              step,
              attempt,
              retry.maxAttempts(),
              millis);
          pause.sleep(millis);
        }
      }
    } finally {
      connection.setAutoCommit(true);
    }
  }
}
