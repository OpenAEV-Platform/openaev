package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_93__Add_tenant_settings_capabilities_to_default_roles extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      // Add ACCESS_TENANT_SETTINGS to Observer role (read-only access)
      statement.execute(
          """
              INSERT INTO roles_capabilities (role_id, capability)
                  SELECT r.role_id, c.capability
                    FROM roles r
                      JOIN (VALUES
                          ('ACCESS_TENANT_SETTINGS')
                      ) AS c(capability) ON r.role_name = 'Observer'
                  ON CONFLICT DO NOTHING;
              """);

      // Add full tenant settings capabilities to Manager role
      statement.execute(
          """
              INSERT INTO roles_capabilities (role_id, capability)
                  SELECT r.role_id, c.capability
                    FROM roles r
                      JOIN (VALUES
                          ('ACCESS_TENANT_SETTINGS'),
                          ('MANAGE_TENANT_SETTINGS'),
                          ('DELETE_TENANT_SETTINGS')
                      ) AS c(capability) ON r.role_name = 'Manager'
                  ON CONFLICT DO NOTHING;
              """);
    }
  }
}

