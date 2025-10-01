package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_45__Add_indexes_scenario_exercise_latency extends BaseJavaMigration {
  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement select = connection.createStatement();
    select.execute(
        "CREATE INDEX IF NOT EXISTS idx_inject_expectation_inject_id ON injects_expectations(inject_id);");
    select.execute(
        "CREATE INDEX IF NOT EXISTS idx_inject_expectation_team_id ON injects_expectations(team_id);");
    select.execute(
        "CREATE INDEX IF NOT EXISTS idx_inject_expectation_user_id ON injects_expectations(user_id);");
    select.execute(
        "CREATE INDEX IF NOT EXISTS idx_inject_expectation_asset_group_id ON injects_expectations(asset_group_id);");
    select.execute(
        "CREATE INDEX IF NOT EXISTS idx_inject_expectation_asset_id ON injects_expectations(asset_id);");
    select.execute(
        "CREATE INDEX IF NOT EXISTS idx_inject_expectation_exercise_id ON injects_expectations(exercise_id);");
  }
}
