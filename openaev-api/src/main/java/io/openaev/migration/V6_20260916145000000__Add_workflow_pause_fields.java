package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V6_20260916145000000__Add_workflow_pause_fields extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          ALTER TABLE workflows
          ADD COLUMN IF NOT EXISTS workflow_pause_at timestamp;
          """);

      statement.execute(
          """
          ALTER TABLE workflows
          ADD COLUMN IF NOT EXISTS workflow_pause_second bigint NOT NULL DEFAULT 0;
          """);
    }
  }
}
