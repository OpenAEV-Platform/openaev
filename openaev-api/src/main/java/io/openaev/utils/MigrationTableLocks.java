package io.openaev.utils;

import io.openaev.annotation.AllowRawJdbc;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/**
 * Runs a migration step inside the migration's transaction, after taking an {@code ACCESS
 * EXCLUSIVE} lock on each of its tables, in the given order, with {@code NOWAIT}.
 *
 * <p>During a rolling upgrade the platform on the earlier version holds locks on the same tables. A
 * lock request that waits joins the table's lock queue, so every later read of that table waits
 * behind it, and it can close a deadlock with a transaction that holds one table and reads the
 * other. With {@code NOWAIT} the step never waits on its table locks: when one is held elsewhere,
 * the step rolls back to a savepoint, which releases what it took, pauses and tries again, with a
 * growing pause and a bounded number of attempts. Any other lock the step needs waits at most a
 * short {@code lock_timeout}, with the same retry.
 *
 * <p>The step runs in the migration's transaction, so a step that fails leaves no trace: Flyway
 * rolls the transaction back, records nothing, and the next boot runs the migration again.
 */
@AllowRawJdbc(
    reason =
        "runs a Flyway migration step on the migration's own connection, as the classes of"
            + " io.openaev.migration do; the statements are the calling migration's, which"
            + " covers every tenant on purpose")
@Slf4j
public final class MigrationTableLocks {

  static final String LOCK_TIMEOUT = "500ms";
  public static final Retry RETRY = new Retry(60, 100, 5_000);

  private static final Set<String> LOCK_CONFLICTS =
      Set.of(
          "55P03", // lock_not_available: NOWAIT, or lock_timeout expired
          "40P01"); // deadlock_detected
  private static final Pattern TABLE_NAME = Pattern.compile("[a-z_][a-z0-9_]*");
  private static final String LOCK_HOLDERS =
      "SELECT l.relation::regclass::text, l.mode, a.pid, a.application_name, a.state,"
          + " a.query_start"
          + " FROM pg_locks l JOIN pg_stat_activity a ON a.pid = l.pid"
          + " WHERE l.granted AND l.pid <> pg_backend_pid()"
          + " AND l.database = (SELECT oid FROM pg_database WHERE datname = current_database())"
          + " AND l.relation IN (SELECT to_regclass(t) FROM unnest(string_to_array(?, ',')) t)"
          + " ORDER BY a.query_start LIMIT 10";

  private MigrationTableLocks() {}

