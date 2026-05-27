package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V5_12__Rename_Execution_Statuses extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
              UPDATE injects_statuses
              SET status_name = 'EXECUTED'
              WHERE status_name = 'SUCCESS'
                 OR status_name IN (
                   'SUCCESS_WITH_CLEANUP_FAIL',
                   'SUCCESS_WITH_CLEANUP_FAILURE',
                   'EXECUTED_WITH_CLEANUP_FAIL',
                   'EXECUTED_WITH_CLEANUP_FAILURE'
                 );
              """);

      statement.execute(
          """
              UPDATE injects_tests_statuses
              SET status_name = 'EXECUTED'
              WHERE status_name = 'SUCCESS'
                 OR status_name IN (
                   'SUCCESS_WITH_CLEANUP_FAIL',
                   'SUCCESS_WITH_CLEANUP_FAILURE',
                   'EXECUTED_WITH_CLEANUP_FAIL',
                   'EXECUTED_WITH_CLEANUP_FAILURE'
                 );
              """);

      statement.execute(
          """
              UPDATE execution_traces
              SET execution_status = 'EXECUTED'
              WHERE execution_status = 'SUCCESS';
              """);

      statement.execute(
          """
              UPDATE execution_traces
              SET execution_status = 'EXECUTED_WITH_CLEANUP_FAILURE'
              WHERE execution_status IN (
                'SUCCESS WITH CLEANUP FAIL',
                'SUCCESS WITH CLEANUP FAILURE',
                'EXECUTED WITH CLEANUP FAILURE',
                                                        'EXECUTED WITH CLEANUP FAIL',
              );
              """);
    }
  }
}
