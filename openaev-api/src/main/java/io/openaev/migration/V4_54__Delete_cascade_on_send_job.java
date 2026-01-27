package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_54__Delete_cascade_on_send_job extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          ALTER TABLE security_coverage_send_job ADD COLUMN security_coverage_send_job_simulation_tmp VARCHAR(255) UNIQUE REFERENCES exercises(exercise_id) ON DELETE CASCADE;
          UPDATE security_coverage_send_job SET security_coverage_send_job_simulation_tmp = security_coverage_send_job_simulation;
          ALTER TABLE security_coverage_send_job DROP COLUMN security_coverage_send_job_simulation;
          ALTER TABLE security_coverage_send_job ALTER COLUMN security_coverage_send_job_simulation_tmp SET NOT NULL;
          ALTER TABLE security_coverage_send_job RENAME security_coverage_send_job_simulation_tmp TO security_coverage_send_job_simulation;
          """);
    }
  }
}
