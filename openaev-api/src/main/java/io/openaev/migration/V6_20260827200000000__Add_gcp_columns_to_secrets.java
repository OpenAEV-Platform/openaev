package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds the GCP columns to the single-table {@code secrets} inheritance.
 *
 * <p>{@code secret_gcp_scope} and {@code secret_gcp_project_id} are shared by both GCP subtypes,
 * exactly like {@code secret_azure_client_id} is shared by the two Azure ones. The {@code oauth}
 * prefix marks the columns owned by the OAuth2 subtype only.
 */
@Component
public class V6_20260827200000000__Add_gcp_columns_to_secrets extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          ALTER TABLE secrets
          ADD COLUMN IF NOT EXISTS secret_gcp_scope VARCHAR(255),
          ADD COLUMN IF NOT EXISTS secret_gcp_project_id VARCHAR(255),
          ADD COLUMN IF NOT EXISTS secret_gcp_private_key_json BYTEA,
          ADD COLUMN IF NOT EXISTS secret_gcp_oauth_client_id VARCHAR(255),
          ADD COLUMN IF NOT EXISTS secret_gcp_oauth_client_secret TEXT,
          ADD COLUMN IF NOT EXISTS secret_gcp_oauth_refresh_token TEXT
          """);
    }
  }
}
