package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_02__Add_Parameters_Custom_Dashboard extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    try (Statement statement = connection.createStatement()) {
      statement.execute(
          """
              ALTER TABLE exercises ADD COLUMN IF NOT EXISTS exercise_custom_dashboard VARCHAR(255) NULL;
              ALTER TABLE exercises ADD CONSTRAINT exercise_custom_dashboard_fk FOREIGN KEY (exercise_custom_dashboard) REFERENCES custom_dashboards(custom_dashboard_id) ON DELETE SET NULL;
              ALTER TABLE scenarios ADD COLUMN IF NOT EXISTS scenario_custom_dashboard VARCHAR(255) NULL;
              ALTER TABLE scenarios ADD CONSTRAINT scenario_custom_dashboard_fk FOREIGN KEY (scenario_custom_dashboard) REFERENCES custom_dashboards(custom_dashboard_id) ON DELETE SET NULL;
              CREATE TABLE IF NOT EXISTS custom_dashboards_parameters (
                  custom_dashboards_parameter_id varchar(255) not null constraint custom_dashboards_parameters_pkey primary key,
                  custom_dashboard_id varchar(255) NOT NULL REFERENCES custom_dashboards(custom_dashboard_id) ON DELETE CASCADE,
                  custom_dashboards_parameter_name text NOT NULL,
                  custom_dashboards_parameter_type text NOT NULL
                  );
              CREATE INDEX IF NOT EXISTS idx_custom_dashboards_parameters on custom_dashboards_parameters(custom_dashboards_parameter_id);
              """);
    }
  }
}
