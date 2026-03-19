package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_56__Add_type_affinity extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          ALTER TABLE security_coverages
          ADD COLUMN security_coverage_type_affinity VARCHAR(255);
          ALTER TABLE scenarios
          ADD COLUMN scenario_type_affinity VARCHAR(255);
          """);
    }
  }
}
