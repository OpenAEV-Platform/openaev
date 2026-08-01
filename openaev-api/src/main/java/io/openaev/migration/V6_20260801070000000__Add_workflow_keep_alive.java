package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds the {@code workflow_keep_alive} marker to {@code workflows}.
 *
 * <p>An autonomous (AI-driven) attack-path run provisions an EMPTY chaining workflow and lets the
 * XTM One orchestrator author its steps incrementally over the life of the run. The standard
 * evaluator ends a workflow the moment it has no step templates and no active/delayed steps, which
 * would kill an autonomous run at launch (empty) or between decision cycles (idle). This flag lets
 * {@code WorkflowService.evaluateWorkflowProgress} park such a workflow in {@code RUN} instead of
 * transitioning it to {@code END}; autonomous provisioning also disables the workflow timeout so
 * the long-lived incremental build is never force-ended.
 *
 * <p>Additive, idempotent, and lock-light: a constant-default {@code ADD COLUMN} is metadata-only
 * on PostgreSQL 11+ (no table rewrite), and {@code IF NOT EXISTS} makes re-running a no-op.
 */
@Component
public class V6_20260801070000000__Add_workflow_keep_alive extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "ALTER TABLE workflows "
              + "ADD COLUMN IF NOT EXISTS workflow_keep_alive boolean NOT NULL DEFAULT false;");
    }
  }
}
