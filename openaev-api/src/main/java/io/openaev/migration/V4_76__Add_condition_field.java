package io.openaev.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_76__Add_condition_field extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    var statement = context.getConnection().createStatement();
    statement.execute("ALTER TABLE conditions ADD COLUMN condition_field VARCHAR(255);");
  }
}
