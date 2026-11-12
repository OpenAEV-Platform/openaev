package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Stores the commercial model the XTM Hub returns with each connectivity refresh. Nullable: it
 * stays empty until a hub that knows the field answers a refresh.
 */
@Component
public class V6_20261105100000000__Add_commercial_model_to_tenant_xtmhub_registration
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          ALTER TABLE tenant_xtmhub_registrations
              ADD COLUMN IF NOT EXISTS registration_commercial_model VARCHAR(255);
          """);
    }
  }
}
