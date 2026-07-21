package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V6_20260720190000000__Add_expectation_expected_security_platforms
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      // Security platform types expected to fulfil a technical expectation. Stored as a JSONB array
      // of SECURITY_PLATFORM_TYPE names (e.g. ["EDR","XDR"]). NULL / empty means "any platform"
      // (legacy behaviour). Metadata-only, lock-light nullable ADD COLUMN.
      statement.executeUpdate(
          """
          ALTER TABLE injects_expectations
            ADD COLUMN IF NOT EXISTS inject_expectation_expected_security_platforms JSONB;
          """);
    }
  }
}
