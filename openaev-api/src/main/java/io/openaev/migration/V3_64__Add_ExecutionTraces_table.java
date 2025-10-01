package io.openaev.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.*;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_64__Add_ExecutionTraces_table extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement select = connection.createStatement();

    // CREATE ExecutionTraces table
    select.execute(
        """
              CREATE TABLE execution_traces (
                  execution_trace_id VARCHAR(255) NOT NULL CONSTRAINT execution_traces_pkey PRIMARY KEY,
                  execution_inject_status_id VARCHAR(255),
                  execution_inject_test_status_id VARCHAR(255),
                  execution_agent_id VARCHAR(255),
                  execution_message TEXT NOT NULL,
                  execution_action VARCHAR(255),
                  execution_status VARCHAR(255) NOT NULL,
                  execution_time TIMESTAMP,
                  execution_created_at TIMESTAMP DEFAULT now(),
                  execution_updated_at TIMESTAMP DEFAULT now(),
                  execution_context_identifiers text[],
                  FOREIGN KEY (execution_inject_status_id) REFERENCES injects_statuses (status_id) ON DELETE CASCADE,
                  FOREIGN KEY (execution_inject_test_status_id) REFERENCES injects_tests_statuses (status_id) ON DELETE CASCADE,
                  FOREIGN KEY (execution_agent_id) REFERENCES agents (agent_id) ON DELETE CASCADE,
                    CONSTRAINT check_inject_status_or_test_status
                            CHECK (
                execution_inject_status_id IS NOT NULL\s

                        OR execution_inject_test_status_id IS NOT NULL

                    )
                );
              """);

    ResultSet allInjectStatuses =
        select.executeQuery(
            "SELECT status_id, status_executions, 'injects_statuses' AS source_table FROM injects_statuses "
                + "UNION "
                + "SELECT status_id, status_executions, 'injects_tests_statuses' AS source_table FROM injects_tests_statuses");
    PreparedStatement statement =
        context
            .getConnection()
            .prepareStatement(
                """
                            INSERT INTO execution_traces(execution_trace_id, execution_inject_status_id, execution_agent_id, execution_message,
                                               execution_status, execution_time, execution_created_at, execution_context_identifiers, execution_inject_test_status_id)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """);

    ObjectMapper mapper = new ObjectMapper();
    while (allInjectStatuses.next()) {
      String statusId = allInjectStatuses.getString("status_id");
      String sourceTable = allInjectStatuses.getString("source_table");
      String executions = allInjectStatuses.getString("status_executions");

      if (executions != null && !executions.isEmpty()) {
        JsonNode jsonArray = mapper.readTree(executions);
        for (JsonNode execution : jsonArray) {
          statement.setObject(1, UUID.randomUUID());
          statement.setString(2, "injects_statuses".equals(sourceTable) ? statusId : null);
          statement.setString(9, "injects_tests_statuses".equals(sourceTable) ? statusId : null);
          statement.setString(3, null);
          statement.setString(
              4,
              execution.has("execution_message")
                  ? execution.get("execution_message").asText()
                  : null);
          statement.setString(
              5,
              execution.has("execution_status")
                  ? execution.get("execution_status").asText()
                  : null);
          statement.setTimestamp(
              6,
              execution.has("execution_time") && !execution.get("execution_time").isNull()
                  ? Timestamp.valueOf(
                      OffsetDateTime.parse(
                              execution.get("execution_time").asText(),
                              DateTimeFormatter.ISO_DATE_TIME)
                          .toLocalDateTime())
                  : null);
          statement.setTimestamp(
              7,
              execution.has("execution_time") && !execution.get("execution_time").isNull()
                  ? Timestamp.valueOf(
                      OffsetDateTime.parse(
                              execution.get("execution_time").asText(),
                              DateTimeFormatter.ISO_DATE_TIME)
                          .toLocalDateTime())
                  : null);
          statement.setObject(
              8,
              execution.has("execution_context_identifiers")
                      && !execution.get("execution_context_identifiers").isNull()
                  ? execution
                      .get("execution_context_identifiers")
                      .toString()
                      .replace("[", "{")
                      .replace("]", "}")
                  : null,
              java.sql.Types.OTHER);
          statement.addBatch();
        }
      }
    }
    statement.executeBatch();

    // Remove old injectStatuses columns
    Statement removeColumns = context.getConnection().createStatement();
    removeColumns.execute(
        """
            ALTER TABLE injects_statuses DROP COLUMN status_reporting;
            ALTER TABLE injects_statuses DROP COLUMN tracking_total_execution_time;
            ALTER TABLE injects_statuses DROP COLUMN status_executions;
            ALTER TABLE injects_statuses DROP COLUMN tracking_ack_date;
            ALTER TABLE injects_statuses DROP COLUMN tracking_total_count;
            ALTER TABLE injects_statuses DROP COLUMN tracking_total_error;
            ALTER TABLE injects_statuses DROP COLUMN tracking_total_success;

            ALTER TABLE injects_tests_statuses DROP COLUMN tracking_total_execution_time;
            ALTER TABLE injects_tests_statuses DROP COLUMN status_executions;
            ALTER TABLE injects_tests_statuses DROP COLUMN tracking_ack_date;
            ALTER TABLE injects_tests_statuses DROP COLUMN tracking_total_count;
            ALTER TABLE injects_tests_statuses DROP COLUMN tracking_total_error;
            ALTER TABLE injects_tests_statuses DROP COLUMN tracking_total_success;
    """);
  }
}
