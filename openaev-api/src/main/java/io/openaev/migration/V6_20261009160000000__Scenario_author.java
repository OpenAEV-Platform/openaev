package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/** Scenario author (F33), same shape as the payload author: user, team or organization. */
@Component
public class V6_20261009160000000__Scenario_author extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          ALTER TABLE scenarios
            ADD COLUMN IF NOT EXISTS scenario_author_user VARCHAR(255)
              REFERENCES users(user_id) ON DELETE SET NULL,
            ADD COLUMN IF NOT EXISTS scenario_author_team VARCHAR(255)
              REFERENCES teams(team_id) ON DELETE SET NULL,
            ADD COLUMN IF NOT EXISTS scenario_author_organization VARCHAR(255)
              REFERENCES organizations(organization_id) ON DELETE SET NULL;
          """);
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_scenarios_author_user ON scenarios(scenario_author_user);");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_scenarios_author_team ON scenarios(scenario_author_team);");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_scenarios_author_organization ON scenarios(scenario_author_organization);");
    }
  }
}
