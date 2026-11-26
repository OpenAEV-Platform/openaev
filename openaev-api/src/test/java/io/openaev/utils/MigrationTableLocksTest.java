package io.openaev.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.utils.MigrationTableLocks.Pause;
import io.openaev.utils.MigrationTableLocks.Retry;
import io.openaev.utils.MigrationTableLocks.SqlWork;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

@DisplayName("Migration table locks")
class MigrationTableLocksTest {

  private static final Retry RETRY = new Retry(4, 100, 250);
  private static final List<String> TABLES = List.of("payloads", "injectors_contracts");
  private static final String CALLER_LOCK_TIMEOUT = "0";

  private final List<Long> pauses = new ArrayList<>();
  private Connection connection;
  private Statement statement;
  private Savepoint savepoint;
  private PreparedStatement restoreLockTimeout;
  private PreparedStatement lockHolders;
  private ResultSet holders;

  @BeforeEach
  void setUp() throws SQLException {
    connection = mock(Connection.class);
    statement = mock(Statement.class);
    savepoint = mock(Savepoint.class);
    when(connection.getAutoCommit()).thenReturn(false);
    when(connection.createStatement()).thenReturn(statement);
    when(connection.setSavepoint()).thenReturn(savepoint);

    PreparedStatement readLockTimeout = mock(PreparedStatement.class);
    ResultSet lockTimeout = mock(ResultSet.class);
    when(connection.prepareStatement(startsWith("SELECT current_setting")))
        .thenReturn(readLockTimeout);
    when(readLockTimeout.executeQuery()).thenReturn(lockTimeout);
    when(lockTimeout.next()).thenReturn(true);
    when(lockTimeout.getString(1)).thenReturn(CALLER_LOCK_TIMEOUT);

    restoreLockTimeout = mock(PreparedStatement.class);
    when(connection.prepareStatement(startsWith("SELECT set_config")))
        .thenReturn(restoreLockTimeout);

    lockHolders = mock(PreparedStatement.class);
    holders = mock(ResultSet.class);
    when(connection.prepareStatement(startsWith("SELECT l.relation"))).thenReturn(lockHolders);
    when(lockHolders.executeQuery()).thenReturn(holders);
  }

  @AfterEach
  void clearInterrupt() {
    Thread.interrupted();
  }

  private void run(SqlWork work, Pause pause) throws SQLException {
    MigrationTableLocks.runLocked(connection, "step", TABLES, work, RETRY, pause);
  }

  private void run() throws SQLException {
    run(s -> s.execute("ALTER TABLE payloads"), pauses::add);
  }

  @Nested
  @DisplayName("Inside the migration's transaction")
  class InTransaction {

    @Test
    @DisplayName("Given a deadlock then free tables, should lock in order without waiting, release")
    void given_deadlockThenFreeTables_should_lockInOrderWithoutWaitingAndRelease()
        throws Exception {
      // Arrange
      when(statement.execute(startsWith("LOCK TABLE injectors_contracts")))
          .thenThrow(new SQLException("deadlock detected", "40P01"))
          .thenReturn(false);

      // Act
      run();

      // Assert
      assertThat(pauses).containsExactly(100L);
      InOrder order = inOrder(connection, statement, restoreLockTimeout);
      order.verify(connection).setSavepoint();
      order.verify(statement).execute("SET LOCAL lock_timeout = '500ms'");
      order.verify(statement).execute("LOCK TABLE payloads IN ACCESS EXCLUSIVE MODE NOWAIT");
      order
          .verify(statement)
          .execute("LOCK TABLE injectors_contracts IN ACCESS EXCLUSIVE MODE NOWAIT");
      order.verify(connection).rollback(savepoint);
      order.verify(connection).setSavepoint();
      order.verify(statement).execute("LOCK TABLE payloads IN ACCESS EXCLUSIVE MODE NOWAIT");
      order
          .verify(statement)
          .execute("LOCK TABLE injectors_contracts IN ACCESS EXCLUSIVE MODE NOWAIT");
      order.verify(statement).execute("ALTER TABLE payloads");
      order.verify(restoreLockTimeout).setString(1, CALLER_LOCK_TIMEOUT);
      order.verify(restoreLockTimeout).execute();
      order.verify(connection).releaseSavepoint(savepoint);
      verify(connection, never()).commit();
      verify(connection, never()).rollback();
      verify(connection, never()).setAutoCommit(anyBoolean());
    }

