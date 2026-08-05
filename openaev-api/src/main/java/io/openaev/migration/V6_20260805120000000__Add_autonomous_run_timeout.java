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
 * <p>Additive, idempotent, and lock-light: nullable {@code ADD COLUMN}s are metadata-only on
 * PostgreSQL 11+ (no table rewrite), and {@code IF NOT EXISTS} makes re-running a no-op.
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
    }
  }
}
