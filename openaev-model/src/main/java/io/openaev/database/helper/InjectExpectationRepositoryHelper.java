package io.openaev.database.helper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class InjectExpectationRepositoryHelper {

  @Autowired private DataSource dataSource;

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
