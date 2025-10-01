package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_43__Expectation_audience extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Statement select = context.getConnection().createStatement();
    // Add audience relation
    select.execute(
        "ALTER TABLE injects_expectations_executions ADD audience_id varchar(256) NOT NULL;");
    select.execute(
        "ALTER TABLE injects_expectations_executions "
            + "ADD CONSTRAINT fk_expectations_audience "
            + "FOREIGN KEY (audience_id) REFERENCES audiences(audience_id) "
            + "ON DELETE CASCADE ;");
    // Make user relation nullable
    select.execute("ALTER TABLE injects_expectations_executions ALTER user_id DROP NOT NULL;");
  }
}
