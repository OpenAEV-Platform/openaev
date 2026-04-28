package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_91__Add_scenario_to_workflow extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement select = context.getConnection().createStatement()) {

      select.execute(
          """
                  ALTER TABLE workflows
                      ADD COLUMN IF NOT EXISTS workflow_scenario_id VARCHAR(255)
                          REFERENCES scenarios(scenario_id) ON DELETE CASCADE;
              """);

      select.execute(
          """
                  CREATE UNIQUE INDEX IF NOT EXISTS uk_workflow_scenario_template
                      ON workflows (workflow_scenario_id)
                      WHERE workflow_status = 'TEMPLATE';
              """);

      select.execute("ALTER TABLE workflows ALTER COLUMN workflow_simulation_id DROP NOT NULL");

      select.execute(
          """
                  ALTER TABLE workflows
                      ADD CONSTRAINT chk_workflow_simulation_or_scenario
                      CHECK (
                          (workflow_simulation_id IS NOT NULL AND workflow_scenario_id IS NULL)
                          OR
                          (workflow_simulation_id IS NULL AND workflow_scenario_id IS NOT NULL)
                      );
              """);
    }
  }
}
