package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds the nullable {@code workflow_scope_rule_value_label} column to {@code workflow_scope_rules}
 * and backfills it for existing ASSET / ASSET_GROUP rules.
 *
 * <p>A scope rule stores only the referenced asset / asset-group id; the display name shown in the
 * UI was otherwise resolved against the live inventory. Once the asset / group is deleted the live
 * lookup returns nothing, so a past simulation's scope rendered a permanent "Loading..."
 * placeholder even though the immutable per-run reference was intact. Snapshotting the name at
 * create / update time (and here, for pre-existing rows) lets the UI keep showing a meaningful
 * label after deletion.
 *
 * <p>The backfill is tenant-scoped: it joins each rule through its workflow to the owning
 * simulation / scenario tenant and only copies names from assets of that same tenant, so it can
 * never leak another tenant's asset name into a rule (Hibernate's tenant filter does not apply to
 * the raw JDBC used here).
 *
 * <p>Additive, idempotent and lock-light: a nullable {@code ADD COLUMN} is metadata-only on
 * PostgreSQL 11+ (no table rewrite), {@code IF NOT EXISTS} makes re-running a no-op, and the
 * backfill only sets rows whose label is still {@code NULL}.
 */
@Component
public class V6_20260804240000000__Add_workflow_scope_rule_value_label extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "ALTER TABLE workflow_scope_rules "
              + "ADD COLUMN IF NOT EXISTS workflow_scope_rule_value_label text;");

      // PostgreSQL forbids referencing the UPDATE target (r) inside a JOIN ... ON in the FROM
      // clause; the target may only be joined to the FROM tables through the WHERE clause. So the
      // workflow is comma-joined and the exercise/scenario tenants hang off the workflow via LEFT
      // JOINs, while r is correlated in WHERE.
      statement.execute(
          "UPDATE workflow_scope_rules r "
              + "SET workflow_scope_rule_value_label = a.asset_name "
              + "FROM assets a, workflows w "
              + "LEFT JOIN exercises e ON e.exercise_id = w.workflow_simulation_id "
              + "LEFT JOIN scenarios s ON s.scenario_id = w.workflow_scenario_id "
              + "WHERE w.workflow_id = r.workflow_id "
              + "AND r.workflow_scope_rule_source = 'ASSET' "
              + "AND r.workflow_scope_rule_value_label IS NULL "
              + "AND a.asset_id = r.workflow_scope_rule_value "
              + "AND a.tenant_id = COALESCE(e.tenant_id, s.tenant_id);");

      statement.execute(
          "UPDATE workflow_scope_rules r "
              + "SET workflow_scope_rule_value_label = ag.asset_group_name "
              + "FROM asset_groups ag, workflows w "
              + "LEFT JOIN exercises e ON e.exercise_id = w.workflow_simulation_id "
              + "LEFT JOIN scenarios s ON s.scenario_id = w.workflow_scenario_id "
              + "WHERE w.workflow_id = r.workflow_id "
              + "AND r.workflow_scope_rule_source = 'ASSET_GROUP' "
              + "AND r.workflow_scope_rule_value_label IS NULL "
              + "AND ag.asset_group_id = r.workflow_scope_rule_value "
              + "AND ag.tenant_id = COALESCE(e.tenant_id, s.tenant_id);");
    }
  }
}
