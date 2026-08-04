package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds a snapshot label column to workflow scope rules so an asset/asset group's display name is
 * preserved even after the referenced asset/asset group is later deleted.
 *
 * <p>Backfills existing ASSET/ASSET_GROUP rules from the currently live asset/asset group names
 * where still resolvable; rules whose referenced asset has already been deleted keep a {@code NULL}
 * label (name unrecoverable), and the frontend falls back to a generic placeholder for those.
 *
 * <p>The backfill resolves the owning tenant through the rule's workflow (a workflow belongs to
 * either a simulation or a scenario, both tenant-scoped) and only copies a name when the referenced
 * asset/asset group lives in that same tenant: assets are tenant-scoped, and raw SQL bypasses the
 * Hibernate tenant filter, so an unconstrained id join could copy (and later expose through the
 * API) another tenant's asset name if a rule ever referenced a foreign id.
 */
@Component
public class V6_20260804230000000__Add_workflow_scope_rule_value_label extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.addBatch(
          "ALTER TABLE workflow_scope_rules ADD COLUMN IF NOT EXISTS workflow_scope_rule_value_label varchar(255);");

      statement.addBatch(
          "UPDATE workflow_scope_rules r SET workflow_scope_rule_value_label = a.asset_name "
              + "FROM workflows w "
              + "LEFT JOIN exercises e ON e.exercise_id = w.workflow_simulation_id "
              + "LEFT JOIN scenarios s ON s.scenario_id = w.workflow_scenario_id "
              + "JOIN assets a ON a.tenant_id = COALESCE(e.tenant_id, s.tenant_id) "
              + "WHERE r.workflow_id = w.workflow_id "
              + "  AND r.workflow_scope_rule_source = 'ASSET' "
              + "  AND r.workflow_scope_rule_value = a.asset_id "
              + "  AND r.workflow_scope_rule_value_label IS NULL;");

      statement.addBatch(
          "UPDATE workflow_scope_rules r SET workflow_scope_rule_value_label = g.asset_group_name "
              + "FROM workflows w "
              + "LEFT JOIN exercises e ON e.exercise_id = w.workflow_simulation_id "
              + "LEFT JOIN scenarios s ON s.scenario_id = w.workflow_scenario_id "
              + "JOIN asset_groups g ON g.tenant_id = COALESCE(e.tenant_id, s.tenant_id) "
              + "WHERE r.workflow_id = w.workflow_id "
              + "  AND r.workflow_scope_rule_source = 'ASSET_GROUP' "
              + "  AND r.workflow_scope_rule_value = g.asset_group_id "
              + "  AND r.workflow_scope_rule_value_label IS NULL;");

      statement.executeBatch();
    }
  }
}
