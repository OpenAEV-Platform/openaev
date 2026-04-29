package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_86__Add_platform_groups_users extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      // Create platform_groups_users join table
      statement.execute(
          """
              CREATE TABLE IF NOT EXISTS platform_groups_users (
                  platform_group_id VARCHAR(255) NOT NULL,
                  user_id VARCHAR(255) NOT NULL,
                  PRIMARY KEY (platform_group_id, user_id),
                  CONSTRAINT fk_pgu_platform_group FOREIGN KEY (platform_group_id)
                      REFERENCES platform_groups(platform_group_id) ON DELETE CASCADE,
                  CONSTRAINT fk_pgu_user FOREIGN KEY (user_id)
                      REFERENCES users(user_id) ON DELETE CASCADE
              );
              """);

      // Create platform_groups_platform_roles join table
      statement.execute(
          """
              CREATE TABLE IF NOT EXISTS platform_groups_platform_roles (
                  platform_group_id VARCHAR(255) NOT NULL,
                  platform_role_id VARCHAR(255) NOT NULL,
                  PRIMARY KEY (platform_group_id, platform_role_id),
                  CONSTRAINT fk_pgpr_platform_group FOREIGN KEY (platform_group_id)
                      REFERENCES platform_groups(platform_group_id) ON DELETE CASCADE,
                  CONSTRAINT fk_pgpr_platform_role FOREIGN KEY (platform_role_id)
                      REFERENCES platform_roles(platform_role_id) ON DELETE CASCADE
              );
              """);
    }
  }
}
