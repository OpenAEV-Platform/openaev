package io.openaev.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V5_10__Add_index_teams_contextual extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    context
        .getConnection()
        .createStatement()
        .execute("CREATE INDEX IF NOT EXISTS idx_teams_contextual ON teams (team_contextual);");
  }
}
