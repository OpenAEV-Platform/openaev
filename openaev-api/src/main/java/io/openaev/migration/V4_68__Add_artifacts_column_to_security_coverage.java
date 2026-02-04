package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_68__Add_artifacts_column_to_security_coverage extends BaseJavaMigration {
  @Override
  public void migrate(Context context) throws Exception {
    try (Statement stmt = context.getConnection().createStatement()) {
      stmt.execute(
          "ALTER TABLE security_coverages ADD COLUMN IF NOT EXISTS security_coverage_artifacts_refs JSONB;");
    }
  }
}
