package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_29__Add_default_roles extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    try (Statement statement = connection.createStatement()) {
      statement.execute(
          """
              INSERT INTO roles (role_id, role_name) VALUES
                  (gen_random_uuid(), 'Observer'),
                  (gen_random_uuid(), 'Manager'),
                  (gen_random_uuid(), 'Admin');
              """);

      statement.execute(
          """
                  INSERT INTO roles_capabilities (role_id, capability)
                      SELECT r.role_id, c.capability
                        FROM roles r
                          JOIN (VALUES
                              ('ACCESS_ASSESSMENT'),
                              ('ACCESS_ASSETS'),
                              ('ACCESS_PAYLOADS'),
                              ('ACCESS_DASHBOARDS'),
                              ('ACCESS_FINDINGS'),
                              ('ACCESS_DOCUMENTS'),
                              ('ACCESS_CHANNELS'),
                              ('ACCESS_CHALLENGES'),
                              ('ACCESS_LESSONS_LEARNED'),
                              ('ACCESS_SECURITY_PLATFORMS')
                          ) AS c(capability) ON r.role_name = 'Observer';
                  """);

      statement.execute(
          """
                  INSERT INTO roles_capabilities (role_id, capability)
                      SELECT r.role_id, c.capability
                        FROM roles r
                          JOIN (VALUES
                              ('ACCESS_ASSESSMENT'),
                              ('MANAGE_ASSESSMENT'),
                              ('DELETE_ASSESSMENT'),
                              ('LAUNCH_ASSESSMENT'),
                              ('MANAGE_TEAMS_AND_PLAYERS'),
                              ('DELETE_TEAMS_AND_PLAYERS'),
                              ('ACCESS_ASSETS'),
                              ('MANAGE_ASSETS'),
                              ('DELETE_ASSETS'),
                              ('ACCESS_PAYLOADS'),
                              ('MANAGE_PAYLOADS'),
                              ('DELETE_PAYLOADS'),
                              ('ACCESS_DASHBOARDS'),
                              ('MANAGE_DASHBOARDS'),
                              ('DELETE_DASHBOARDS'),
                              ('ACCESS_FINDINGS'),
                              ('MANAGE_FINDINGS'),
                              ('DELETE_FINDINGS'),
                              ('ACCESS_DOCUMENTS'),
                              ('MANAGE_DOCUMENTS'),
                              ('DELETE_DOCUMENTS'),
                              ('ACCESS_CHANNELS'),
                              ('MANAGE_CHANNELS'),
                              ('DELETE_CHANNELS'),
                              ('ACCESS_CHALLENGES'),
                              ('MANAGE_CHALLENGES'),
                              ('DELETE_CHALLENGES'),
                              ('ACCESS_LESSONS_LEARNED'),
                              ('MANAGE_LESSONS_LEARNED'),
                              ('DELETE_LESSONS_LEARNED'),
                              ('ACCESS_SECURITY_PLATFORMS'),
                              ('DELETE_SECURITY_PLATFORMS'),
                              ('MANAGE_SECURITY_PLATFORMS')
                          ) AS c(capability) ON r.role_name = 'Manager';
                  """);
      statement.execute(
          """
                  INSERT INTO roles_capabilities (role_id, capability)
                      SELECT r.role_id, c.capability
                        FROM roles r
                          JOIN (VALUES
                              ('BYPASS')
                          ) AS c(capability) ON r.role_name = 'Admin';
                  """);
    }
  }
}
