package io.openaev.database.helper;

import io.openaev.database.model.ExecutionTrace;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class ExecutionTraceRepositoryHelper {

  @Autowired private DataSource dataSource;

  private static final String INSERT_EXECUTION_TRACE =
      """
          INSERT INTO execution_traces (
            execution_trace_id,
            execution_inject_status_id,
            execution_inject_test_status_id,
            execution_agent_id,
            execution_message,
            execution_structured_output,
            execution_action,
            execution_status,
            execution_time,
            execution_context_identifiers,
            execution_created_at,
            execution_updated_at
        ) VALUES (
            ?,
            ?,
            ?,
            ?,
            ?,
            ?,
            ?,
            ?,
            ?,
            ?,
            ?,
            ?
        )""";

  /**
   * Save execution trace with a low level database call
   *
   * @param executionTrace the execution trace
   * @return the id of the new trace
   */
  public String saveExecutionTrace(ExecutionTrace executionTrace) {
    try (Connection conn = dataSource.getConnection()) {

      try (PreparedStatement ps = conn.prepareStatement(INSERT_EXECUTION_TRACE)) {

        String injectStatusId = null;
        if (executionTrace.getInjectStatus() != null) {
          injectStatusId = executionTrace.getInjectStatus().getId();
        }
        String injectTestStatusId = null;
        if (executionTrace.getInjectTestStatus() != null) {
          injectTestStatusId = executionTrace.getInjectTestStatus().getId();
        }
        String structuredOutputAsText = null;
        if (executionTrace.getStructuredOutput() != null) {
          structuredOutputAsText = executionTrace.getStructuredOutput().asText();
        }
        String id = UUID.randomUUID().toString();

        ps.setString(1, id);
        ps.setString(2, injectStatusId);
        ps.setString(3, injectTestStatusId);
        ps.setString(4, executionTrace.getAgent().getId());
        ps.setString(5, executionTrace.getMessage());
        ps.setString(6, structuredOutputAsText);
        ps.setString(7, executionTrace.getAction().name());
        ps.setString(8, executionTrace.getStatus().name());
        ps.setTimestamp(9, Timestamp.from(executionTrace.getTime()));
        ps.setArray(10, conn.createArrayOf("text", executionTrace.getIdentifiers().toArray()));
        ps.setTimestamp(11, Timestamp.from(executionTrace.getCreationDate()));
        ps.setTimestamp(12, Timestamp.from(executionTrace.getUpdateDate()));

        ps.executeUpdate();

        return id;
      }
    } catch (SQLException e) {
      throw new RuntimeException("Failed to insert execution trace", e);
    }
  }

  /**
   * Update an inject status with a new status name and end_date with a low level database call
   *
   * @param injectStatusId the id of the inject status to update
   * @param name the name of the new status
   * @param endDate the end date
   */
  public void updateInjectStatus(String injectStatusId, String name, Instant endDate) {
    String sql =
        "UPDATE injects_statuses SET status_name = ?, tracking_end_date = ? WHERE status_id = ?";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {

      ps.setString(1, name);
      ps.setTimestamp(2, endDate != null ? Timestamp.from(endDate) : null);
      ps.setString(3, injectStatusId);
      ps.executeUpdate();

    } catch (SQLException e) {
      throw new RuntimeException("Failed to update inject status", e);
    }
  }

  /**
   * Update the update date of an injects with a low level database call
   *
   * @param id the id of the inject
   * @param updatedAt the update date
   */
  public void updateInjectUpdateDate(String id, Instant updatedAt) {
    String sql = "UPDATE injects SET inject_updated_at = ? WHERE inject_id = ?";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {

      ps.setTimestamp(1, updatedAt != null ? Timestamp.from(updatedAt) : null);
      ps.setString(2, id);
      ps.executeUpdate();

    } catch (SQLException e) {
      throw new RuntimeException("Failed to update inject update date", e);
    }
  }
}
