package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V6_20261113100000000__Payload_revocations extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          CREATE TABLE IF NOT EXISTS payload_revocations (
            payload_revocation_id VARCHAR(255) NOT NULL PRIMARY KEY,
            tenant_id VARCHAR(255) NOT NULL REFERENCES tenants (tenant_id) ON DELETE CASCADE,
            payload_id VARCHAR(255) NOT NULL UNIQUE
              REFERENCES payloads (payload_id) ON DELETE CASCADE,
            payload_revocation_reason TEXT NOT NULL,
            payload_revocation_user VARCHAR(255) REFERENCES users (user_id) ON DELETE SET NULL,
            payload_revocation_created_at TIMESTAMPTZ NOT NULL DEFAULT now()
          );
          """);
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_payload_revocations_tenant"
              + " ON payload_revocations (tenant_id);");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_payload_revocations_user"
              + " ON payload_revocations (payload_revocation_user);");
      // Revoking needed the delete right until now: roles that have it keep revoking.
      statement.execute(
          """
          INSERT INTO roles_capabilities (role_id, capability)
          SELECT role_id, 'REVOKE_THREAT_ARSENALS' FROM roles_capabilities
          WHERE capability = 'DELETE_THREAT_ARSENALS'
          ON CONFLICT DO NOTHING;
          """);
    }
  }
}
