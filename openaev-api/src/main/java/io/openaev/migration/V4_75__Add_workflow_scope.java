package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_75__Add_workflow_scope extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement select = context.getConnection().createStatement()) {

      select.execute(
          """
              ALTER TABLE workflows
                ADD COLUMN IF NOT EXISTS workflow_scope JSONB;
          """);

      select.execute(
          """
              ALTER TABLE workflows
                ADD COLUMN IF NOT EXISTS workflow_timeout BIGINT;
          """);
    }
  }
}
