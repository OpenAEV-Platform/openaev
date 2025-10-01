package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_84__Scenario_recurrence_end_date extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement select = connection.createStatement();
    // Add end date recurrence to scenario
    select.executeUpdate(
        """
        ALTER TABLE scenarios ADD COLUMN scenario_recurrence_end timestamp;
        """);
  }
}
