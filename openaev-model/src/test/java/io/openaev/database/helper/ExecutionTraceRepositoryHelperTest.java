package io.openaev.database.helper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.*;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExecutionTraceRepositoryHelper")
class ExecutionTraceRepositoryHelperTest {

  @Mock private JdbcTemplate jdbcTemplate;

  private ExecutionTraceRepositoryHelper helper;

  @BeforeEach
  void setUp() {
    helper = new ExecutionTraceRepositoryHelper(jdbcTemplate);
  }

  @Nested
  @DisplayName("saveExecutionTrace")
  class SaveExecutionTrace {

    @Test
    @DisplayName("should insert trace with all fields populated via JdbcTemplate")
    @SuppressWarnings("unchecked")
    void shouldInsertTraceWithAllFields() {
      // Given
      InjectStatus injectStatus = new InjectStatus();
      injectStatus.setId("status-123");

      Agent agent = new Agent();
      agent.setId("agent-456");

      ObjectNode structuredOutput = JsonNodeFactory.instance.objectNode();
      structuredOutput.put("key", "value");

      Instant now = Instant.now();
      ExecutionTrace trace = new ExecutionTrace();
      trace.setInjectStatus(injectStatus);
      trace.setInjectTestStatus(null);
      trace.setAgent(agent);
      trace.setMessage("Execution completed");
      trace.setStructuredOutput(structuredOutput);
      trace.setAction(ExecutionTraceAction.COMPLETE);
      trace.setStatus(ExecutionTraceStatus.SUCCESS);
      trace.setTime(now);
      trace.setIdentifiers(List.of("id1", "id2"));
      trace.setCreationDate(now);
      trace.setUpdateDate(now);

      when(jdbcTemplate.execute(anyString(), any(PreparedStatementCallback.class))).thenReturn(1);

      // When
      String id = helper.saveExecutionTrace(trace);

      // Then
      assertNotNull(id, "Generated ID should not be null");
      assertFalse(id.isBlank(), "Generated ID should not be blank");

      // Verify JdbcTemplate.execute was called (not raw DataSource.getConnection)
      verify(jdbcTemplate).execute(contains("INSERT INTO execution_traces"), any(PreparedStatementCallback.class));
    }

    @Test
    @DisplayName("should handle null agent, null test status, and null structured output")
    @SuppressWarnings("unchecked")
    void shouldHandleNullOptionalFields() {
      // Given
      InjectStatus injectStatus = new InjectStatus();
      injectStatus.setId("status-789");

      Instant now = Instant.now();
      ExecutionTrace trace = new ExecutionTrace();
      trace.setInjectStatus(injectStatus);
      trace.setInjectTestStatus(null); // null test status
      trace.setAgent(null); // null agent
      trace.setMessage("No agent execution");
      trace.setStructuredOutput(null); // null structured output
      trace.setAction(ExecutionTraceAction.START);
      trace.setStatus(ExecutionTraceStatus.INFO);
      trace.setTime(now);
      trace.setIdentifiers(List.of());
      trace.setCreationDate(now);
      trace.setUpdateDate(now);

      when(jdbcTemplate.execute(anyString(), any(PreparedStatementCallback.class))).thenReturn(1);

      // When
      String id = helper.saveExecutionTrace(trace);

      // Then
      assertNotNull(id);
      verify(jdbcTemplate).execute(contains("INSERT INTO execution_traces"), any(PreparedStatementCallback.class));
    }

    @Test
    @DisplayName("should propagate DataAccessException from JdbcTemplate")
    @SuppressWarnings("unchecked")
    void shouldPropagateDataAccessException() {
      // Given
      InjectStatus injectStatus = new InjectStatus();
      injectStatus.setId("status-err");

      Instant now = Instant.now();
      ExecutionTrace trace = new ExecutionTrace();
      trace.setInjectStatus(injectStatus);
      trace.setInjectTestStatus(null);
      trace.setAgent(null);
      trace.setMessage("Will fail");
      trace.setStructuredOutput(null);
      trace.setAction(ExecutionTraceAction.COMPLETE);
      trace.setStatus(ExecutionTraceStatus.ERROR);
      trace.setTime(now);
      trace.setIdentifiers(List.of());
      trace.setCreationDate(now);
      trace.setUpdateDate(now);

      when(jdbcTemplate.execute(anyString(), any(PreparedStatementCallback.class)))
          .thenThrow(
              new org.springframework.dao.DataAccessResourceFailureException("Connection lost"));

      // When / Then
      assertThrows(
          org.springframework.dao.DataAccessException.class,
          () -> helper.saveExecutionTrace(trace));
    }
  }

  @Nested
  @DisplayName("updateInjectStatus")
  class UpdateInjectStatus {

    @Test
    @DisplayName("should update status with end date via JdbcTemplate")
    void shouldUpdateStatusWithEndDate() {
      // Given
      Instant endDate = Instant.now();

      // When
      helper.updateInjectStatus("status-123", "SUCCESS", endDate);

      // Then
      ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
      verify(jdbcTemplate)
          .update(contains("UPDATE injects_statuses"), argsCaptor.capture());

      Object[] args = argsCaptor.getValue();
      assertEquals("SUCCESS", args[0]);
      assertEquals(Timestamp.from(endDate), args[1]);
      assertEquals("status-123", args[2]);
    }

    @Test
    @DisplayName("should handle null end date (inject still executing)")
    void shouldHandleNullEndDate() {
      // When
      helper.updateInjectStatus("status-456", "PENDING", null);

      // Then
      ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
      verify(jdbcTemplate)
          .update(contains("UPDATE injects_statuses"), argsCaptor.capture());

      Object[] args = argsCaptor.getValue();
      assertEquals("PENDING", args[0]);
      assertNull(args[1], "End date should be null for non-completed inject");
      assertEquals("status-456", args[2]);
    }
  }

  @Nested
  @DisplayName("updateInjectUpdateDate")
  class UpdateInjectUpdateDate {

    @Test
    @DisplayName("should update inject timestamp via JdbcTemplate")
    void shouldUpdateInjectTimestamp() {
      // Given
      Instant updatedAt = Instant.now();

      // When
      helper.updateInjectUpdateDate("inject-123", updatedAt);

      // Then
      ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
      verify(jdbcTemplate).update(contains("UPDATE injects"), argsCaptor.capture());

      Object[] args = argsCaptor.getValue();
      assertEquals(Timestamp.from(updatedAt), args[0]);
      assertEquals("inject-123", args[1]);
    }

    @Test
    @DisplayName("should handle null timestamp")
    void shouldHandleNullTimestamp() {
      // When
      helper.updateInjectUpdateDate("inject-456", null);

      // Then
      ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
      verify(jdbcTemplate).update(contains("UPDATE injects"), argsCaptor.capture());

      Object[] args = argsCaptor.getValue();
      assertNull(args[0], "Timestamp should be null");
      assertEquals("inject-456", args[1]);
    }
  }
}
