package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Extends the workflow scope enums so a scope rule can target a TEAM, not only assets and asset
 * groups. This lets a chaining / autonomous workflow scope its audience (teams) the same way it
 * already scopes its asset perimeter, and lets an autonomous run persist its resolved scope onto
 * the workflow so it is enforced and visible instead of living only in the orchestrator's head.
 * Humans are targeted through a team in scope, never as a bare person.
 *
 * <p>{@code ADD VALUE IF NOT EXISTS} is idempotent and safe to re-run. Postgres appends the new
 * label to the existing enum type; it does not rewrite the {@code workflow_scope_rules} table.
 */
@Component
public class V6_20260803170000000__Add_team_scope_rule_types extends BaseJavaMigration {
  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          ALTER TYPE scope_rule_source ADD VALUE IF NOT EXISTS 'TEAM';
          ALTER TYPE scope_rule_value_type ADD VALUE IF NOT EXISTS 'TEAM_ID';
          """);
    }
  }
}
