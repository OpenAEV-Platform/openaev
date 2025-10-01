package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_85__Add_Custom_Dashboard extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      // Custom dashboards table
      statement.execute(
          """
              CREATE TABLE custom_dashboards (
                  custom_dashboard_id varchar(255) NOT NULL CONSTRAINT custom_dashboards_pkey PRIMARY KEY,
                  custom_dashboard_name VARCHAR(255) NOT NULL,
                  custom_dashboard_description VARCHAR(255),
                  custom_dashboard_created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
                  custom_dashboard_updated_at TIMESTAMP WITH TIME ZONE DEFAULT now()
              );
              """);
      // Widgets table
      statement.execute(
          """
                  CREATE TABLE widgets (
                      widget_id varchar(255) NOT NULL CONSTRAINT widgets_pkey PRIMARY KEY,
                      widget_type VARCHAR(255) NOT NULL,
                      widget_config JSONB,
                      widget_layout JSONB,
                      widget_custom_dashboard varchar(255) constraint custom_dashboards_pkey references custom_dashboards on delete cascade,
                      widget_created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
                      widget_updated_at TIMESTAMP WITH TIME ZONE DEFAULT now()
                  );
              """);
    }
  }
}
