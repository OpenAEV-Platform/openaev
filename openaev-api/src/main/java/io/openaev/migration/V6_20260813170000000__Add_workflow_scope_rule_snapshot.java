package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds the execution-time scope snapshot: two nullable jsonb columns (launch + end) and the new
 * SECURITY_PLATFORM scope source / value type. No index (see ADR-006: snapshot columns are never
 * queried by content, and the read path is already served by idx_workflow_scope_rules_workflow_id).
 */
@Component
public class V6_20260813170000000__Add_workflow_scope_rule_snapshot extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement stmt = context.getConnection().createStatement()) {

      // -- New enum values (idempotent) --
      stmt.execute("ALTER TYPE scope_rule_source ADD VALUE IF NOT EXISTS 'SECURITY_PLATFORM';");
      stmt.execute(
          "ALTER TYPE scope_rule_value_type ADD VALUE IF NOT EXISTS 'SECURITY_PLATFORM_ID';");

      // -- Two immutable snapshot columns (launch + end) --
      stmt.execute(
          """
          ALTER TABLE workflow_scope_rules
            ADD COLUMN IF NOT EXISTS workflow_scope_rule_snapshot_start jsonb,
            ADD COLUMN IF NOT EXISTS workflow_scope_rule_snapshot_end   jsonb;
          """);
    }
  }
}
