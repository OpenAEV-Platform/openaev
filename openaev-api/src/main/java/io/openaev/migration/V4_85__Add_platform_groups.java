package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_85__Add_platform_groups extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      // Create platform_groups table
      statement.execute(
          """
              CREATE TABLE IF NOT EXISTS platform_groups (
                  platform_group_id VARCHAR(255) NOT NULL PRIMARY KEY,
                  platform_group_name VARCHAR(255) NOT NULL UNIQUE,
                  platform_group_description TEXT,
                  platform_group_created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
                  platform_group_updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
              );
              """);
    }
  }
}