    @Test
    @DisplayName("Given a lock held on every attempt, should back off then fail with the holders")
    void given_lockHeldOnEveryAttempt_should_backOffThenFailNamingTheHolders() throws Exception {
      // Arrange
      SQLException conflict = new SQLException("lock not available", "55P03");
      when(statement.execute(startsWith("LOCK TABLE payloads"))).thenThrow(conflict);
      when(holders.next()).thenReturn(true, false);
      when(holders.getString(1)).thenReturn("payloads");
      when(holders.getString(2)).thenReturn("AccessShareLock");
      when(holders.getInt(3)).thenReturn(4242);
      when(holders.getString(4)).thenReturn("openaev");
      when(holders.getString(5)).thenReturn("idle in transaction");
      when(holders.getTimestamp(6)).thenReturn(Timestamp.valueOf("2026-11-25 10:00:00"));

      // Act
      SQLException thrown =
          catchThrowableOfType(SQLException.class, MigrationTableLocksTest.this::run);

      // Assert
      assertThat(thrown.getSQLState()).isEqualTo("55P03");
      assertThat(thrown.getCause()).isSameAs(conflict);
      assertThat(thrown.getMessage())
          .startsWith("step: gave up after 4 attempts to lock payloads, injectors_contracts")
          .contains("earlier version", "stop it")
          .contains("pid 4242 (application 'openaev', idle in transaction since 2026-11-25")
          .contains("AccessShareLock on payloads");
      verify(lockHolders).setString(1, "payloads,injectors_contracts");
      assertThat(pauses).containsExactly(100L, 200L, 250L);
      verify(connection, times(4)).rollback(savepoint);
      verify(connection, never()).releaseSavepoint(any());
      verify(connection, never()).commit();
    }

    @Test
    @DisplayName("Given the holders cannot be read, should report the lock conflict")
    void given_holdersCannotBeRead_should_reportTheLockConflict() throws Exception {
      // Arrange
      when(statement.execute(startsWith("LOCK TABLE payloads")))
          .thenThrow(new SQLException("lock not available", "55P03"));
      when(lockHolders.executeQuery()).thenThrow(new SQLException("permission denied", "42501"));

      // Act
      SQLException thrown =
          catchThrowableOfType(SQLException.class, MigrationTableLocksTest.this::run);

      // Assert
      assertThat(thrown.getSQLState()).isEqualTo("55P03");
      assertThat(thrown.getMessage()).contains("gave up after 4 attempts", "unknown (");
    }
  }

  @Nested
  @DisplayName("Failures of the step")
  class Failures {

    @Test
    @DisplayName("Given an SQL error other than a lock conflict, should roll back, no retry")
    void given_otherSqlError_should_rollBackAndRethrowWithNoRetry() throws Exception {
      // Arrange
      SQLException duplicate = new SQLException("duplicate", "23505");
      when(statement.execute("ALTER TABLE payloads")).thenThrow(duplicate);

      // Act and Assert
      assertThatThrownBy(MigrationTableLocksTest.this::run).isSameAs(duplicate);
      assertThat(pauses).isEmpty();
      verify(connection).rollback(savepoint);
      verify(connection, never()).releaseSavepoint(any());
    }

    @Test
    @DisplayName("Given an SQL error with no SQLState, should roll back and rethrow it, no retry")
    void given_sqlErrorWithoutSqlState_should_rollBackAndRethrowWithNoRetry() throws Exception {
      // Arrange
      SQLException noState = new SQLException("connection reset");
      when(statement.execute("ALTER TABLE payloads")).thenThrow(noState);

      // Act and Assert
      assertThatThrownBy(MigrationTableLocksTest.this::run).isSameAs(noState);
      assertThat(pauses).isEmpty();
      verify(connection).rollback(savepoint);
      verify(connection, never()).releaseSavepoint(any());
    }

