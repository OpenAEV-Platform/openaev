package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V6_20261007130000000__Add_payload_approval extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      // Every existing payload is approved: introducing approval must not block content that was
      // already in use. The default then becomes PENDING, so a write that never decides the status
      // is not trusted (fail-closed).
      statement.executeUpdate(
          """
          ALTER TABLE payloads
            ADD COLUMN IF NOT EXISTS payload_approval_status VARCHAR(32) NOT NULL DEFAULT 'APPROVED',
            ADD COLUMN IF NOT EXISTS payload_approved_fingerprint VARCHAR(64);
          """);
      statement.executeUpdate(
          "ALTER TABLE payloads ALTER COLUMN payload_approval_status SET DEFAULT 'PENDING';");
      statement.executeUpdate(
          """
          CREATE INDEX IF NOT EXISTS idx_payloads_tenant_approval_status
            ON payloads(tenant_id, payload_approval_status);
          """);

      // Approval history of a payload (status changes, automatic approvals, decisions).
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
            payload_approval_fingerprint VARCHAR(64),
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

      // One MIGRATION entry per existing payload, so every history starts with how it became
      // approved. Idempotent: payloads that already have an entry are skipped.
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
    }
  }
}
