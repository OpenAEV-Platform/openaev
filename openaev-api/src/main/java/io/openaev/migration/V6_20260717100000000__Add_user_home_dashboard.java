package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds the per-user home dashboard preference. Resolution order for the home page is: built-in
 * platform default dashboard, then tenant setting (platform_home_dashboard), then this user-level
 * override.
 */
@Component
public class V6_20260717100000000__Add_user_home_dashboard extends BaseJavaMigration {
  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.executeUpdate(
          """
          ALTER TABLE users
            ADD COLUMN IF NOT EXISTS user_home_dashboard VARCHAR(255)
              REFERENCES custom_dashboards(custom_dashboard_id) ON DELETE SET NULL;
          """);
    }
  }
}
