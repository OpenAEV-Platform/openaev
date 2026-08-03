package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds the {@code scenario_autonomous} flag to {@code scenarios}.
 *
 * <p>An autonomous (AI-driven) run owns exactly one scenario, but the link lived only on the {@code
 * autonomous_runs} row (keyed by {@code autonomous_run_scenario_id}), so the frontend had to probe
 * the autonomous-run lookup endpoint on EVERY scenario load just to discover a plain manual
 * scenario is manual (an expected 404 that then re-polled and forced full-page re-renders). This
 * flag lets the scenario payload state it directly, so a manual scenario never triggers the lookup.
 *
 * <p>Backfills existing autonomous scenarios (those already referenced by an {@code
 * autonomous_runs} row) to {@code true} so their AI cockpit keeps rendering after the upgrade.
 *
 * <p>Additive, idempotent, and lock-light: a boolean {@code ADD COLUMN} with a constant default is
 * metadata-only on PostgreSQL 11+ (no table rewrite), and {@code IF NOT EXISTS} makes re-running a
 * no-op.
 */
@Component
public class V6_20260803160000000__Add_scenario_autonomous_flag extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "ALTER TABLE scenarios ADD COLUMN IF NOT EXISTS scenario_autonomous boolean NOT NULL"
              + " DEFAULT false;");
      statement.execute(
          "UPDATE scenarios SET scenario_autonomous = true WHERE scenario_id IN (SELECT"
              + " autonomous_run_scenario_id FROM autonomous_runs WHERE autonomous_run_scenario_id"
              + " IS NOT NULL);");
    }
  }
}
