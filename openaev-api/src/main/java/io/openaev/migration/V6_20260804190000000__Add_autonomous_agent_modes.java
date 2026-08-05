package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds the {@code autonomous_run_agent_modes} JSONB column to {@code autonomous_runs}.
 *
 * <p>Stores the per-agent discovery mode for a run, as a JSON object mapping an XTM One agent id
 * (or the orchestrator's own id) to a discovery mode (EXISTING_ONLY / SCOPED / EXPANSIVE). This
 * governs how much latitude each agent has to create new assets / findings / persons from recon on
 * the fly, enforced at OpenAEV's creation choke points. Forwarded to XTM One at engage time.
 *
 * <p>Additive, idempotent, and lock-light: a nullable {@code ADD COLUMN} is metadata-only on
 * PostgreSQL 11+ (no table rewrite), and {@code IF NOT EXISTS} makes re-running a no-op.
 */
@Component
public class V6_20260804190000000__Add_autonomous_agent_modes extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "ALTER TABLE autonomous_runs ADD COLUMN IF NOT EXISTS autonomous_run_agent_modes jsonb;");
    }
  }
}
