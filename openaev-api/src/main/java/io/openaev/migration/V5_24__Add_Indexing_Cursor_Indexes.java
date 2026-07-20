package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V5_24__Add_Indexing_Cursor_Indexes extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement stmt = context.getConnection().createStatement()) {
      stmt.execute("CREATE INDEX IF NOT EXISTS idx_assets_updated_at ON assets (asset_updated_at)");
      stmt.execute(
          "CREATE INDEX IF NOT EXISTS idx_exercises_updated_at ON exercises (exercise_updated_at)");
      stmt.execute(
          "CREATE INDEX IF NOT EXISTS idx_scenarios_updated_at ON scenarios (scenario_updated_at)");
      stmt.execute(
          "CREATE INDEX IF NOT EXISTS idx_injects_expectations_updated_at ON injects_expectations (inject_expectation_updated_at)");
    }
  }
}
