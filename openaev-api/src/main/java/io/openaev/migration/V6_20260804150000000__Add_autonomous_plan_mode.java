package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds the dry-run ("plan mode") columns to {@code autonomous_runs}.
 *
 * <ul>
 *   <li>{@code autonomous_run_plan_mode}: when true the run is a dry-run - the orchestrator designs
 *       the attack path (scope, steps, decisions) but nothing is ever executed. Defaults to false
 *       so every existing run stays a normal executing run.
 *   <li>{@code autonomous_run_plan_guidance}: the plan summary captured from a dry-run and handed
 *       to the promoted real run as guidance.
 * </ul>
 *
 * <p>Additive, idempotent, and lock-light: a nullable {@code ADD COLUMN} (and a boolean column with
 * a constant default) is metadata-only on PostgreSQL 11+ (no table rewrite), and {@code IF NOT
 * EXISTS} makes re-running a no-op.
 */
@Component
public class V6_20260804150000000__Add_autonomous_plan_mode extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "ALTER TABLE autonomous_runs ADD COLUMN IF NOT EXISTS autonomous_run_plan_mode boolean"
              + " NOT NULL DEFAULT false;");
      statement.execute(
          "ALTER TABLE autonomous_runs ADD COLUMN IF NOT EXISTS autonomous_run_plan_guidance"
              + " text;");
    }
  }
}
