package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Heals scenario workflow TEMPLATEs that earlier releases marked keep-alive.
 *
 * <p>Before keep-alive moved to the launched SIMULATION ({@code
 * WorkflowService.markSimulationWorkflowKeepAlive}), building or launching an autonomous run
 * mutated the reusable scenario workflow TEMPLATE ({@code workflow_keep_alive = true}, {@code
 * workflow_timeout_enabled = false}). That left the scenario's "Simulation time out" Scope-tab
 * config disabled/locked, and a subsequent NORMAL launch copied the stale flags so the simulation
 * parked forever instead of running-and-ending. Removing the write only fixes newly planned
 * scenarios, so this migration restores every already-mutated scenario template to the default
 * run-and-end contract (keep-alive off, timeout watchdog back on).
 *
 * <p>Only scenario-scoped TEMPLATE rows with {@code workflow_keep_alive = true} are touched: the
 * autonomous mark was the single writer of that flag on scenario templates, so a user-disabled
 * timeout alone (keep-alive false) is never overridden. Simulation-scoped workflows are left
 * untouched so a legacy autonomous run still in flight keeps its own keep-alive contract.
 *
 * <p>Idempotent (a re-run matches zero rows) and lock-light (a targeted UPDATE on a small subset).
 */
@Component
public class V6_20260808200000000__Heal_legacy_autonomous_scenario_workflows
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "UPDATE workflows SET workflow_keep_alive = false, workflow_timeout_enabled = true"
              + " WHERE workflow_status = 'TEMPLATE'"
              + " AND workflow_scenario_id IS NOT NULL"
              + " AND workflow_keep_alive = true;");
    }
  }
}
