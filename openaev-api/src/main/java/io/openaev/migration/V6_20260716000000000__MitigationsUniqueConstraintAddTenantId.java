package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Replaces the global unique constraint on {@code mitigation_external_id} with a tenant-scoped
 * composite unique constraint {@code (mitigation_external_id, tenant_id)}.
 *
 * <p>This allows two tenants to independently hold the same MITRE ATT&CK mitigation external ID
 * (e.g. {@code M1013}) without conflict, which is required for proper multi-tenant isolation.
 */
@Component
public class V6_20260716000000000__MitigationsUniqueConstraintAddTenantId
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement stmt = context.getConnection().createStatement()) {
      // Drop the old global unique index (no tenant scope)
      stmt.execute("DROP INDEX IF EXISTS mitigations_unique");

      // Add composite unique index scoped to tenant.
      // Note: PostgreSQL does not support ADD CONSTRAINT IF NOT EXISTS; use CREATE UNIQUE INDEX
      // IF NOT EXISTS instead, which is idempotent.
      stmt.execute(
          "CREATE UNIQUE INDEX IF NOT EXISTS uk_mitigations_external_id_tenant "
              + "ON mitigations (mitigation_external_id, tenant_id)");
    }
  }
}
