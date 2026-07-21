package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V6_20260720191000000__Add_payload_expected_security_platforms
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      // Optional map of expectation type to the security platform types expected to fulfil it.
      // Stored as JSONB (e.g. {"DETECTION":["EDR","XDR"],"PREVENTION":["EDR"]}). NULL / empty
      // means "any platform" (legacy behaviour). Metadata-only, lock-light nullable ADD COLUMN.
      statement.executeUpdate(
          """
          ALTER TABLE payloads
            ADD COLUMN IF NOT EXISTS payload_expected_security_platforms JSONB;
          """);
    }
  }
}
