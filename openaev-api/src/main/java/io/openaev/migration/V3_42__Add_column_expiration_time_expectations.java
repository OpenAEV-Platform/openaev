package io.openaev.migration;

import static io.openaev.expectation.ExpectationPropertiesConfig.DEFAULT_HUMAN_EXPECTATION_EXPIRATION_TIME;
import static io.openaev.expectation.ExpectationPropertiesConfig.DEFAULT_TECHNICAL_EXPECTATION_EXPIRATION_TIME;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_42__Add_column_expiration_time_expectations extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement statement = connection.createStatement();

    statement.execute("ALTER TABLE injects_expectations ADD inject_expiration_time bigint;");
    statement.execute(
        "UPDATE injects_expectations SET inject_expiration_time = "
            + DEFAULT_TECHNICAL_EXPECTATION_EXPIRATION_TIME
            + " "
            + "WHERE inject_expectation_type = 'DETECTION' OR inject_expectation_type = 'PREVENTION';");

    statement.execute(
        "UPDATE injects_expectations SET inject_expiration_time = "
            + DEFAULT_HUMAN_EXPECTATION_EXPIRATION_TIME
            + " "
            + "WHERE inject_expectation_type = 'MANUAL' OR inject_expectation_type = 'CHALLENGE' "
            + "OR inject_expectation_type = 'ARTICLE' OR inject_expectation_type = 'DOCUMENT' OR inject_expectation_type = 'TEXT';");

    statement.execute(
        "ALTER TABLE injects_expectations ALTER COLUMN inject_expiration_time SET NOT NULL;");
  }
}
