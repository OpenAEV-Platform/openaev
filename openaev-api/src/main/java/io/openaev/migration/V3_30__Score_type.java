package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_30__Score_type extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Statement select = context.getConnection().createStatement();
    select.execute("ALTER TABLE challenges alter column challenge_score type DOUBLE PRECISION;");
    select.execute(
        "ALTER TABLE injects_expectations alter column inject_expectation_score type DOUBLE PRECISION;");
    select.execute(
        "ALTER TABLE injects_expectations alter column inject_expectation_expected_score type DOUBLE PRECISION;");
  }
}
