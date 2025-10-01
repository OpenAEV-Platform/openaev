package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_40__Modify_detection_remediation extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement select = context.getConnection().createStatement()) {
      select.execute(
          """
              CREATE TYPE author_enum AS ENUM ('HUMAN', 'AI', 'AI_OUTDATED');
              ALTER TABLE detection_remediations
                ADD COLUMN author_rule author_enum NOT NULL DEFAULT 'HUMAN';
              """);
    }
  }
}
