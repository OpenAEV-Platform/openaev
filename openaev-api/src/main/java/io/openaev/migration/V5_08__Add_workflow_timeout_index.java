package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V5_08__Add_workflow_timeout_index extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement stmt = context.getConnection().createStatement()) {
      // Partial index supporting WorkflowRepository.findAllExpiredRunWorkflows():
      // the timeout-sweep job filters by workflow_status='RUN' and workflow_timeout_enabled=true,
      // then compares workflow_created_at against now(). Restricting the index to running,
      // timeout-enabled workflows keeps it small (bounded by concurrent runs, not history).
      stmt.execute(
          """
          CREATE INDEX IF NOT EXISTS idx_workflows_active_timeout
            ON workflows (workflow_created_at)
            WHERE workflow_status = 'RUN' AND workflow_timeout_enabled = true;
          """);
    }
  }
}
