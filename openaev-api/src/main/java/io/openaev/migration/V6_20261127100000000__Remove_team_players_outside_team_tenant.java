package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Removes the players of a team who are not members of the team's tenant, from the team and from
 * its simulation and scenario audiences.
 *
 * <p>A player is a member of the tenant of each team they play in. Users detached from a tenant
 * stayed in its teams, so its injects still reached them. The audiences go first, then the team
 * memberships. The team search documents do not hold the players, so no re-feed is needed.
 */
@Component
public class V6_20261127100000000__Remove_team_players_outside_team_tenant
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      for (String table : new String[] {"exercises_teams_users", "scenarios_teams_users"}) {
        statement.execute(
            """
            DELETE FROM %s audience
            USING teams t
            WHERE t.team_id = audience.team_id
              AND NOT EXISTS (SELECT 1 FROM users_tenants ut
                              WHERE ut.user_id = audience.user_id
                                AND ut.tenant_id = t.tenant_id);
            """
                .formatted(table));
      }
      statement.execute(
          """
          DELETE FROM users_teams ut_team
          USING teams t
          WHERE t.team_id = ut_team.team_id
            AND NOT EXISTS (SELECT 1 FROM users_tenants ut
                            WHERE ut.user_id = ut_team.user_id
                              AND ut.tenant_id = t.tenant_id);
          """);
    }
  }
}
