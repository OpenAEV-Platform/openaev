package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V6_20261120110000000__Payload_base extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          ALTER TABLE payloads
            ADD COLUMN IF NOT EXISTS payload_base VARCHAR(255)
              REFERENCES payloads (payload_id) ON DELETE SET NULL;
          """);
      statement.execute("CREATE INDEX IF NOT EXISTS payloads_base_idx ON payloads (payload_base);");
    }
  }
}
