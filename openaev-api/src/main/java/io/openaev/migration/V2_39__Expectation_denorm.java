package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_39__Expectation_denorm extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement select = connection.createStatement();
    // Add relations denormalization
    select.execute(
        "ALTER TABLE injects_expectations_executions ADD exercise_id varchar(256) NOT NULL;");
    select.execute(
        "ALTER TABLE injects_expectations_executions "
            + "ADD CONSTRAINT fk_expectation_exercise "
            + "FOREIGN KEY (exercise_id) REFERENCES exercises(exercise_id) "
            + "ON DELETE CASCADE ;");
    select.execute(
        "ALTER TABLE injects_expectations_executions ADD inject_id varchar(256) NOT NULL;");
    select.execute(
        "ALTER TABLE injects_expectations_executions "
            + "ADD CONSTRAINT fk_expectation_inject "
            + "FOREIGN KEY (inject_id) REFERENCES injects(inject_id) "
            + "ON DELETE CASCADE ;");
  }
}
