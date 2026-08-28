package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds the {@code autonomous_run_agent_ids} JSONB column to {@code autonomous_runs}.
 *
 * <p>Stores the XTM One agent ids the orchestrator may consult as specialist handover targets
 * during a run (in addition to the built-in payload creator), as a JSON array of ids. Forwarded to
 * XTM One at engage time.
 *
 * <p>Additive, idempotent, and lock-light: a nullable {@code ADD COLUMN} is metadata-only on
 * PostgreSQL 11+ (no table rewrite), and {@code IF NOT EXISTS} makes re-running a no-op.
 */
@Component
public class V6_20260804180000000__Add_autonomous_agent_ids extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "ALTER TABLE autonomous_runs ADD COLUMN IF NOT EXISTS autonomous_run_agent_ids jsonb;");
    }
  }
}
