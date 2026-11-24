package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V6_20261124110000000__Rename_catalog_history_to_content_changes
    extends BaseJavaMigration {

  private static final String[] COLUMNS = {
    "id",
    "type",
    "repository",
    "external_id",
    "content_id",
    "version",
    "action",
    "mode",
    "user",
    "created_at"
  };

  @Override
  public void migrate(Context context) throws Exception {
    StringBuilder renames = new StringBuilder();
    for (String column : COLUMNS) {
      renames
          .append("ALTER TABLE content_changes RENAME COLUMN catalog_history_")
          .append(column)
          .append(" TO content_change_")
          .append(column)
          .append(";");
    }
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "DO $$ BEGIN IF to_regclass('catalog_history') IS NOT NULL THEN"
              + " ALTER TABLE catalog_history RENAME TO content_changes;"
              + renames
              + " ALTER TABLE content_changes RENAME CONSTRAINT catalog_history_pkey"
              + " TO content_changes_pkey;"
              + " ALTER TABLE content_changes RENAME CONSTRAINT catalog_history_tenant_id_fkey"
              + " TO content_changes_tenant_id_fkey;"
              + " ALTER TABLE content_changes RENAME CONSTRAINT"
              + " catalog_history_catalog_history_user_fkey TO content_changes_content_change_user_fkey;"
              + " END IF; END $$;");
      statement.execute(
          "ALTER INDEX IF EXISTS idx_catalog_history_content RENAME TO idx_content_changes_content");
      statement.execute(
          "ALTER INDEX IF EXISTS idx_catalog_history_user RENAME TO idx_content_changes_user");
      statement.execute(
          "ALTER INDEX IF EXISTS idx_catalog_history_content_id"
              + " RENAME TO idx_content_changes_content_id");
    }
  }
}
