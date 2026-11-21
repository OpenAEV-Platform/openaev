package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Read-only marker on injector contracts and payloads. Both default to false, so every existing row
 * keeps its behaviour: nothing is read-only until it is marked.
 */
@Component
public class V6_20261121160000000__Coverage_scenario_safe_mode_and_approval
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
    }
  }
}
