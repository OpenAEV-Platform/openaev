package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V6_20260915143000000__Deduplicate_step_delay_queue_and_enforce_unique_key
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          DELETE FROM steps_delay_queue sdq
          USING (
            SELECT steps_delay_queue_id
            FROM (
              SELECT steps_delay_queue_id,
                     ROW_NUMBER() OVER (
                       PARTITION BY steps_delay_queue_workflow_run_id,
                                    steps_delay_queue_step_template_id,
                                    COALESCE(steps_delay_queue_input, '')
                       ORDER BY steps_delay_queue_created_at DESC, steps_delay_queue_id DESC
                     ) AS rn
              FROM steps_delay_queue
            ) ranked
            WHERE ranked.rn > 1
          ) duplicates
          WHERE sdq.steps_delay_queue_id = duplicates.steps_delay_queue_id;
          """);

      statement.execute(
          """
            CREATE UNIQUE INDEX uk_steps_delay_queue_run_step_input
            ON steps_delay_queue (
              steps_delay_queue_workflow_run_id,
              steps_delay_queue_step_template_id,
              steps_delay_queue_input
            ) NULLS NOT DISTINCT;
          """);

      statement.execute(
          """
          ALTER TABLE steps_delay_queue
          ALTER COLUMN steps_delay_queue_id
          SET DEFAULT gen_random_uuid()::text;
          """);
    }
  }
}
