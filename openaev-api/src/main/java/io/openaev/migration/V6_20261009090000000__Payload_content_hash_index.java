package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/** Lookup of payloads by content hash (zip import, migration, sync). */
@Component
public class V6_20261009090000000__Payload_content_hash_index extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          CREATE INDEX IF NOT EXISTS idx_payloads_content_hash
            ON payloads (tenant_id, payload_content_hash)
            WHERE payload_content_hash IS NOT NULL;
          """);
    }
  }
}
