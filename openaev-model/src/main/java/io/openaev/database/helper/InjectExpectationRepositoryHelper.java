package io.openaev.database.helper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

/**
 * Repository helper for low-level database operations on inject expectations.
 *
 * <p>This helper provides optimized JDBC-based operations for updating inject expectation
 * signatures, which are stored as JSONB arrays in PostgreSQL. Direct SQL is used for atomic append
 * operations that would be complex or inefficient through JPA.
 *
 * @see io.openaev.database.model.InjectExpectation
 * @see io.openaev.database.model.InjectExpectationSignature
 */
@Repository
public class InjectExpectationRepositoryHelper {

  @Autowired private DataSource dataSource;

  /**
   * Appends a new signature entry to an inject expectation's signature array.
   *
   * <p>This method atomically appends a type/value tuple to the JSONB signature array for a
   * specific inject and agent combination. The operation is performed using PostgreSQL's native
   * JSONB concatenation for optimal performance.
   *
   * @param injectId the ID of the inject
   * @param agentId the ID of the agent
   * @param type the signature type (e.g., "process_name", "command_line", "file_hash")
   * @param value the signature value
   * @throws RuntimeException if the database update fails
   */
  public void insertSignatureForAgentAndInject(
      String injectId, String agentId, String type, String value) {
    try (Connection conn = dataSource.getConnection()) {

      try (PreparedStatement ps =
          conn.prepareStatement(
              """
                UPDATE injects_expectations
                SET inject_expectation_signatures =
                    COALESCE(inject_expectation_signatures, '[]'::jsonb) ||
                    jsonb_build_array(jsonb_build_object('type', ?, 'value', ?))
                WHERE inject_id = ? AND agent_id = ?
                """)) {

        ps.setString(1, type);
        ps.setString(2, value);
        ps.setString(3, injectId);
        ps.setString(4, agentId);

        ps.executeUpdate();
      }
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }
}
