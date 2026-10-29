package io.openaev.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Set;
import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Pins that a {@code SQLException} thrown while reading a statement's JDBC warning chain is
 * recorded rather than silently dropped: {@code getWarnings()} is the only bridge from the
 * PostgreSQL trigger to this listener, so a driver problem there is an undetectable gap, not a
 * harmless one.
 */
@DisplayName("Write-attribution listener: a statement whose warnings cannot be read is recorded")
class WriteAttrDetectorListenerInstrumentationTest {

  private final WriteAttrDetectorListener listener =
      new WriteAttrDetectorListener(
          () -> new WriteAttrTableClassifier(Set.of(), Set.of(), Set.of()));

  @BeforeEach
  void setUp() {
    WriteAttrDetectorRecorder.start();
  }

  @AfterEach
  void tearDown() {
    WriteAttrDetectorRecorder.stop();
  }

  @Nested
  @DisplayName("Given getWarnings() throws for the executed statement")
  class WarningsUnreadable {

    @Test
    @DisplayName("should record an instrumentation failure instead of swallowing it")
    void given_getWarningsThrows_should_recordAnInstrumentationFailure() throws SQLException {
      // Arrange
      Statement statement = mock(Statement.class);
      when(statement.getWarnings()).thenThrow(new SQLException("driver disconnected"));
      ExecutionInfo execInfo = mock(ExecutionInfo.class);
      when(execInfo.getStatement()).thenReturn(statement);
      int before = WriteAttrDetectorRecorder.instrumentationFailures().size();

      // Act
      listener.afterQuery(execInfo, List.<QueryInfo>of());

      // Assert
      List<String> failures = WriteAttrDetectorRecorder.instrumentationFailures();
      assertThat(failures.size()).isEqualTo(before + 1);
      assertThat(failures.get(failures.size() - 1)).contains("driver disconnected");
    }
  }
}
