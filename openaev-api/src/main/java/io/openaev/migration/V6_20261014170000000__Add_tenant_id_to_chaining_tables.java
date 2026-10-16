package io.openaev.migration;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;

import java.sql.Statement;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Gives the chaining tables their own {@code tenant_id}, so that each row can be scoped on its own
 * column instead of through its workflow's owner.
 *
 * <p>Backfill, parent first:
 *
 * <ul>
 *   <li>{@code workflows} from its simulation or its scenario. {@code
 *       chk_workflow_simulation_or_scenario} guarantees exactly one of the two is set, so both are
 *       read.
 *   <li>Every child table from its workflow.
 *   <li>A condition with no workflow from a step it is linked to, then from the step it comes from,
 *       then from its parent condition, level by level.
 *   <li>A row reachable from no parent at all goes to the default tenant, and is counted in the
 *       log.
 * </ul>
 *
 * <p>The column is left with no default: a row written without a tenant fails on {@code NOT NULL}
 * instead of landing silently in the default tenant.
 *
 * <p>{@code conditions_steps} and {@code workflow_state_entries} get no column: neither is ever the
 * root of a query, both are always reached through a parent that carries one.
 */
@Slf4j
@Component
public class V6_20261014170000000__Add_tenant_id_to_chaining_tables extends BaseJavaMigration {

  public static final List<String> TENANT_TABLES =
      List.of(
          "workflows",
          "steps",
          "conditions",
          "steps_delay_queue",
          "workflow_scope_rules",
          "scope_variables",
          "workflow_states");

  /** Child table to the column that references its workflow. */
  private static final Map<String, String> WORKFLOW_COLUMNS =
      Map.of(
          "steps", "step_workflow_id",
          "conditions", "condition_workflow_id",
          "steps_delay_queue", "steps_delay_queue_workflow_run_id",
          "workflow_scope_rules", "workflow_id",
          "scope_variables", "scope_variable_workflow",
          "workflow_states", "workflow_execution_id");

  @Override
  public void migrate(Context context) throws Exception {
    // Table and column names below are constants of this class, never input.
    try (Statement statement = context.getConnection().createStatement()) {
      for (String table : TENANT_TABLES) {
        statement.execute(
            "ALTER TABLE " + table + " ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(255)");
      }

      statement.executeUpdate(
          "UPDATE workflows w SET tenant_id = e.tenant_id FROM exercises e"
              + " WHERE w.tenant_id IS NULL AND w.workflow_simulation_id = e.exercise_id");
      statement.executeUpdate(
          "UPDATE workflows w SET tenant_id = s.tenant_id FROM scenarios s"
              + " WHERE w.tenant_id IS NULL AND w.workflow_scenario_id = s.scenario_id");

      for (Map.Entry<String, String> child : WORKFLOW_COLUMNS.entrySet()) {
        statement.executeUpdate(
            "UPDATE "
                + child.getKey()
                + " c SET tenant_id = w.tenant_id FROM workflows w WHERE c.tenant_id IS NULL AND c."
                + child.getValue()
                + " = w.workflow_id");
      }

      backfillConditionsWithoutWorkflow(statement);

      for (String table : TENANT_TABLES) {
        int orphans =
            statement.executeUpdate(
                "UPDATE "
                    + table
                    + " SET tenant_id = '"
                    + DEFAULT_TENANT_UUID
                    + "' WHERE tenant_id IS NULL");
        if (orphans > 0) {
          log.warn(
              "{} row(s) of {} have no parent to take a tenant from and were attributed to the"
                  + " default tenant",
              orphans,
              table);
        }
        statement.execute("ALTER TABLE " + table + " ALTER COLUMN tenant_id SET NOT NULL");
        statement.execute(
            "DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_"
                + table
                + "_tenant_id') THEN ALTER TABLE "
                + table
                + " ADD CONSTRAINT fk_"
                + table
                + "_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id)"
                + " ON DELETE CASCADE; END IF; END $$");
        statement.execute(
            "CREATE INDEX IF NOT EXISTS idx_" + table + "_tenant_id ON " + table + "(tenant_id)");
      }
    }
  }

  /**
   * {@code condition_workflow_id} is nullable: a condition can be linked to its workflow only
   * through a step, or only through its parent.
   */
  private void backfillConditionsWithoutWorkflow(Statement statement) throws Exception {
    statement.executeUpdate(
        "UPDATE conditions c SET tenant_id = s.tenant_id FROM conditions_steps cs"
            + " JOIN steps s ON s.step_id = cs.step_id"
            + " WHERE c.tenant_id IS NULL AND cs.condition_id = c.condition_id");
    statement.executeUpdate(
        "UPDATE conditions c SET tenant_id = s.tenant_id FROM steps s"
            + " WHERE c.tenant_id IS NULL AND c.step_from_id = s.step_id");
    // One tree level per pass; a pass that attributes nothing means no attributed parent is left.
    int attributed;
    do {
      attributed =
          statement.executeUpdate(
              "UPDATE conditions c SET tenant_id = p.tenant_id FROM conditions p"
                  + " WHERE c.tenant_id IS NULL AND c.condition_parent_id = p.condition_id"
                  + " AND p.tenant_id IS NOT NULL");
    } while (attributed > 0);
  }

  /* ROLLBACK
   ALTER TABLE workflows DROP COLUMN IF EXISTS tenant_id;
   ALTER TABLE steps DROP COLUMN IF EXISTS tenant_id;
   ALTER TABLE conditions DROP COLUMN IF EXISTS tenant_id;
   ALTER TABLE steps_delay_queue DROP COLUMN IF EXISTS tenant_id;
   ALTER TABLE workflow_scope_rules DROP COLUMN IF EXISTS tenant_id;
   ALTER TABLE scope_variables DROP COLUMN IF EXISTS tenant_id;
   ALTER TABLE workflow_states DROP COLUMN IF EXISTS tenant_id;
   -- Dropping the column drops its foreign key and index. Only valid together with a revert of the
   -- entity mappings, which write the column on every insert.
  */
}
