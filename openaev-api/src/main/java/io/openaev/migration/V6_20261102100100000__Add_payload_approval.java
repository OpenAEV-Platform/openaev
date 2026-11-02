package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Payload approval on payload versions: each version row carries its own approval status, decided
 * once when the version is created, then by an approver.
 *
 * <ul>
 *   <li>{@code payloads.payload_approval_status}: every existing version is approved, so
 *       introducing approval blocks nothing already in use; the default then becomes PENDING
 *       (fail-closed).
 *   <li>{@code payload_approvals}: the approval history of each version.
 *   <li>{@code payload_identities.latest_approved_payload_id}: the highest approved, non-revoked
 *       version, which pickers and automatic selection use while a newer version waits.
 * </ul>
 */
@Component
public class V6_20261102100100000__Add_payload_approval extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.executeUpdate(
          """
          ALTER TABLE payloads
            ADD COLUMN IF NOT EXISTS payload_approval_status VARCHAR(32) NOT NULL DEFAULT 'APPROVED';
          """);
      statement.executeUpdate(
          "ALTER TABLE payloads ALTER COLUMN payload_approval_status SET DEFAULT 'PENDING';");
      statement.executeUpdate(
          """
          CREATE INDEX IF NOT EXISTS idx_payloads_tenant_approval_status
            ON payloads(tenant_id, payload_approval_status);
          """);

      // Approval history of a version (automatic approvals, decisions, supersessions).
      statement.executeUpdate(
          """
          CREATE TABLE IF NOT EXISTS payload_approvals (
            payload_approval_id VARCHAR(255) NOT NULL PRIMARY KEY,
            tenant_id VARCHAR(255) NOT NULL REFERENCES tenants(tenant_id) ON DELETE CASCADE,
            payload_id VARCHAR(255) NOT NULL REFERENCES payloads(payload_id) ON DELETE CASCADE,
            payload_approval_status VARCHAR(32) NOT NULL,
            payload_approval_origin VARCHAR(32) NOT NULL,
            payload_approval_automatic BOOLEAN NOT NULL DEFAULT FALSE,
            payload_approval_actor VARCHAR(255) REFERENCES users(user_id) ON DELETE SET NULL,
            payload_approval_actor_name VARCHAR(255),
            payload_approval_comment TEXT,
            payload_approval_created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
          );
          """);
      statement.executeUpdate(
          "CREATE INDEX IF NOT EXISTS idx_payload_approvals_tenant ON payload_approvals(tenant_id);");
      statement.executeUpdate(
          """
          CREATE INDEX IF NOT EXISTS idx_payload_approvals_payload_created
            ON payload_approvals(payload_id, payload_approval_created_at DESC);
          """);
      statement.executeUpdate(
          "CREATE INDEX IF NOT EXISTS idx_payload_approvals_actor ON payload_approvals(payload_approval_actor);");

      // One MIGRATION entry per existing version, so every history starts with how it became
      // approved. Idempotent: versions that already have an entry are skipped.
      statement.executeUpdate(
          """
          INSERT INTO payload_approvals (
            payload_approval_id, tenant_id, payload_id, payload_approval_status,
            payload_approval_origin, payload_approval_automatic, payload_approval_comment)
          SELECT gen_random_uuid()::text, p.tenant_id, p.payload_id, 'APPROVED', 'MIGRATION', TRUE,
                 'Approved when payload approval was introduced'
          FROM payloads p
          WHERE NOT EXISTS (SELECT 1 FROM payload_approvals a WHERE a.payload_id = p.payload_id);
          """);

      // Same rules as latest_payload_id: deferred, and deleting the version clears the pointer.
      statement.executeUpdate(
          """
          ALTER TABLE payload_identities
            ADD COLUMN IF NOT EXISTS latest_approved_payload_id VARCHAR(255);
          """);
      statement.execute(
          """
          DO $$
          BEGIN
            IF NOT EXISTS (
              SELECT 1 FROM pg_constraint WHERE conname = 'payload_identities_latest_approved_fk'
            ) THEN
              ALTER TABLE payload_identities
                ADD CONSTRAINT payload_identities_latest_approved_fk
                  FOREIGN KEY (latest_approved_payload_id, identity_id)
                  REFERENCES payloads (payload_id, identity_id)
                  ON DELETE SET NULL (latest_approved_payload_id)
                  DEFERRABLE INITIALLY DEFERRED;
            END IF;
          END $$;
          """);
      // Every version is approved at this point: the latest approved one is the latest one.
      statement.executeUpdate(
          """
          UPDATE payload_identities
            SET latest_approved_payload_id = latest_payload_id
            WHERE latest_approved_payload_id IS NULL;
          """);
    }
  }
}
