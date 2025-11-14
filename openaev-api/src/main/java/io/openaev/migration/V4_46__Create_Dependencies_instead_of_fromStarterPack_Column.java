package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_46__Create_Dependencies_instead_of_fromStarterPack_Column
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement select = context.getConnection().createStatement()) {
      select.execute(
          """
                  ALTER TABLE scenarios
                  ADD COLUMN IF NOT EXISTS scenario_dependencies text[] DEFAULT '{}';
              """);
      select.execute(
          """
                  UPDATE scenarios
                  SET scenario_dependencies = array_append(scenario_dependencies, 'STARTERPACK')
                  WHERE
                      from_starter_pack = TRUE AND
                      (NOT ('STARTERPACK' = ANY(scenario_dependencies)) OR scenario_dependencies IS NULL);
              """);
      select.execute(
          """
                  ALTER TABLE scenarios
                  DROP COLUMN IF EXISTS from_starter_pack;
              """);
    }
  }
}

// -- Rollback
// BEGIN;
//
// ALTER TABLE scenarios
// ADD COLUMN IF NOT EXISTS from_starter_pack boolean DEFAULT false;
//
// UPDATE scenarios
// SET from_starter_pack = TRUE
// WHERE 'STARTERPACK' = ANY(scenario_dependencies);
//
// ALTER TABLE scenarios
// DROP COLUMN IF EXISTS scenario_dependencies;
//
// COMMIT;
