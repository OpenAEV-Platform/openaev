package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_02__Exercise_enhancement extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Statement select = context.getConnection().createStatement();
    select.execute("ALTER TABLE exercises ADD exercise_category varchar(255);");
    select.execute("ALTER TABLE exercises ADD exercise_severity varchar(255);");
    select.execute("ALTER TABLE exercises ADD exercise_main_focus varchar(255);");
    select.execute("ALTER TABLE scenarios ADD scenario_external_reference varchar(255);");
  }
}
