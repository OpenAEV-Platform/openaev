package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V5_35__Add_indexing_last_id extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "ALTER TABLE indexing_status ADD COLUMN IF NOT EXISTS indexing_last_id TEXT NULL");
      // Force a one-time full reindex for expectations so that any rows that were previously
      // skipped (same-timestamp batch overflow before this fix) are picked up on next cycle.
      statement.execute(
          "UPDATE indexing_status SET indexing_last_id = NULL, indexing_status_indexing_date = '1970-01-01 00:00:00'"
              + " WHERE indexing_status_type = 'expectation-inject'");
    }
  }
}
