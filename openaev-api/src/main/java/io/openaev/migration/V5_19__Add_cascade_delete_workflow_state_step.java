package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V5_19__Add_cascade_delete_workflow_state_step extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          ALTER TABLE workflow_states
            DROP CONSTRAINT IF EXISTS fk_workflow_state_step;

          ALTER TABLE workflow_states
            ADD CONSTRAINT fk_workflow_state_step
              FOREIGN KEY (workflow_step_template_id)
              REFERENCES steps(step_id)
              ON DELETE CASCADE;
          """);
    }
  }
}
