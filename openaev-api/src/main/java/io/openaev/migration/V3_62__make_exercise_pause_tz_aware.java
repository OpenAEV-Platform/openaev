package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_62__make_exercise_pause_tz_aware extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement statement = connection.createStatement();

    statement.execute(
        """
        ALTER TABLE exercises ADD COLUMN exercise_pause_date_tempwithtz TIMESTAMP WITH TIME ZONE;
        UPDATE exercises SET exercise_pause_date_tempwithtz = exercise_pause_date;
        ALTER TABLE exercises DROP COLUMN exercise_pause_date;
        ALTER TABLE exercises RENAME COLUMN exercise_pause_date_tempwithtz TO exercise_pause_date;
    """);
  }
}
