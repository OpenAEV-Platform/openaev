package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_83__Scenario_recurrence extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement select = connection.createStatement();
    // Add recurrence to scenario
    select.executeUpdate(
        """
        ALTER TABLE scenarios ADD COLUMN scenario_recurrence varchar(256);
        ALTER TABLE scenarios ADD COLUMN scenario_recurrence_start timestamp;
        """);
    // Add association table between scenario and exercise
    select.execute(
        """
        CREATE TABLE scenario_exercise (
          scenario_id varchar(255) not null constraint scenario_id_fk references scenarios on delete cascade,
          exercise_id varchar(255) not null constraint exercise_id_fk references exercises on delete cascade,
          constraint scenario_exercise_pkey primary key (scenario_id, exercise_id)
        );
        CREATE INDEX idx_scenario_exercise_scenario on scenario_exercise (scenario_id);
        CREATE INDEX idx_scenario_exercise_exercise on scenario_exercise (exercise_id);
        """);
  }
}
