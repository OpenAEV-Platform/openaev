package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_86__Enforce_constraint_grant_scenario extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      // Remove duplicate values
      statement.execute(
          """
          DELETE FROM grants g
          USING grants g2
          WHERE g.ctid < g2.ctid
             AND g.grant_group = g2.grant_group
             AND g.grant_scenario = g2.grant_scenario
             AND g.grant_name = g2.grant_name;
          """);
      // Rename existing index
      statement.execute(
          """
                ALTER INDEX "grant" RENAME TO grant_exercise;
              """);
      // Create index for grant X scenario
      statement.execute(
          """
              CREATE UNIQUE INDEX IF NOT EXISTS grant_scenario
              ON grants (grant_group, grant_scenario, grant_name);
          """);
    }
  }
}
