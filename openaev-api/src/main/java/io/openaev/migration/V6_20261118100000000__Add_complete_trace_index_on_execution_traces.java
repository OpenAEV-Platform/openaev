package io.openaev.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Indexes the COMPLETE traces of each inject status, with the two columns the status a reader sees
 * is computed from ({@code InjectVisibleStatusService}): every inject list reads them for its
 * closed injects. A COMPLETE trace is about one trace in six, so the index is read one row per
 * agent instead of every trace of the inject, and the planner's estimate stays under the JIT
 * thresholds (measurement: {@code brainstorming/marking/task4/tech-design.md}, "Measured (step
 * 11.11)").
 *
 * <p>{@code execution_traces} is written constantly, so the index is built {@code CONCURRENTLY}:
 * inserts go on during the build. That cannot run in a transaction, hence {@link
 * #canExecuteInTransaction()}. An interrupted concurrent build leaves an invalid index, which
 * {@code IF NOT EXISTS} would keep: it is dropped and built again.
 */
@Component
public class V6_20261118100000000__Add_complete_trace_index_on_execution_traces
    extends BaseJavaMigration {

  public static final String INDEX = "idx_execution_traces_complete_inject_status";

  @Override
  public boolean canExecuteInTransaction() {
    return false;
  }

  @Override
  public void migrate(Context context) throws Exception {
    migrate(context.getConnection());
  }

  /** Puts {@code connection} in autocommit mode: a concurrent build refuses a transaction. */
  void migrate(Connection connection) throws SQLException {
    connection.setAutoCommit(true);
    try (Statement statement = connection.createStatement()) {
      if (isInvalid(connection)) {
        statement.execute("DROP INDEX CONCURRENTLY IF EXISTS " + INDEX);
      }
      statement.execute(
          "CREATE INDEX CONCURRENTLY IF NOT EXISTS "
              + INDEX
              + " ON execution_traces (execution_inject_status_id)"
              + " INCLUDE (execution_status, execution_agent_id)"
              + " WHERE execution_action = 'COMPLETE'");
    }
  }

  private static boolean isInvalid(Connection connection) throws SQLException {
    try (PreparedStatement query =
        connection.prepareStatement(
            "SELECT NOT i.indisvalid FROM pg_index i"
                + " JOIN pg_class c ON c.oid = i.indexrelid WHERE c.relname = ?")) {
      query.setString(1, INDEX);
      try (ResultSet row = query.executeQuery()) {
        return row.next() && row.getBoolean(1);
      }
    }
  }
}
