package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_75__Inject_expectation_result extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement select = connection.createStatement();
    // Migration the data
    select.execute(
        """
        ALTER TABLE injects_expectations ADD COLUMN inject_expectation_results json;
        UPDATE injects_expectations SET inject_expectation_results = json_build_array(json_build_object('result', inject_expectation_result));
        ALTER TABLE injects_expectations DROP COLUMN inject_expectation_result;

        UPDATE injects_expectations
        SET inject_expectation_type = 'PREVENTION'
        WHERE inject_expectation_type = 'TECHNICAL';
        """);
  }
}
