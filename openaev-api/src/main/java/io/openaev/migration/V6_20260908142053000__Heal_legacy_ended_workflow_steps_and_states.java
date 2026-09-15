package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Heals workflow runs that reached {@code END} before the chaining engine had a step-ending
 * mechanism (see {@code WorkflowEndService.manageWorkflowEnd}).
 *
 * <p>Those legacy runs were flagged {@code END} at the workflow level, but their non-template steps
 * were left in {@code READY}/{@code RUN} and their {@code workflow_states} rows were never cleared.
 * This migration brings them in line with what a normal end-of-workflow now does: sets the dangling
 * steps to {@code END} and deletes the associated workflow states.
 *
 * <p>Only {@code END} workflows with {@code workflow_keep_alive = false} are touched, so
 * intentionally kept-alive runs are left untouched.
 *
 * <p>Idempotent (a re-run matches zero rows).
 */
@Component
public class V6_20260908142053000__Heal_legacy_ended_workflow_steps_and_states
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "DELETE FROM workflow_states ws"
              + " USING workflows w"
              + " WHERE ws.workflow_execution_id = w.workflow_id"
              + " AND w.workflow_status = 'END'"
              + " AND w.workflow_keep_alive IS NOT TRUE;");

      statement.execute(
          "UPDATE steps"
              + " SET step_status = 'END', step_updated_at = now()"
              + " FROM workflows w"
              + " WHERE steps.step_workflow_id = w.workflow_id"
              + " AND steps.step_status::text NOT IN ('END', 'TEMPLATE')"
              + " AND w.workflow_status = 'END'"
              + " AND w.workflow_keep_alive IS NOT TRUE;");
    }
  }
}
