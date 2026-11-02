package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V6_20261007120000000__Add_payload_last_modified_by extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      // User who last modified a payload. Nullable + ON DELETE SET NULL so removing the user never
      // blocks or cascades onto the payload; existing rows stay null (unknown), no backfill.
      statement.executeUpdate(
          """
          ALTER TABLE payloads
            ADD COLUMN IF NOT EXISTS payload_last_modified_by VARCHAR(255)
              REFERENCES users(user_id) ON DELETE SET NULL;
          """);
      statement.executeUpdate(
          "CREATE INDEX IF NOT EXISTS idx_payloads_last_modified_by ON payloads(payload_last_modified_by);");
    }
  }
}
