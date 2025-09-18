package io.openbas.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_26__Modify_detection_remediation extends BaseJavaMigration {
  @Override
  public void migrate(Context context) throws Exception {
    try (Statement select = context.getConnection().createStatement()) {
      select.execute(
          """
                            ALTER TABLE detection_remediations
                              ADD COLUMN detection_remediation_ai_rule_creation_date timestamp DEFAULT NULL ;
                            """);
    }
  }
}
