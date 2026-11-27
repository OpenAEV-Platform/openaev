package io.openaev.migration;

import io.openaev.utils.MigrationTableLocks;
import java.util.List;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V6_20261126173000000__Add_ai_target_credential extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    MigrationTableLocks.runLocked(
        context.getConnection(),
        "AI target credential",
        List.of("secrets", "assets"),
        statement -> {
          statement.execute("ALTER TABLE secrets ADD COLUMN IF NOT EXISTS secret_api_token TEXT;");
          statement.execute(
              """
              ALTER TABLE assets ADD COLUMN IF NOT EXISTS ai_target_credential_id VARCHAR(255)
                REFERENCES secret_references (secret_reference_id) ON DELETE SET NULL;
              """);
          statement.execute(
              "CREATE INDEX IF NOT EXISTS idx_assets_ai_target_credential"
                  + " ON assets (ai_target_credential_id);");
          statement.execute("ALTER TABLE assets DROP COLUMN IF EXISTS ai_target_token;");
        });
  }
}