  // A driver error can carry no SQLState: it is not a lock conflict, so it fails the step.
  private static boolean isLockConflict(SQLException e) {
    String sqlState = e.getSQLState();
    return sqlState != null && LOCK_CONFLICTS.contains(sqlState);
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
  public record Retry(int maxAttempts, long firstPauseMillis, long maxPauseMillis) {
    long pauseAfter(int attempt) {
      return Math.min(maxPauseMillis, firstPauseMillis << Math.min(attempt - 1, 20));
    }
  }

  /**
   * Runs {@code work} once every table of {@code tables} is locked, in that order, and leaves the
   * commit to the migration's transaction.
   *
   * @throws IllegalStateException when the connection is in autocommit: the migration must run in a
   *     transaction, which is what {@code canExecuteInTransaction()} returns by default
   * @throws SQLException with the lock conflict's SQLState once the attempts are spent, the message
   *     naming the step, the attempts and the sessions holding the tables; or any other error of
   *     the step, after rolling the step back
   */
  public static void runLocked(
      Connection connection, String step, List<String> tables, SqlWork work) throws SQLException {
    runLocked(connection, step, tables, work, RETRY);
  }

  public static void runLocked(
      Connection connection, String step, List<String> tables, SqlWork work, Retry retry)
      throws SQLException {
    runLocked(connection, step, tables, work, retry, Thread::sleep);
  }

  static void runLocked(
      Connection connection,
      String step,
      List<String> tables,
      SqlWork work,
      Retry retry,
      Pause pause)
      throws SQLException {
    for (String table : tables) {
      if (!TABLE_NAME.matcher(table).matches()) {
        throw new IllegalArgumentException("Not a table name: " + table);
      }
    }
    if (connection.getAutoCommit()) {
      throw new IllegalStateException(
          step + " runs inside the migration's transaction, and the connection has none");
    }
    String callerLockTimeout = lockTimeout(connection);
    for (int attempt = 1; ; attempt++) {
      Savepoint savepoint = connection.setSavepoint();
      try {
        lockAndRun(connection, tables, work, callerLockTimeout);
        connection.releaseSavepoint(savepoint);
        return;
      } catch (SQLException e) {
        if (!rolledBack(connection, savepoint, e) || !isLockConflict(e)) {
          throw e;
        }
        if (attempt >= retry.maxAttempts()) {
          throw gaveUp(connection, step, tables, attempt, e);
        }
        pauseBeforeRetry(step, attempt, retry, pause, e);
      } catch (RuntimeException | Error e) {
        rolledBack(connection, savepoint, e);
        throw e;
      }
    }
  }

  private static void lockAndRun(
      Connection connection, List<String> tables, SqlWork work, String callerLockTimeout)
      throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute("SET LOCAL lock_timeout = '" + LOCK_TIMEOUT + "'");
      for (String table : tables) {
        statement.execute("LOCK TABLE " + table + " IN ACCESS EXCLUSIVE MODE NOWAIT");
      }
      work.run(statement);
    }
    // The rest of the migration's transaction, Flyway's history row included, waits as the caller
    // set it to.
    try (PreparedStatement restore =
        connection.prepareStatement("SELECT set_config('lock_timeout', ?, true)")) {
      restore.setString(1, callerLockTimeout);
      restore.execute();
    }
  }

  private static String lockTimeout(Connection connection) throws SQLException {
    try (PreparedStatement statement =
            connection.prepareStatement("SELECT current_setting('lock_timeout')");
        ResultSet resultSet = statement.executeQuery()) {
      resultSet.next();
      return resultSet.getString(1);
    }
  }

  /** Rolls the step back to its savepoint; a failure to do so is added to {@code failure}. */
  private static boolean rolledBack(Connection connection, Savepoint savepoint, Throwable failure) {
    try {
      connection.rollback(savepoint);
      return true;
    } catch (SQLException | RuntimeException rollbackFailure) {
      failure.addSuppressed(rollbackFailure);
      return false;
    }
  }

  private static void pauseBeforeRetry(
      String step, int attempt, Retry retry, Pause pause, SQLException conflict)
      throws SQLException {
    long millis = retry.pauseAfter(attempt);
    log.info(
        "{}: attempt {} of {} gave way to a lock held elsewhere, retrying in {} ms",
        step,
        attempt,
        retry.maxAttempts(),
        millis);
    try {
      pause.sleep(millis);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      conflict.addSuppressed(interrupted);
      throw conflict;
    }
  }

  private static SQLException gaveUp(
      Connection connection, String step, List<String> tables, int attempts, SQLException last) {
    String message =
        "%s: gave up after %d attempts to lock %s without waiting. Likely cause: a platform of the"
                .formatted(step, attempts, String.join(", ", tables))
            + " earlier version is running and holds these tables; stop it, then restart this"
            + " one. Sessions holding them: "
            + lockHolders(connection, tables);
    return new SQLException(message, last.getSQLState(), last.getErrorCode(), last);
  }

  /** Who holds the tables, without the text of their queries. */
  private static String lockHolders(Connection connection, List<String> tables) {
    try (PreparedStatement statement = connection.prepareStatement(LOCK_HOLDERS)) {
      statement.setString(1, String.join(",", tables));
      List<String> holders = new ArrayList<>();
      try (ResultSet resultSet = statement.executeQuery()) {
        while (resultSet.next()) {
          holders.add(
              "pid %d (application '%s', %s since %s, %s on %s)"
                  .formatted(
                      resultSet.getInt(3),
                      resultSet.getString(4),
                      resultSet.getString(5),
                      resultSet.getTimestamp(6),
                      resultSet.getString(2),
                      resultSet.getString(1)));
        }
      }
      return holders.isEmpty() ? "none left at the last attempt" : String.join("; ", holders);
    } catch (SQLException | RuntimeException e) {
      // The lock conflict is the error to report; the list of holders only helps read it.
      return "unknown (" + e.getMessage() + ")";
    }
  }
}
