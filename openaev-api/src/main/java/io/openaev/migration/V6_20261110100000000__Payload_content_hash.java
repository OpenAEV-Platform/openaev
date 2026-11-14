package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V6_20261110100000000__Payload_content_hash extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "ALTER TABLE payloads ADD COLUMN IF NOT EXISTS payload_content_hash VARCHAR(64);");
      statement.execute(
          """
          CREATE INDEX IF NOT EXISTS idx_payloads_content_hash
            ON payloads (tenant_id, payload_content_hash)
            WHERE payload_content_hash IS NOT NULL;
          """);
    }
  }
}
