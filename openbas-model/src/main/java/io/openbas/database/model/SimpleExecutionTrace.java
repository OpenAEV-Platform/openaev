package io.openbas.database.model;

import static java.time.Instant.now;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Data
@NoArgsConstructor
public class SimpleExecutionTrace implements Base {

  private String id;
  private String injectStatusId;
  private String injectTestStatusId;
  private String agentId;
  private String message;
  private ObjectNode structuredOutput;
  private ExecutionTraceAction action;
  private ExecutionTraceStatus status;
  private Instant time;
  private String[] identifiers;
  private Instant creationDate = now();
  private Instant updateDate = now();

  public SimpleExecutionTrace(
      String injectStatusId,
      ExecutionTraceStatus status,
      List<String> identifiers,
      String message,
      ExecutionTraceAction action,
      String agentId,
      Instant time) {
    this.injectStatusId = injectStatusId;
    this.status = status;
    this.identifiers = identifiers == null ? new String[0] : identifiers.toArray(new String[0]);
    this.message = message;
    this.time = time == null ? now() : time;
    this.action = action;
    this.agentId = agentId;
  }

  public SimpleExecutionTrace(ExecutionTrace executionTrace) {
    this.id = executionTrace.getId();
    this.injectStatusId = executionTrace.getInjectStatus().getId();
    this.injectTestStatusId = executionTrace.getInjectTestStatus().getId();
    this.agentId = executionTrace.getAgent().getId();
    this.message = executionTrace.getMessage();
    this.structuredOutput = executionTrace.getStructuredOutput();
    this.action = executionTrace.getAction();
    this.status = executionTrace.getStatus();
    this.time = executionTrace.getTime();
    this.creationDate = executionTrace.getCreationDate();
    this.updateDate = executionTrace.getUpdateDate();
    this.identifiers = executionTrace.getIdentifiers().toArray(new String[0]);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  @Component
  public static class SimpleExecutionTraceRowMapper implements RowMapper<SimpleExecutionTrace> {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public SimpleExecutionTrace mapRow(ResultSet rs, int rowNum) throws SQLException {
      SimpleExecutionTrace simpleExecutionTrace = new SimpleExecutionTrace();
      simpleExecutionTrace.setId(rs.getString("execution_trace_id"));
      simpleExecutionTrace.setInjectStatusId(rs.getString("execution_inject_status_id"));
      simpleExecutionTrace.setInjectTestStatusId(rs.getString("execution_inject_test_status_id"));
      simpleExecutionTrace.setAgentId(rs.getString("execution_agent_id"));
      simpleExecutionTrace.setMessage(rs.getString("execution_message"));
      simpleExecutionTrace.setAction(
          ExecutionTraceAction.valueOf(rs.getString("execution_action")));
      simpleExecutionTrace.setStatus(
          ExecutionTraceStatus.valueOf(rs.getString("execution_status")));
      simpleExecutionTrace.setTime(rs.getTimestamp("execution_time").toInstant());
      simpleExecutionTrace.setCreationDate(rs.getTimestamp("execution_created_at").toInstant());
      simpleExecutionTrace.setUpdateDate(rs.getTimestamp("execution_updated_at").toInstant());
      simpleExecutionTrace.setIdentifiers(
          (String[]) rs.getArray("execution_context_identifiers").getArray());
      try {
        if (rs.getString("execution_structured_output") != null) {
          simpleExecutionTrace.setStructuredOutput(
              (ObjectNode) mapper.readTree(rs.getString("execution_structured_output")));
        }
      } catch (JsonProcessingException e) {
        throw new RuntimeException(e);
      }

      return simpleExecutionTrace;
    }
  }
}
