package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/** Tenant revocations (F34): a tenant admin revokes one of its payloads. */
@Component
public class V6_20261027100400000__Tenant_content_revocations extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          ALTER TABLE content_revocations
            ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(255)
              REFERENCES tenants(tenant_id) ON DELETE CASCADE,
            ADD COLUMN IF NOT EXISTS content_revocation_payload VARCHAR(255)
              REFERENCES payloads(payload_id) ON DELETE CASCADE,
            ADD COLUMN IF NOT EXISTS content_revocation_revoked_by VARCHAR(255)
              REFERENCES users(user_id) ON DELETE SET NULL,
            ADD COLUMN IF NOT EXISTS content_revocation_payload_previous_status VARCHAR(255),
            ALTER COLUMN content_revocation_external_id DROP NOT NULL,
            ALTER COLUMN content_revocation_version DROP NOT NULL,
            ALTER COLUMN content_revocation_list_serial DROP NOT NULL;
          """);
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_content_revocations_tenant ON content_revocations(tenant_id);");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_content_revocations_payload ON content_revocations(content_revocation_payload);");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_content_revocations_revoked_by ON content_revocations(content_revocation_revoked_by);");
      statement.execute(
          """
          CREATE UNIQUE INDEX IF NOT EXISTS content_revocations_payload_active_unique
            ON content_revocations (content_revocation_payload)
            WHERE content_revocation_lifted_at IS NULL;
          """);
      // A row comes either from the revocation list (no tenant) or from a tenant admin.
      statement.execute(
          "ALTER TABLE content_revocations DROP CONSTRAINT IF EXISTS content_revocations_scope_check;");
      statement.execute(
          """
          ALTER TABLE content_revocations ADD CONSTRAINT content_revocations_scope_check CHECK (
            (tenant_id IS NULL
              AND content_revocation_payload IS NULL
              AND content_revocation_external_id IS NOT NULL
              AND content_revocation_version IS NOT NULL
              AND content_revocation_list_serial IS NOT NULL)
            OR (tenant_id IS NOT NULL AND content_revocation_payload IS NOT NULL));
          """);
    }
  }
}