    @Test
    @DisplayName("Given a runtime exception from the work, should roll back to the savepoint")
    void given_runtimeExceptionFromTheWork_should_rollBackAndRethrow() throws Exception {
      // Arrange
      IllegalStateException failure = new IllegalStateException("work failed");

      // Act and Assert
      assertThatThrownBy(
              () ->
                  run(
                      s -> {
                        throw failure;
                      },
                      pauses::add))
          .isSameAs(failure);
      verify(connection).rollback(savepoint);
      verify(connection, never()).releaseSavepoint(any());
    }

    @Test
    @DisplayName("Given an error from the work, should roll back to the savepoint")
    void given_errorFromTheWork_should_rollBackAndRethrow() throws Exception {
      // Arrange
      Error failure = new AssertionError("work failed");

      // Act and Assert
      assertThatThrownBy(
              () ->
                  run(
                      s -> {
                        throw failure;
                      },
                      pauses::add))
          .isSameAs(failure);
      verify(connection).rollback(savepoint);
      verify(connection, never()).releaseSavepoint(any());
    }

    @Test
    @DisplayName(
        "Given the rollback fails too, should rethrow the failure, the rollback suppressed")
    void given_rollbackFails_should_rethrowWithTheRollbackFailureSuppressed() throws Exception {
      // Arrange
      SQLException conflict = new SQLException("lock not available", "55P03");
      SQLException rollbackFailure = new SQLException("connection lost", "08006");
      when(statement.execute(startsWith("LOCK TABLE payloads"))).thenThrow(conflict);
      doThrow(rollbackFailure).when(connection).rollback(savepoint);

      // Act and Assert
      assertThatThrownBy(MigrationTableLocksTest.this::run).isSameAs(conflict);
      assertThat(conflict.getSuppressed()).containsExactly(rollbackFailure);
      assertThat(pauses).isEmpty();
    }

    @Test
    @DisplayName("Given the pause is interrupted, should stop with the interrupt flag set")
    void given_pauseInterrupted_should_stopAndRestoreTheInterruptFlag() throws Exception {
      // Arrange
      SQLException conflict = new SQLException("lock not available", "55P03");
      when(statement.execute(startsWith("LOCK TABLE payloads"))).thenThrow(conflict);
      InterruptedException interrupted = new InterruptedException();

      // Act and Assert
      assertThatThrownBy(
              () ->
                  run(
                      s -> s.execute("ALTER TABLE payloads"),
                      millis -> {
                        throw interrupted;
                      }))
          .isSameAs(conflict);
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
      assertThat(conflict.getSuppressed()).containsExactly(interrupted);
      verify(connection, times(1)).setSavepoint();
    }
  }

  @Nested
  @DisplayName("Refusals")
  class Refusals {

    @Test
    @DisplayName("Given a connection in autocommit, should refuse to run outside a transaction")
    void given_connectionInAutocommit_should_refuse() throws Exception {
      // Arrange
      when(connection.getAutoCommit()).thenReturn(true);

      // Act and Assert
      assertThatThrownBy(MigrationTableLocksTest.this::run)
          .isInstanceOf(IllegalStateException.class);
      verify(statement, never()).execute(anyString());
      verify(connection, never()).setSavepoint();
    }

    @Test
    @DisplayName("Given a table name that is not one, should refuse before any statement")
    void given_notATableName_should_refuse() throws Exception {
      // Act and Assert
      assertThatThrownBy(
              () ->
                  MigrationTableLocks.runLocked(
                      connection,
                      "step",
                      List.of("payloads; DROP TABLE users"),
                      s -> {},
                      RETRY,
                      pauses::add))
          .isInstanceOf(IllegalArgumentException.class);
      verify(connection, never()).setSavepoint();
    }
  }
}
