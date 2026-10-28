package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds the optional columns that let a credential reach a sovereign or mocked cloud provider.
 *
 * <p>Every column is nullable and has no default: a credential without them keeps targeting the
 * official endpoints. {@code secret_gcp_custom_projects_uri} is shared by both GCP subtypes, like
 * {@code secret_gcp_scope}, and the two Azure columns by both Azure subtypes. The access token and
 * the certificate are stored encrypted, exactly like {@code secret_gcp_oauth_client_secret} and
 * {@code secret_gcp_private_key_json}.
 */
@Component
public class V6_20261028120000000__Add_sovereign_cloud_columns_to_secrets
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
            ALTER TABLE secrets
            ADD COLUMN IF NOT EXISTS secret_aws_account_id VARCHAR(255),
            ADD COLUMN IF NOT EXISTS secret_aws_endpoint_url VARCHAR(255),
            ADD COLUMN IF NOT EXISTS secret_gcp_custom_token_uri VARCHAR(255),
            ADD COLUMN IF NOT EXISTS secret_gcp_custom_projects_uri VARCHAR(255),
            ADD COLUMN IF NOT EXISTS secret_gcp_custom_resource_manager_uri VARCHAR(255),
            ADD COLUMN IF NOT EXISTS secret_gcp_custom_iam_uri VARCHAR(255),
            ADD COLUMN IF NOT EXISTS secret_gcp_custom_oauth_access_token TEXT,
            ADD COLUMN IF NOT EXISTS secret_azure_authority_host_uri VARCHAR(255),
            ADD COLUMN IF NOT EXISTS secret_azure_requests_certificate BYTEA
          """);
    }
  }
}
