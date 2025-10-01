package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_98__Scenario_enhancement extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Statement select = context.getConnection().createStatement();
    select.execute("ALTER TABLE scenarios ADD scenario_category varchar(255);");
    select.execute("ALTER TABLE scenarios ADD scenario_severity varchar(255);");
    select.execute("ALTER TABLE scenarios ADD scenario_main_focus varchar(255);");
    select.execute("ALTER TABLE scenario_exercise RENAME TO scenarios_exercises;");
  }
}
