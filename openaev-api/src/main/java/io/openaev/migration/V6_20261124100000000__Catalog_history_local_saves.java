package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V6_20261124100000000__Catalog_history_local_saves extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      // Local content has no catalogue key: its rows are found by the id its versions share.
      statement.execute(
          "ALTER TABLE catalog_history"
              + " ADD COLUMN IF NOT EXISTS catalog_history_content_id VARCHAR(255),"
              + " ALTER COLUMN catalog_history_repository DROP NOT NULL,"
              + " ALTER COLUMN catalog_history_external_id DROP NOT NULL");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_catalog_history_content_id ON catalog_history"
              + " (tenant_id, catalog_history_type, catalog_history_content_id,"
              + " catalog_history_created_at DESC)");
    }
  }
}
