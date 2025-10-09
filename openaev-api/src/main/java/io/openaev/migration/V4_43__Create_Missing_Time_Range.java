package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_43__Create_Missing_Time_Range extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement select = context.getConnection().createStatement()) {
      select.execute(
          """
                    INSERT INTO custom_dashboards_parameters (custom_dashboards_parameter_id, custom_dashboard_id, custom_dashboards_parameter_name, custom_dashboards_parameter_type)
                    SELECT gen_random_uuid(), cd.custom_dashboard_id, 'Time Range', 'timeRange'
                    FROM custom_dashboards cd
                    LEFT JOIN custom_dashboards_parameters cdp
                       ON cdp.custom_dashboard_id = cd.custom_dashboard_id
                       AND cdp.custom_dashboards_parameter_type = 'timeRange'
                    WHERE cdp.custom_dashboards_parameter_type IS NULL
                """);
      select.execute(
          """
                      INSERT INTO custom_dashboards_parameters (custom_dashboards_parameter_id, custom_dashboard_id, custom_dashboards_parameter_name, custom_dashboards_parameter_type)
                      SELECT gen_random_uuid(), cd.custom_dashboard_id, 'End date', 'endDate'
                      FROM custom_dashboards cd
                      LEFT JOIN custom_dashboards_parameters cdp
                         ON cdp.custom_dashboard_id = cd.custom_dashboard_id
                         AND cdp.custom_dashboards_parameter_type = 'endDate'
                      WHERE cdp.custom_dashboards_parameter_type IS NULL
                """);
      select.execute(
          """
                      INSERT INTO custom_dashboards_parameters (custom_dashboards_parameter_id, custom_dashboard_id, custom_dashboards_parameter_name, custom_dashboards_parameter_type)
                      SELECT gen_random_uuid(), cd.custom_dashboard_id, 'Start date', 'startDate'
                      FROM custom_dashboards cd
                      LEFT JOIN custom_dashboards_parameters cdp
                         ON cdp.custom_dashboard_id = cd.custom_dashboard_id
                         AND cdp.custom_dashboards_parameter_type = 'startDate'
                      WHERE cdp.custom_dashboards_parameter_type IS NULL
                """);
    }
  }
}
