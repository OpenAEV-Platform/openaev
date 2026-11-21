package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Read-only marker on injector contracts and payloads, and safe mode, approval and asset group run
 * order on scenarios. Every default keeps the behaviour of existing rows: nothing is read-only,
 * nothing is in safe mode and nothing waits for an approval.
 */
@Component
public class V6_20261121140000000__Coverage_scenario_safe_mode_and_approval
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "ALTER TABLE injectors_contracts"
              + " ADD COLUMN IF NOT EXISTS injector_contract_read_only BOOLEAN NOT NULL DEFAULT false");
      statement.execute(
          "ALTER TABLE payloads"
              + " ADD COLUMN IF NOT EXISTS payload_read_only BOOLEAN NOT NULL DEFAULT false");
      statement.execute(
          """
          ALTER TABLE scenarios
            ADD COLUMN IF NOT EXISTS scenario_safe_mode BOOLEAN NOT NULL DEFAULT false,
            ADD COLUMN IF NOT EXISTS scenario_approval_status VARCHAR(32) NOT NULL DEFAULT 'NOT_REQUIRED',
            ADD COLUMN IF NOT EXISTS scenario_approved_by VARCHAR(255)
              REFERENCES users(user_id) ON DELETE SET NULL,
            ADD COLUMN IF NOT EXISTS scenario_approved_at TIMESTAMP WITH TIME ZONE,
            ADD COLUMN IF NOT EXISTS scenario_asset_group_order TEXT[] NOT NULL DEFAULT '{}';
          """);
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_scenarios_approval_status"
              + " ON scenarios(scenario_approval_status);");
    }
  }
}
