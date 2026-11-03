package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/** What a history entry changed: fields before and after, merge choices, reasons. */
@Component
public class V6_20261027101300000__Content_history_details extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "ALTER TABLE content_history ADD COLUMN IF NOT EXISTS content_history_details JSONB;");
    }
  }
}
