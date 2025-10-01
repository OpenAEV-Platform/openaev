package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_8__Cleanup_model extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Statement select = context.getConnection().createStatement();
    // User
    select.execute("ALTER TABLE users DROP column user_login;");
    // Exercise
    select.execute("ALTER TABLE exercises DROP column exercise_latitude;");
    select.execute("ALTER TABLE exercises DROP column exercise_longitude;");
    select.execute("ALTER TABLE exercises DROP column exercise_type;");
    select.execute(
        "ALTER TABLE exercises RENAME COLUMN exercise_mail_expediteur TO exercise_mail_from;");
  }
}
