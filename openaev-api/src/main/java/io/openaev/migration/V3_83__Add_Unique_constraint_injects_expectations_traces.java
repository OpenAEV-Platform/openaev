package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_83__Add_Unique_constraint_injects_expectations_traces extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement select = connection.createStatement();

    select.execute(
        " DELETE FROM injects_expectations_traces iet WHERE exists ("
            + "  SELECT 1 from injects_expectations_traces iet1"
            + "  WHERE iet.inject_expectation_trace_expectation = iet1.inject_expectation_trace_expectation"
            + "  AND iet.inject_expectation_trace_source_id = iet1.inject_expectation_trace_source_id"
            + "  AND iet.inject_expectation_trace_alert_link = iet1.inject_expectation_trace_alert_link"
            + "  AND iet.inject_expectation_trace_alert_name = iet1.inject_expectation_trace_alert_name"
            + ");");

    select.execute(
        "ALTER TABLE injects_expectations_traces ADD CONSTRAINT unique_injects_expectations_traces_constraint UNIQUE (inject_expectation_trace_expectation, inject_expectation_trace_source_id, inject_expectation_trace_alert_link, inject_expectation_trace_alert_name);");
  }
}
