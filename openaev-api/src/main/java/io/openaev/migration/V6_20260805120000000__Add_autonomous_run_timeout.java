package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds the OpenAEV-owned timeout columns to {@code autonomous_runs}.
 *
 * <ul>
 *   <li>{@code autonomous_run_timeout_seconds} - maximum wall-clock lifetime of the run; OpenAEV
 *       steers the orchestrator with winddown signals shortly before it and hard-stops the run when
 *       it is reached.
 *   <li>{@code autonomous_run_started_at} - when the run last became live; the deadline is based on
 *       it.
 *   <li>{@code autonomous_run_deadline_at} - absolute instant at which the watchdog hard-stops the
 *       run.
 *   <li>{@code autonomous_run_winddown_phase} - internal bookkeeping so each winddown nudge is
 *       queued at most once.
 * </ul>
 *
 * <p>Also adds a partial index supporting the watchdog sweep ({@code findRunIdsDueForTimeout}
 * filters by {@code tenant_id} + {@code deadline_at} + live status every 30s): only rows with a
 * non-null deadline are indexed, so the index stays tiny (plan runs and settled runs carry no
 * deadline) while keeping the sweep an index scan as the table grows.
 *
 * <p>Additive, idempotent, and lock-light: nullable {@code ADD COLUMN}s are metadata-only on
 * PostgreSQL 11+ (no table rewrite), the index is partial on a young, small table, and {@code IF
 * NOT EXISTS} makes re-running a no-op.
 */
@Component
public class V6_20260805120000000__Add_autonomous_run_timeout extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "ALTER TABLE autonomous_runs "
              + "ADD COLUMN IF NOT EXISTS autonomous_run_timeout_seconds bigint;");
      statement.execute(
          "ALTER TABLE autonomous_runs "
              + "ADD COLUMN IF NOT EXISTS autonomous_run_started_at timestamptz;");
      statement.execute(
          "ALTER TABLE autonomous_runs "
              + "ADD COLUMN IF NOT EXISTS autonomous_run_deadline_at timestamptz;");
      statement.execute(
          "ALTER TABLE autonomous_runs "
              + "ADD COLUMN IF NOT EXISTS autonomous_run_winddown_phase varchar(32);");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_autonomous_runs_tenant_deadline "
              + "ON autonomous_runs (tenant_id, autonomous_run_deadline_at) "
              + "WHERE autonomous_run_deadline_at IS NOT NULL;");
    }
  }
}
