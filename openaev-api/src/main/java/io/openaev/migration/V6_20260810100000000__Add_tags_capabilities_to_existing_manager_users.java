package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V6_20260810100000000__Add_tags_capabilities_to_existing_manager_users
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          INSERT INTO roles_capabilities (role_id, capability)
          SELECT r.role_id, c.capability
          FROM roles r
          CROSS JOIN (
              VALUES
                ('ACCESS_TAGS'),
                ('MANAGE_TAGS'),
                ('DELETE_TAGS')
          ) AS c(capability)
          WHERE r.role_name = 'Manager'
            AND r.tenant_id IS NOT NULL
          ON CONFLICT (role_id, capability) DO NOTHING;
          """);
    }
  }
}
