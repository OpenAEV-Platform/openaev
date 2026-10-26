package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V6_20261026120000000__Drop_scenario_and_simulation_sender_email
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.executeUpdate("ALTER TABLE scenarios DROP COLUMN IF EXISTS scenario_mail_from;");
      statement.executeUpdate("ALTER TABLE exercises DROP COLUMN IF EXISTS exercise_mail_from;");
    }
  }
}
