package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V6_20261125100000000__Content_changes extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          DO $$ BEGIN IF to_regclass('catalog_history') IS NOT NULL THEN
            ALTER TABLE catalog_history RENAME TO content_changes;
            ALTER TABLE content_changes RENAME COLUMN catalog_history_id TO content_change_id;
            ALTER TABLE content_changes RENAME COLUMN catalog_history_type TO content_change_type;
            ALTER TABLE content_changes
              RENAME COLUMN catalog_history_repository TO content_change_repository;
            ALTER TABLE content_changes
              RENAME COLUMN catalog_history_external_id TO content_change_external_id;
            ALTER TABLE content_changes
              RENAME COLUMN catalog_history_version TO content_change_version;
            ALTER TABLE content_changes RENAME COLUMN catalog_history_action TO content_change_action;
            ALTER TABLE content_changes RENAME COLUMN catalog_history_mode TO content_change_mode;
            ALTER TABLE content_changes RENAME COLUMN catalog_history_user TO content_change_user;
            ALTER TABLE content_changes
              RENAME COLUMN catalog_history_created_at TO content_change_created_at;
            ALTER TABLE content_changes RENAME CONSTRAINT catalog_history_pkey TO content_changes_pkey;
            ALTER TABLE content_changes
              RENAME CONSTRAINT catalog_history_tenant_id_fkey TO content_changes_tenant_id_fkey;
            ALTER TABLE content_changes RENAME CONSTRAINT catalog_history_catalog_history_user_fkey
              TO content_changes_content_change_user_fkey;
            ALTER INDEX idx_catalog_history_content RENAME TO idx_content_changes_content;
            ALTER INDEX idx_catalog_history_user RENAME TO idx_content_changes_user;
          END IF; END $$;
          """);
      // Local content has no catalogue key: its rows are found by the id its versions share.
      statement.execute(
          """
          ALTER TABLE content_changes
            ADD COLUMN IF NOT EXISTS content_change_content_id VARCHAR(255),
            ALTER COLUMN content_change_repository DROP NOT NULL,
            ALTER COLUMN content_change_external_id DROP NOT NULL;
          CREATE INDEX IF NOT EXISTS idx_content_changes_content_id ON content_changes
            (tenant_id, content_change_type, content_change_content_id,
             content_change_created_at DESC);
          """);
    }
  }
}
