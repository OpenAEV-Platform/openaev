package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Stores the name of the XTM Hub organization the platform is registered in, returned at
 * registration and with each connectivity refresh. Nullable: it stays empty until a hub that knows
 * the field answers.
 */
@Component
public class V6_20261124100000000__Add_organization_name_to_tenant_xtmhub_registration
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          ALTER TABLE tenant_xtmhub_registrations
              ADD COLUMN IF NOT EXISTS registration_organization_name VARCHAR(255);
          """);
    }
  }
}
