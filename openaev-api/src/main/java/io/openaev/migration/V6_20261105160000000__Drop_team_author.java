package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V6_20261105160000000__Drop_team_author extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute("ALTER TABLE payloads DROP COLUMN IF EXISTS payload_author_team;");
      statement.execute(
          "ALTER TABLE injectors_contracts DROP COLUMN IF EXISTS injector_contract_author_team;");
    }
  }
}
