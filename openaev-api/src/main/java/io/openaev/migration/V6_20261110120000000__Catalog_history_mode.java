package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V6_20261110120000000__Catalog_history_mode extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      // Every change recorded so far was made by a user.
      statement.execute(
          "ALTER TABLE catalog_history ADD COLUMN IF NOT EXISTS catalog_history_mode VARCHAR(255)"
              + " NOT NULL DEFAULT 'MANUAL';");
      statement.execute(
          "ALTER TABLE catalog_history ALTER COLUMN catalog_history_mode DROP DEFAULT;");
    }
  }
}
