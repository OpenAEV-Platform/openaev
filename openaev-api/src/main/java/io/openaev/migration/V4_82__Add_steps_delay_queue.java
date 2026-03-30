package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_82__Add_steps_delay_queue extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement select = context.getConnection().createStatement()) {

      select.execute(
          """
                                CREATE TABLE IF NOT EXISTS steps_delay_queue (
                                    steps_delay_queue_id VARCHAR(255) NOT NULL CONSTRAINT steps_delay_queue_pkey PRIMARY KEY,
                                    steps_delay_queue_input TEXT,
                                    steps_delay_queue_now TIMESTAMP WITH TIME ZONE,
                                    steps_delay_queue_goal TIMESTAMP WITH TIME ZONE,
                                    steps_delay_queue_delay BIGINT,
                                    steps_delay_queue_step_template_id VARCHAR(255) REFERENCES steps(step_id) ON DELETE CASCADE,
                                    steps_delay_queue_workflow_run_id VARCHAR(255) REFERENCES workflows(workflow_id) ON DELETE CASCADE,
                                    steps_delay_queue_created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
                                    steps_delay_queue_updated_at TIMESTAMP WITH TIME ZONE DEFAULT now()
                                );
                            """);

      select.execute(
          """
                                CREATE INDEX IF NOT EXISTS idx_steps_delay_queue_step_template_id
                                    ON steps_delay_queue(steps_delay_queue_step_template_id);
                                 CREATE INDEX IF NOT EXISTS idx_steps_delay_queue_workflow_run_id
                                    ON steps_delay_queue(steps_delay_queue_workflow_run_id);
                                CREATE INDEX IF NOT EXISTS idx_steps_delay_queue_goal
                                    ON steps_delay_queue(steps_delay_queue_goal);
                            """);
    }
  }
}
