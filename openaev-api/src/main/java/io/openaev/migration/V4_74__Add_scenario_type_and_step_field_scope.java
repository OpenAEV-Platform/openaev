package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_74__Add_scenario_type_and_step_field_scope extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement select = context.getConnection().createStatement()) {

      select.execute(
          """
              ALTER TABLE scenarios
                ADD COLUMN IF NOT EXISTS scenario_type VARCHAR(50) DEFAULT 'time-based';
          """);

      select.execute(
          """
              ALTER TABLE steps
                ADD COLUMN IF NOT EXISTS step_field_scope VARCHAR(50) DEFAULT 'GLOBAL';
          """);

      // Allow workflows to be linked to a scenario directly (not only via simulation)
      select.execute(
          """
              ALTER TABLE workflows
                ADD COLUMN IF NOT EXISTS workflow_scenario_id VARCHAR(255) REFERENCES scenarios(scenario_id) ON DELETE CASCADE;
          """);

      select.execute(
          """
              ALTER TABLE workflows
                ALTER COLUMN workflow_simulation_id DROP NOT NULL;
          """);

      select.execute(
          """
              CREATE INDEX IF NOT EXISTS idx_workflows_scenario_id
                ON workflows(workflow_scenario_id);
          """);
    }
  }
}
