package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Payload drafts of a new action: a draft saved from the Create form belongs to no payload yet, so
 * its payload identity is null until it is published as the first version.
 */
@Component
public class V6_20261124100000000__Payload_drafts_for_new_actions extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.executeUpdate(
          "ALTER TABLE payload_drafts ALTER COLUMN payload_identity_id DROP NOT NULL;");
      // The drafts of new actions of a tenant, last changed first (Create form, My changes).
      statement.executeUpdate(
          """
          CREATE INDEX IF NOT EXISTS idx_payload_drafts_new_actions
            ON payload_drafts(tenant_id, payload_draft_updated_at DESC)
            WHERE payload_identity_id IS NULL;
          """);
    }
  }
}
