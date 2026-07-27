package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds {@code scenario_default_kill_chain} and {@code exercise_default_kill_chain}: the kill chain
 * (by name, e.g. "mitre-attack") the overview's kill chain results section displays first. Null
 * means automatic (ATT&CK first); the user's own selection, remembered in local storage, still
 * overrides the default. Additive and idempotent.
 */
@Component
public class V6_20260726210000000__Add_default_kill_chain_to_scenarios_and_exercises
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "ALTER TABLE scenarios ADD COLUMN IF NOT EXISTS scenario_default_kill_chain varchar(255);");
      statement.execute(
          "ALTER TABLE exercises ADD COLUMN IF NOT EXISTS exercise_default_kill_chain varchar(255);");
    }
  }
}
