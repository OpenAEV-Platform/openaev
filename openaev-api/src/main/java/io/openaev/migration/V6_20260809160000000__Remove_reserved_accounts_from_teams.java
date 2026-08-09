package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Removes reserved service/connector accounts ({@code *@openaev.invalid} - see {@code
 * ReservedKeyValidator}) from team membership and from the enabled-players tables.
 *
 * <p>Reserved accounts are system users, not players: they are hidden from every player list
 * ({@code PlayerService#playerPagination}), so a team membership row for one of them is invisible
 * everywhere yet still counted by {@code team_users_number} - a team card then reports a player
 * count that the persons list can never match. Such rows were inserted by write paths that resolved
 * raw user ids without the reserved-account guard (team players update, simulation / scenario
 * player add, autonomous target-team wrapping); those paths now filter reserved accounts, and this
 * migration purges the memberships that already leaked in.
 *
 * <p>The {@code @openaev.invalid} suffix is reserved for system accounts (service tenants and
 * OpenCTI connectors), so matching on it can never remove a real player. Idempotent (a re-run
 * matches zero rows) and lock-light (targeted DELETEs on small subsets).
 */
@Component
public class V6_20260809160000000__Remove_reserved_accounts_from_teams extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "DELETE FROM users_teams ut USING users u"
              + " WHERE ut.user_id = u.user_id"
              + " AND lower(u.user_email) LIKE '%@openaev.invalid';");
      statement.execute(
          "DELETE FROM exercises_teams_users etu USING users u"
              + " WHERE etu.user_id = u.user_id"
              + " AND lower(u.user_email) LIKE '%@openaev.invalid';");
      statement.execute(
          "DELETE FROM scenarios_teams_users stu USING users u"
              + " WHERE stu.user_id = u.user_id"
              + " AND lower(u.user_email) LIKE '%@openaev.invalid';");
    }
  }
}
