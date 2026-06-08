package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V5_18__Add_secrets_provider_connector_type extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement stmt = context.getConnection().createStatement()) {
      stmt.execute(
          """
            ALTER TYPE connector_type
            ADD VALUE IF NOT EXISTS 'SECRETS_PROVIDER'
            AFTER 'EXECUTOR'
            """);
    }
  }
}
