package io.openaev.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.migration.V6_20261117160000000__Convert_findings_to_identity.LockPolicy;
import io.openaev.migration.V6_20261117160000000__Convert_findings_to_identity.TableLock;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Migration V6_20261117160000000: lock retry of the catalog steps")
class ConvertFindingsToIdentityLockRetryTest {

  private static final Duration FIRST = Duration.ofSeconds(3);
  private static final LockPolicy THREE_QUICK_ATTEMPTS =
      new LockPolicy(Duration.ofMillis(10), 3, Duration.ofMillis(1));
  private static final List<TableLock> FINDINGS =
      List.of(new TableLock("findings", "ACCESS EXCLUSIVE"));

  private final V6_20261117160000000__Convert_findings_to_identity migration =
      new V6_20261117160000000__Convert_findings_to_identity();

  @Mock private Connection connection;
  @Mock private Statement statement;

  @Nested
  @DisplayName("Pause between attempts")
  class Pause {

    @Test
    @DisplayName("The ceiling of the pause doubles from the first pause up to 30 seconds")
    void given_successiveFailedAttempts_should_doubleTheCeilingUpToTheCap() {
      // Act
      List<Long> ceilings =
          List.of(1, 2, 3, 4, 5, 6).stream()
              .map(attempt -> pauseAfter(attempt, 1.0).toSeconds())
              .toList();

      // Assert
      assertThat(ceilings).containsExactly(3L, 6L, 12L, 24L, 30L, 30L);
      assertThat(pauseAfter(1_000, 1.0)).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    @DisplayName(
        "The jitter draws the pause in the upper half of its ceiling, never under the first")
    void given_anyRandomDraw_should_stayInTheUpperHalfOfTheCeilingAndAboveTheFirstPause() {
      for (int attempt = 1; attempt <= 8; attempt++) {
        long ceiling = pauseAfter(attempt, 1.0).toMillis();
        for (double random = 0.0; random <= 1.0; random += 0.125) {
          // Act
          long pause = pauseAfter(attempt, random).toMillis();

          // Assert
          assertThat(pause)
              .isBetween(Math.max(FIRST.toMillis(), ceiling / 2), ceiling)
              .isLessThanOrEqualTo(30_000);
        }
      }
      assertThat(pauseAfter(4, 0.0)).isEqualTo(Duration.ofSeconds(12));
      assertThat(pauseAfter(4, 0.5)).isEqualTo(Duration.ofSeconds(18));
    }

    private Duration pauseAfter(int failedAttempt, double random) {
      return V6_20261117160000000__Convert_findings_to_identity.pauseAfter(
          failedAttempt, FIRST, random);
    }
  }

  @Nested
  @DisplayName("Lock errors")
  class LockErrors {

    @Test
    @DisplayName("A deadlock rolls the attempt back and runs the step again")
    void given_deadlockOnFirstAttempt_should_rollBackAndRunAgain() throws Exception {
      // Arrange
      when(connection.createStatement()).thenReturn(statement);
      AtomicInteger lockRequests = new AtomicInteger();
      when(statement.execute(anyString()))
          .thenAnswer(
              invocation -> {
                String sql = invocation.getArgument(0);
                if (sql.startsWith("LOCK TABLE") && lockRequests.incrementAndGet() == 1) {
                  throw new SQLException("deadlock detected", "40P01");
                }
                return false;
              });

      // Act
      migration.commitUnderLocks(connection, THREE_QUICK_ATTEMPTS, FINDINGS, "SELECT 1");

      // Assert
      verify(connection, times(1)).rollback();
      verify(connection, times(1)).commit();
      verify(statement, times(1)).execute("SELECT 1");
    }

    @Test
    @DisplayName("A rollback that fails surfaces the lock error, with the rollback error in it")
    void given_rollbackFailing_should_throwTheLockErrorWithTheRollbackErrorSuppressed()
        throws Exception {
      // Arrange
      when(connection.createStatement()).thenReturn(statement);
      SQLException lockError = new SQLException("lock not available", "55P03");
      SQLException rollbackError = new SQLException("connection lost", "08006");
      when(statement.execute(anyString()))
          .thenAnswer(
              invocation -> {
                if (((String) invocation.getArgument(0)).startsWith("LOCK TABLE")) {
                  throw lockError;
                }
                return false;
              });
      doThrow(rollbackError).when(connection).rollback();

      // Act / Assert
      assertThatThrownBy(
              () ->
                  migration.commitUnderLocks(
                      connection, THREE_QUICK_ATTEMPTS, FINDINGS, "SELECT 1"))
          .isSameAs(lockError)
          .satisfies(thrown -> assertThat(thrown.getSuppressed()).containsExactly(rollbackError));
      verify(connection, times(0)).commit();
    }
  }
}
