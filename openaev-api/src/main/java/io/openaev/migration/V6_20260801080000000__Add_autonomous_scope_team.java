package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds the {@code autonomous_run_scope_team_id} column to {@code autonomous_runs}.
 *
 * <p>A run's scope now has two independent facets: an asset-group perimeter (host-targeted
 * objectives) and a team/audience (identity-targeted objectives - phishing, human credential
 * harvesting, ...). An OpenAEV inject can only be delivered to a TEAM, never to a bare person, so an
 * identity objective needs a team to target; the orchestrator uses whichever facet the objective
 * requires. This column carries the optional pre-selected audience alongside the existing {@code
 * autonomous_run_scope_asset_group_id}.
 *
 * <p>Additive, idempotent, and lock-light: a nullable {@code ADD COLUMN} is metadata-only on
 * PostgreSQL 11+ (no table rewrite), and {@code IF NOT EXISTS} makes re-running a no-op.
 */
@Component
public class V6_20260801080000000__Add_autonomous_scope_team extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "ALTER TABLE autonomous_runs "
              + "ADD COLUMN IF NOT EXISTS autonomous_run_scope_team_id varchar(255);");
    }
  }
}
