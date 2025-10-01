package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_7__Model_adaptation extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Statement select = context.getConnection().createStatement();
    // Exercise
    select.execute("ALTER TABLE exercises ALTER COLUMN exercise_description DROP NOT NULL ");
    // User
    select.execute("ALTER TABLE users ALTER COLUMN user_firstname DROP NOT NULL ");
    select.execute("ALTER TABLE users ALTER COLUMN user_lastname DROP NOT NULL ");
    // Group
    select.execute("ALTER TABLE groups ADD group_description text;");
  }
}
