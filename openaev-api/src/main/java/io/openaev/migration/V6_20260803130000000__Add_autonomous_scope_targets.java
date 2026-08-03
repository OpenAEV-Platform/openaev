package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds the {@code autonomous_run_scope} JSONB column to {@code autonomous_runs}.
 *
 * <p>A run's scope is no longer a single asset group + single team: it is a mixed, multi-kind list
 * of targetable entities - individual assets, asset groups, teams, and persons - the same four
 * kinds an OpenAEV inject can target. This column stores that authoritative list as JSON (each
 * entry {@code {"type": "...", "id": "..."}}); the legacy single {@code
 * autonomous_run_scope_asset_group_id} / {@code autonomous_run_scope_team_id} columns are kept as
 * convenience projections (first of each kind).
 *
 * <p>Additive, idempotent, and lock-light: a nullable {@code ADD COLUMN} is metadata-only on
 * PostgreSQL 11+ (no table rewrite), and {@code IF NOT EXISTS} makes re-running a no-op.
 */
@Component
public class V6_20260803130000000__Add_autonomous_scope_targets extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "ALTER TABLE autonomous_runs ADD COLUMN IF NOT EXISTS autonomous_run_scope jsonb;");
    }
  }
}
