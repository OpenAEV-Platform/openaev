package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds the {@code autonomous_run_step_mirror} JSONB column to {@code autonomous_runs}.
 *
 * <p>An autonomous run authors its attack-path steps on the SIMULATION workflow, but the same steps
 * are now mirrored onto the run's SCENARIO workflow so the scenario carries the attack path and can
 * be exported. This column stores the mapping from each simulation step-template id to its scenario
 * twin ({@code {"<sim step id>": "<scenario step id>"}}) so a step that DEPEND_ONs a simulation
 * parent can be reattached to the correct scenario parent, preserving kill-chain ordering. Internal
 * bookkeeping only; never exposed on the API.
 *
 * <p>Additive, idempotent, and lock-light: a nullable {@code ADD COLUMN} is metadata-only on
 * PostgreSQL 11+ (no table rewrite), and {@code IF NOT EXISTS} makes re-running a no-op.
 */
@Component
public class V6_20260803140000000__Add_autonomous_step_mirror extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "ALTER TABLE autonomous_runs ADD COLUMN IF NOT EXISTS autonomous_run_step_mirror jsonb;");
    }
  }
}
