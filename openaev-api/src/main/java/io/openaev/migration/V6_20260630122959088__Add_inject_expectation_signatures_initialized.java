package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V6_20260630122959088__Add_inject_expectation_signatures_initialized
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
              ALTER TABLE injects_expectations
              ADD COLUMN IF NOT EXISTS inject_expectation_signatures_initialized BOOLEAN NOT NULL DEFAULT FALSE
              """);
    }
  }
}

// -- ROLLBACK --
// ALTER TABLE injects_expectations DROP COLUMN IF EXISTS inject_expectation_signatures_initialized;
