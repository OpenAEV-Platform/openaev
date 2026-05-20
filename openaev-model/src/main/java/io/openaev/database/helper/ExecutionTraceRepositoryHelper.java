package io.openaev.database.helper;

import io.openaev.database.model.ExecutionTrace;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Repository helper for low-level database operations on execution traces and inject statuses.
 *
 * <p>This helper provides optimized JDBC-based operations for performance-critical database
 * updates, bypassing JPA overhead when direct SQL execution is more efficient. It is particularly
 * useful for high-volume operations during inject execution tracking.
 *
 * <p><strong>Important:</strong> This class uses {@link JdbcTemplate} instead of raw {@code
 * DataSource.getConnection()} to ensure all SQL operations participate in the current Spring
 * {@code @Transactional} context. Using raw connections would open independent JDBC connections
 * outside the JPA transaction, causing deadlocks under concurrent access (e.g., multiple agent
 * callbacks in a multi-pod Kubernetes deployment).
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
@Repository
public class ExecutionTraceRepositoryHelper {

  private final JdbcTemplate jdbcTemplate;

  public ExecutionTraceRepositoryHelper(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

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
   * <p>Uses {@link JdbcTemplate} which internally calls {@code DataSourceUtils.getConnection()},
   * ensuring the SQL executes on the same JDBC connection as the enclosing {@code @Transactional}
   * method. This prevents deadlocks that occurred when raw {@code DataSource.getConnection()} was
   * used, which opened a separate connection outside the transaction boundary.
   *
   * @param executionTrace the execution trace to save
   * @return the generated UUID of the newly created trace
   * @throws org.springframework.dao.DataAccessException if the database insert fails
   */
  public String saveExecutionTrace(ExecutionTrace executionTrace) {
    String injectStatusId = null;
    if (executionTrace.getInjectStatus() != null) {
      injectStatusId = executionTrace.getInjectStatus().getId();
    }
    String injectTestStatusId = null;
    if (executionTrace.getInjectTestStatus() != null) {
      injectTestStatusId = executionTrace.getInjectTestStatus().getId();
    }
    String agentId = null;
    if (executionTrace.getAgent() != null) {
      agentId = executionTrace.getAgent().getId();
    }
    String structuredOutputAsText = null;
    if (executionTrace.getStructuredOutput() != null) {
      structuredOutputAsText = executionTrace.getStructuredOutput().asText();
    }
    String id = UUID.randomUUID().toString();

    // JdbcTemplate.execute(PreparedStatementCreator, PreparedStatementCallback) is used here
    // instead of jdbcTemplate.update() because we need access to the raw Connection to call
    // createArrayOf() for the PostgreSQL text[] column (execution_context_identifiers).
    // JdbcTemplate ensures this Connection is the one bound to the current Spring transaction.
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
          ps.setTimestamp(9, Timestamp.from(executionTrace.getTime()));
          ps.setArray(
              10,
              ps.getConnection()
                  .createArrayOf("text", executionTrace.getIdentifiers().toArray()));
          ps.setTimestamp(11, Timestamp.from(executionTrace.getCreationDate()));
          ps.setTimestamp(12, Timestamp.from(executionTrace.getUpdateDate()));
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
        sql, name, endDate != null ? Timestamp.from(endDate) : null, injectStatusId);
  }

  /**
   * Updates the last modification timestamp of an inject using direct JDBC.
   *
   * <p>This lightweight operation updates only the {@code inject_updated_at} column without
   * triggering a full entity update, useful for tracking inject modifications efficiently.
   *
   * @param id the ID of the inject to update
   * @param updatedAt the new update timestamp, or {@code null} to clear the value
   * @throws org.springframework.dao.DataAccessException if the database update fails
   */
  public void updateInjectUpdateDate(String id, Instant updatedAt) {
    String sql = "UPDATE injects SET inject_updated_at = ? WHERE inject_id = ?";

    jdbcTemplate.update(sql, updatedAt != null ? Timestamp.from(updatedAt) : null, id);
  }
}
