package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V5_26__Add_chaining_rate_limit_support extends BaseJavaMigration {

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

      // Add rate_limit_count column to steps_delay_queue to carry the reschedule count
      // through the delay queue so it can be propagated to newly created READY steps.
      stmt.execute(
          """
          ALTER TABLE steps_delay_queue
            ADD COLUMN IF NOT EXISTS steps_delay_queue_rate_limit_count INTEGER DEFAULT 0;
          """);

      // Add rate_limit_count column to steps to store the count on the READY step
      // so it can be read by InjectExecutionStep to emit an INFO trace.
      stmt.execute(
          """
          ALTER TABLE steps
            ADD COLUMN IF NOT EXISTS step_rate_limit_count INTEGER DEFAULT 0;
          """);
    }
  }
}
