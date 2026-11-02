package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Payload versioning (Task 5): an edit of an approved payload that needs approval is stored as a
 * pending version, while the payload keeps running its approved content. No backfill: the current
 * content of an existing payload is its version 1, held by the payload row itself.
 */
@Component
public class V6_20261009120000000__Add_payload_versions extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.executeUpdate(
          """
          CREATE TABLE IF NOT EXISTS payload_versions (
            payload_version_id VARCHAR(255) NOT NULL PRIMARY KEY,
            tenant_id VARCHAR(255) NOT NULL REFERENCES tenants(tenant_id) ON DELETE CASCADE,
            payload_id VARCHAR(255) NOT NULL REFERENCES payloads(payload_id) ON DELETE CASCADE,
            payload_version_number INTEGER NOT NULL,
            payload_version_status VARCHAR(32) NOT NULL,
            payload_version_origin VARCHAR(32) NOT NULL,
            payload_version_snapshot JSONB NOT NULL,
            payload_version_fingerprint VARCHAR(64) NOT NULL,
            payload_version_author VARCHAR(255) REFERENCES users(user_id) ON DELETE SET NULL,
            payload_version_author_name VARCHAR(255),
            payload_version_decider VARCHAR(255) REFERENCES users(user_id) ON DELETE SET NULL,
            payload_version_decider_name VARCHAR(255),
            payload_version_comment TEXT,
            payload_version_created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
            payload_version_decided_at TIMESTAMP WITH TIME ZONE
          );
          """);
      statement.executeUpdate(
          "CREATE INDEX IF NOT EXISTS idx_payload_versions_tenant ON payload_versions(tenant_id);");
      statement.executeUpdate(
          """
          CREATE INDEX IF NOT EXISTS idx_payload_versions_payload_number
            ON payload_versions(payload_id, payload_version_number DESC);
          """);
      // At most one pending version per payload.
      statement.executeUpdate(
          """
          CREATE UNIQUE INDEX IF NOT EXISTS uq_payload_versions_one_pending
            ON payload_versions(payload_id) WHERE payload_version_status = 'PENDING';
          """);
      statement.executeUpdate(
          "CREATE INDEX IF NOT EXISTS idx_payload_versions_author ON payload_versions(payload_version_author);");
      statement.executeUpdate(
          "CREATE INDEX IF NOT EXISTS idx_payload_versions_decider ON payload_versions(payload_version_decider);");

      // Lists and filters show "new version pending" without joining the versions.
      statement.executeUpdate(
          """
          ALTER TABLE payloads
            ADD COLUMN IF NOT EXISTS payload_pending_version BOOLEAN NOT NULL DEFAULT FALSE;
          """);

      // An approval of a pending version records which version it applied.
      statement.executeUpdate(
          """
          ALTER TABLE payload_approvals
            ADD COLUMN IF NOT EXISTS payload_approval_version VARCHAR(255)
              REFERENCES payload_versions(payload_version_id) ON DELETE SET NULL;
          """);
      statement.executeUpdate(
          "CREATE INDEX IF NOT EXISTS idx_payload_approvals_version ON payload_approvals(payload_approval_version);");
    }
  }
}
