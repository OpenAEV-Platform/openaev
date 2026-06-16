package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V5_24__Add_workflow_simulation_run_index extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement stmt = context.getConnection().createStatement()) {
      // Partial index supporting the NOT IN subquery in
      // InjectSpecification.pendingInjectWithThresholdMinutes():
      // the time-based engine excludes injects whose simulation has an active
      // chaining workflow (status = 'RUN'). This index keeps the subquery cheap
      // because only running workflows are indexed (bounded by concurrent runs,
      // not history).
      stmt.execute(
          """
          CREATE INDEX IF NOT EXISTS idx_workflows_simulation_run
            ON workflows (workflow_simulation_id)
            WHERE workflow_status = 'RUN';
          """);

      // Partial index on injects_statuses for terminal statuses: used by
      // InjectStatusRepository.countLaunchedInjectsSince() which is called on every
      // chaining step to enforce rate limiting. Keeps the scan cheap on large simulations
      // by restricting the B-tree to the five terminal statuses only.
      stmt.execute(
          """
          CREATE INDEX IF NOT EXISTS idx_injects_statuses_terminal_sent_date
            ON injects_statuses (status_inject, tracking_sent_date)
            WHERE status_name IN ('EXECUTED', 'PARTIAL', 'ERROR', 'MAYBE_PREVENTED', 'MAYBE_PARTIAL_PREVENTED');
          """);

      // Add step_ready column to delay queue for rate-limit rescheduling
      stmt.execute(
          """
          ALTER TABLE steps_delay_queue
            ADD COLUMN IF NOT EXISTS steps_delay_queue_step_ready_id VARCHAR(255)
            REFERENCES steps(step_id) ON DELETE CASCADE;
          """);
    }
  }
}
