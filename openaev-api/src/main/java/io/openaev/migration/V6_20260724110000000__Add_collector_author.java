package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds an optional source-declared author override on collectors. When set, a collector's payloads
 * (and therefore their arsenal contracts) are attributed to this author organization instead of the
 * collector's display name. No backfill: existing collectors keep the name-based fallback.
 */
@Component
public class V6_20260724110000000__Add_collector_author extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
              ALTER TABLE collectors
              ADD COLUMN IF NOT EXISTS collector_author VARCHAR(255)
              """);
    }
  }
}

// -- ROLLBACK --
// ALTER TABLE collectors DROP COLUMN IF EXISTS collector_author;
