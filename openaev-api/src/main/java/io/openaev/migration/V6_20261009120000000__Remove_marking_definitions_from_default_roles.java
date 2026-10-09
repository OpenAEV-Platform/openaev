package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V6_20261009120000000__Remove_marking_definitions_from_default_roles
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          DELETE FROM roles_capabilities rc
          USING roles r
          WHERE rc.role_id = r.role_id
            AND r.role_name IN ('Observer', 'Manager')
            AND r.tenant_id IS NOT NULL
            AND rc.capability IN (
              'ACCESS_MARKING_DEFINITION', 'MANAGE_MARKING_DEFINITION', 'DELETE_MARKING_DEFINITION')
          """);
    }
  }
}
