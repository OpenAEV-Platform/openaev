package io.openaev.utils;

import static io.openaev.utils.MigrationTableLocks.LockMode.ACCESS_EXCLUSIVE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.utils.MigrationTableLocks.Retry;
import io.openaev.utils.MigrationTableLocks.TableLock;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

@DisplayName("Migration table locks")
class MigrationTableLocksTest {

  private static final Retry RETRY = new Retry(4, 100, 250);
  private static final List<TableLock> LOCKS =
      List.of(
          new TableLock("payloads", ACCESS_EXCLUSIVE),
          new TableLock("injectors_contracts", ACCESS_EXCLUSIVE));

  private final List<Long> pauses = new ArrayList<>();
  private Connection connection;
  private Statement statement;

  @BeforeEach
  void setUp() throws SQLException {
    connection = mock(Connection.class);
    statement = mock(Statement.class);
    when(connection.getAutoCommit()).thenReturn(true);
    when(connection.createStatement()).thenReturn(statement);
  }

  private void run() throws Exception {
    MigrationTableLocks.runLocked(
        connection, "step", LOCKS, s -> s.execute("ALTER TABLE payloads"), RETRY, pauses::add);
  }

  @Nested
  @DisplayName("Retry on a lock held elsewhere")
  class Retrying {

    @Test
    @DisplayName("Given a lock held on every attempt, should back off and give up at the bound")
    void given_lockHeldOnEveryAttempt_should_backOffThenGiveUpAtTheBound() throws Exception {
      // Arrange
      when(statement.execute(startsWith("LOCK TABLE payloads")))
          .thenThrow(new SQLException("lock not available", "55P03"));

      // Act and Assert
      assertThatThrownBy(MigrationTableLocksTest.this::run)
          .isInstanceOf(SQLException.class)
          .extracting(e -> ((SQLException) e).getSQLState())
          .isEqualTo("55P03");
      assertThat(pauses).containsExactly(100L, 200L, 250L);
      verify(connection, times(4)).rollback();
      verify(connection, never()).commit();
      verify(connection).setAutoCommit(true);
    }

    @Test
    @DisplayName("Given a deadlock then free tables, should lock in order without waiting, commit")
    void given_deadlockThenFreeTables_should_lockInOrderWithoutWaitingAndCommit() throws Exception {
      // Arrange
      when(statement.execute(startsWith("LOCK TABLE injectors_contracts")))
          .thenThrow(new SQLException("deadlock detected", "40P01"))
          .thenReturn(false);

      // Act
      run();

      // Assert
      assertThat(pauses).containsExactly(100L);
      InOrder order = inOrder(connection, statement);
      order.verify(connection).setAutoCommit(false);
      order.verify(statement).execute("SET LOCAL lock_timeout = '500ms'");
      order.verify(statement).execute("LOCK TABLE payloads IN ACCESS EXCLUSIVE MODE NOWAIT");
      order
          .verify(statement)
          .execute("LOCK TABLE injectors_contracts IN ACCESS EXCLUSIVE MODE NOWAIT");
      order.verify(connection).rollback();
      order.verify(statement).execute("LOCK TABLE payloads IN ACCESS EXCLUSIVE MODE NOWAIT");
      order
          .verify(statement)
          .execute("LOCK TABLE injectors_contracts IN ACCESS EXCLUSIVE MODE NOWAIT");
      order.verify(statement).execute("ALTER TABLE payloads");
      order.verify(connection).commit();
      order.verify(connection).setAutoCommit(true);
    }
  }

  @Nested
  @DisplayName("Refusals")
  class Refusals {

    @Test
    @DisplayName("Given an error other than a lock conflict, should rethrow it with no retry")
    void given_otherError_should_rethrowWithNoRetry() throws Exception {
      // Arrange
      when(statement.execute("ALTER TABLE payloads"))
          .thenThrow(new SQLException("duplicate", "23505"));

      // Act and Assert
      assertThatThrownBy(MigrationTableLocksTest.this::run).isInstanceOf(SQLException.class);
      assertThat(pauses).isEmpty();
      verify(connection).rollback();
    }

    @Test
    @DisplayName("Given a connection inside a transaction, should refuse to commit it")
    void given_connectionInsideATransaction_should_refuse() throws Exception {
      // Arrange
      when(connection.getAutoCommit()).thenReturn(false);

      // Act and Assert
      assertThatThrownBy(MigrationTableLocksTest.this::run)
          .isInstanceOf(IllegalStateException.class);
      verify(statement, never()).execute(anyString());
      verify(connection, never()).commit();
    }
  }
}
