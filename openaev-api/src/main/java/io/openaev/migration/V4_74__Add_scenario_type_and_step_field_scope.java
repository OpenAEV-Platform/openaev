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
    }
  }
}
