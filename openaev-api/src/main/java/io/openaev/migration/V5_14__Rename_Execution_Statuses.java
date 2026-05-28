package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V5_14__Rename_Execution_Statuses extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
              UPDATE injects_statuses
              SET status_name = 'EXECUTED'
              WHERE status_name = 'SUCCESS'
              """);

      statement.execute(
          """
              UPDATE injects_tests_statuses
              SET status_name = 'EXECUTED'
              WHERE status_name = 'SUCCESS'
              """);

      statement.execute(
          """
              UPDATE execution_traces
              SET execution_status = 'EXECUTED'
              WHERE execution_status = 'SUCCESS';
              """);
    }
  }
}
