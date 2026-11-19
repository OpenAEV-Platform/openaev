package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Payload drafts: versions not published yet, never usable. A draft belongs to a payload (its
 * identity), starts from a version (kept by number when that version is deleted) and, when it
 * reworks a rejected version, points to it. Its content is the JSON the editor saves.
 */
@Component
public class V6_20261119100000000__Add_payload_drafts extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.executeUpdate(
          """
          CREATE TABLE IF NOT EXISTS payload_drafts (
            payload_draft_id VARCHAR(255) NOT NULL PRIMARY KEY,
            tenant_id VARCHAR(255) NOT NULL REFERENCES tenants(tenant_id) ON DELETE CASCADE,
            payload_identity_id VARCHAR(255) NOT NULL
              REFERENCES payload_identities(identity_id) ON DELETE CASCADE,
            payload_draft_source VARCHAR(255) REFERENCES payloads(payload_id) ON DELETE SET NULL,
            payload_draft_source_version VARCHAR(64),
            payload_draft_rework VARCHAR(255) REFERENCES payloads(payload_id) ON DELETE CASCADE,
            payload_draft_content TEXT NOT NULL,
            payload_draft_created_by VARCHAR(255) REFERENCES users(user_id) ON DELETE SET NULL,
            payload_draft_updated_by VARCHAR(255) REFERENCES users(user_id) ON DELETE SET NULL,
            payload_draft_updated_by_name VARCHAR(255),
            payload_draft_created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
            payload_draft_updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
          );
          """);
      statement.executeUpdate(
          """
          CREATE INDEX IF NOT EXISTS idx_payload_drafts_identity
            ON payload_drafts(payload_identity_id, payload_draft_updated_at DESC);
          """);
      // One draft at most reworks a rejected version.
      statement.executeUpdate(
          """
          CREATE UNIQUE INDEX IF NOT EXISTS uq_payload_drafts_rework
            ON payload_drafts(payload_draft_rework) WHERE payload_draft_rework IS NOT NULL;
          """);
      statement.executeUpdate(
          "CREATE INDEX IF NOT EXISTS idx_payload_drafts_tenant ON payload_drafts(tenant_id);");
      statement.executeUpdate(
          "CREATE INDEX IF NOT EXISTS idx_payload_drafts_source ON payload_drafts(payload_draft_source);");
      statement.executeUpdate(
          "CREATE INDEX IF NOT EXISTS idx_payload_drafts_created_by ON payload_drafts(payload_draft_created_by);");
      statement.executeUpdate(
          "CREATE INDEX IF NOT EXISTS idx_payload_drafts_updated_by ON payload_drafts(payload_draft_updated_by);");
    }
  }
}
