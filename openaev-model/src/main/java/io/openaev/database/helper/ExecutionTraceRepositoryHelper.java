package io.openaev.database.helper;

import io.openaev.annotation.AllowRawJdbc;
import io.openaev.database.model.ExecutionTrace;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Repository helper for low-level database operations on execution traces and inject statuses.
 *
 * <p>This helper provides optimized JDBC-based operations for performance-critical database
 * updates, bypassing JPA overhead when direct SQL execution is more efficient. It is particularly
 * useful for high-volume operations during inject execution tracking.
 *
 * <p>Operations include:
 *
 * <ul>
 *   <li>Inserting new execution traces
 *   <li>Updating inject status states
 *   <li>Updating inject timestamps
 * </ul>
 *
 * @see ExecutionTrace
 */
@AllowRawJdbc(reason = "writes execution_traces and injects_statuses, both non-tenant tables")
@Repository
@RequiredArgsConstructor
public class ExecutionTraceRepositoryHelper {

  private final JdbcTemplate jdbcTemplate;

  /** SQL statement for inserting a new execution trace record. */
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
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";

  /**
   * Saves an execution trace using a direct JDBC call for improved performance.
   *
   * <p>This method bypasses JPA to directly insert the execution trace record, which is more
   * efficient for high-volume insert operations during inject execution.
   *
   * @param executionTrace the execution trace to save
   * @return the generated UUID of the newly created trace
   * @throws org.springframework.dao.DataAccessException if the database insert fails
   */
  public String saveExecutionTrace(ExecutionTrace executionTrace) {
    String injectStatusId =
        executionTrace.getInjectStatus() != null ? executionTrace.getInjectStatus().getId() : null;
    String injectTestStatusId =
        executionTrace.getInjectTestStatus() != null
            ? executionTrace.getInjectTestStatus().getId()
            : null;
    String agentId = executionTrace.getAgent() != null ? executionTrace.getAgent().getId() : null;
    String structuredOutputAsText =
        executionTrace.getStructuredOutput() != null
            ? executionTrace.getStructuredOutput().asText()
            : null;
    String id = UUID.randomUUID().toString();

    jdbcTemplate.execute(
        INSERT_EXECUTION_TRACE,
        (PreparedStatement ps) -> {
          ps.setString(1, id);
          ps.setString(2, injectStatusId);
          ps.setString(3, injectTestStatusId);
          ps.setString(4, agentId);
          ps.setString(5, executionTrace.getMessage());
          ps.setString(6, structuredOutputAsText);
          ps.setString(7, executionTrace.getAction().name());
          ps.setString(8, executionTrace.getStatus().name());
          ps.setObject(9, OffsetDateTime.ofInstant(executionTrace.getTime(), ZoneOffset.UTC));
          ps.setArray(
              10,
              ps.getConnection().createArrayOf("text", executionTrace.getIdentifiers().toArray()));
          ps.setObject(
              11, OffsetDateTime.ofInstant(executionTrace.getCreationDate(), ZoneOffset.UTC));
          ps.setObject(
              12, OffsetDateTime.ofInstant(executionTrace.getUpdateDate(), ZoneOffset.UTC));
          return ps.executeUpdate();
        });

    return id;
  }

  /**
   * Updates an inject status with a new status name and end date using direct JDBC.
   *
   * <p>This method is used to efficiently update the status of an inject execution without loading
   * the full entity through JPA.
   *
   * @param injectStatusId the ID of the inject status to update
   * @param name the new status name (e.g., "SUCCESS", "ERROR", "PENDING")
   * @param endDate the end timestamp for the inject execution, or {@code null} if not yet completed
   * @throws org.springframework.dao.DataAccessException if the database update fails
   */
  public void updateInjectStatus(String injectStatusId, String name, Instant endDate) {
    String sql =
        "UPDATE injects_statuses SET status_name = ?, tracking_end_date = ? WHERE status_id = ?";

    jdbcTemplate.update(
        sql,
        name,
        endDate != null ? OffsetDateTime.ofInstant(endDate, ZoneOffset.UTC) : null,
        injectStatusId);
  }
}
