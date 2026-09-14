package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V6_20260827100000000__Add_azure_columns_to_secrets extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          ALTER TABLE secrets
          ADD COLUMN IF NOT EXISTS secret_azure_client_id VARCHAR(255),
          ADD COLUMN IF NOT EXISTS secret_azure_client_secret TEXT,
          ADD COLUMN IF NOT EXISTS secret_azure_tenant_id VARCHAR(255),
          ADD COLUMN IF NOT EXISTS secret_azure_subscription_id VARCHAR(255),
          ADD COLUMN IF NOT EXISTS secret_azure_environment VARCHAR(255)
          """);
    }
  }
}
