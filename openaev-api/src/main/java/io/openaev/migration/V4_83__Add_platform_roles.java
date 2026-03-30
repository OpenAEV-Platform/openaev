package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_83__Add_platform_roles extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      // Create platform_roles table
      statement.execute(
          """
              CREATE TABLE IF NOT EXISTS platform_roles (
                  platform_role_id VARCHAR(255) NOT NULL PRIMARY KEY,
                  platform_role_name VARCHAR(255) NOT NULL UNIQUE,
                  platform_role_description TEXT,
                  platform_role_created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
                  platform_role_updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
              );
              """);

      // Create platform_roles_capabilities join table
      statement.execute(
          """
              CREATE TABLE IF NOT EXISTS platform_roles_capabilities (
                  platform_role_id VARCHAR(255) NOT NULL,
                  capability VARCHAR(255) NOT NULL,
                  PRIMARY KEY (platform_role_id, capability),
                  CONSTRAINT fk_prc_platform_role FOREIGN KEY (platform_role_id)
                      REFERENCES platform_roles(platform_role_id) ON DELETE CASCADE
              );
              """);
    }
  }
}
