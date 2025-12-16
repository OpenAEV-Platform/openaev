package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V4_57__Add_indicators_column_to_security_coverage extends BaseJavaMigration {
  @Override
  public void migrate(Context context) throws Exception {
    try (Statement stmt = context.getConnection().createStatement()) {
      stmt.execute(
          "ALTER TABLE security_coverages ADD COLUMN security_coverage_indicators_refs JSONB;");
    }
  }
}
