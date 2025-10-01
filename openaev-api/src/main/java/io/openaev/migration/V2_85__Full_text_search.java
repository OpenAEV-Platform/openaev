package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_85__Full_text_search extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement select = connection.createStatement();
    // Add full text search index on assets, assets groups, players, teams, organizations
    select.execute(
        """
        CREATE EXTENSION IF NOT EXISTS pg_trgm;

        CREATE INDEX idx_pg_trgm_assets_asset_name ON assets USING gin(to_tsvector('simple', asset_name));
        CREATE INDEX idx_pg_trgm_asset_groups_asset_group_name ON asset_groups USING gin(to_tsvector('simple', asset_group_name));
        CREATE INDEX idx_pg_trgm_users_user_email ON users USING gin(to_tsvector('simple', user_email));
        CREATE INDEX idx_pg_trgm_teams_team_name ON teams USING gin(to_tsvector('simple', team_name));
        CREATE INDEX idx_pg_trgm_organizations_organization_name ON organizations USING gin(to_tsvector('simple', organization_name));
        CREATE INDEX idx_pg_trgm_scenarios_scenario_name ON scenarios USING gin(to_tsvector('simple', scenario_name));
        CREATE INDEX idx_pg_trgm_exercises_exercise_name ON exercises USING gin(to_tsvector('simple', exercise_name));
        """);
  }
}
