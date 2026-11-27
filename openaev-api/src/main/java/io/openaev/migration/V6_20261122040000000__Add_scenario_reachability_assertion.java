package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Reachability assertion flag on scenarios. Defaults to false, so every existing scenario keeps its
 * behaviour: nothing is an assertion until a user declares one.
 */
@Component
public class V6_20261122040000000__Add_scenario_reachability_assertion extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "ALTER TABLE scenarios"
              + " ADD COLUMN IF NOT EXISTS scenario_reachability_assertion BOOLEAN NOT NULL"
              + " DEFAULT false");
    }
  }
}
