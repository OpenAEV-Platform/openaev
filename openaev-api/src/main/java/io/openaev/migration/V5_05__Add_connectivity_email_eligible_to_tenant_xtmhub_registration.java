package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V5_05__Add_connectivity_email_eligible_to_tenant_xtmhub_registration
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
              ALTER TABLE tenant_xtmhub_registrations
                  ADD COLUMN IF NOT EXISTS registration_connectivity_email_eligible BOOLEAN NOT NULL DEFAULT TRUE;
              """);
    }
  }
}
