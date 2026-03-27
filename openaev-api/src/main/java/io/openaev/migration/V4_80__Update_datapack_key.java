package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_80__Update_datapack_key extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
                        ALTER TABLE datapacks
                        DROP CONSTRAINT datapacks_pkey;
                        """);
      statement.execute(
          """
                        ALTER TABLE datapacks
                        ADD CONSTRAINT datapacks_pkey UNIQUE (datapack_id, tenant_id);
                        """);
      statement.execute(
          """
                        DELETE FROM parameters WHERE parameter_key = 'starterpack';
                        """);
      statement.execute(
          """
              ALTER TABLE scenarios_teams
              DROP CONSTRAINT IF EXISTS scenario_id_fk;
              """);
      statement.execute(
          """
              ALTER TABLE scenarios_teams
              DROP CONSTRAINT IF EXISTS team_id_fk;
              """);
      statement.execute(
          """
              ALTER TABLE scenarios_teams
              ADD CONSTRAINT scenario_id_fk
              FOREIGN KEY (scenario_id) REFERENCES scenarios (scenario_id) ON DELETE CASCADE;
              """);
      statement.execute(
          """
              ALTER TABLE scenarios_teams
              ADD CONSTRAINT team_id_fk
              FOREIGN KEY (team_id) REFERENCES teams (team_id) ON DELETE CASCADE;
              """);
    }
  }
}
