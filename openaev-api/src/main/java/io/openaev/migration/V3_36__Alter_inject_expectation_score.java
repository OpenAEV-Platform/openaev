package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_36__Alter_inject_expectation_score extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement select = connection.createStatement();
    select.execute(
        "ALTER TABLE injects_expectations ALTER COLUMN inject_expectation_expected_score SET NOT NULL,\n"
            + "ALTER COLUMN inject_expectation_expected_score SET DEFAULT 100;");
  }
}
