package io.openaev.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

import java.sql.Statement;

@Component
public class V6_20260818100000000__Add_aws_columns_to_secrets extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          ALTER TABLE secrets
          ADD COLUMN IF NOT EXISTS secret_aws_access_key_id VARCHAR(255),
          ADD COLUMN IF NOT EXISTS secret_aws_secret_access_key TEXT,
          ADD COLUMN IF NOT EXISTS secret_aws_session_token TEXT,
          ADD COLUMN IF NOT EXISTS secret_aws_default_region VARCHAR(255),
          ADD COLUMN IF NOT EXISTS secret_aws_role_arn VARCHAR(255),
          ADD COLUMN IF NOT EXISTS secret_aws_source_identity_type VARCHAR(255),
          ADD COLUMN IF NOT EXISTS secret_aws_external_id VARCHAR(255),
          ADD COLUMN IF NOT EXISTS secret_aws_source_profile_access_key_id VARCHAR(255),
          ADD COLUMN IF NOT EXISTS secret_aws_source_profile_secret_access_key TEXT
          """);
    }
  }
}
