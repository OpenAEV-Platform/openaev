package io.openbas.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_28__Add_security_coverage_send_job extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    try (Statement statement = connection.createStatement()) {
      statement.execute(
          """
          CREATE TABLE security_coverage_send_job (
              security_coverage_send_job_id VARCHAR(255) PRIMARY KEY,
              security_coverage_send_job_simulation VARCHAR(255) NOT NULL UNIQUE REFERENCES exercises(exercise_id),
              security_coverage_send_job_status VARCHAR(255) NOT NULL DEFAULT 'PENDING',
              security_coverage_send_job_updated_at TIMESTAMPTZ DEFAULT now()
          );
          """);
    }
  }
}
