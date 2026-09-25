package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * TEMPORARY migration (#294): removes ACCESS_DOCUMENTS (and thus SEARCH) from the system
 * service-account role on existing tenants, replacing it with the scoped AGENT_DOCUMENT_ACCESS
 * capability. Safe: SERVICE_ROLE_ID is a reserved role, never user-editable (see
 * ReservedKeyValidator).
 *
 * <p>The service-account role is identified by the {@code AGENT_RUNTIME_ACCESS} capability, which
 * it alone holds (a hidden SERVICE-group capability never assignable through the UI; the STIX
 * system role uses {@code MANAGE_STIX_BUNDLE} instead). Anchoring on it targets exactly the
 * service-account roles across every tenant in a single pass, without reproducing the Java-derived
 * per-tenant role UUID ({@code AbstractPrivilegeService#getUUIDFromName}) in SQL, and without
 * touching the Observer/Manager roles that legitimately keep {@code ACCESS_DOCUMENTS}.
 *
 * <p>Idempotent by construction: the grant is guarded with {@code ON CONFLICT DO NOTHING} and the
 * removal matches zero rows on a re-run.
 */
@Component
public class V6_20260916210000000__Restrict_service_account_document_search
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      // 1. Grant the scoped AGENT_DOCUMENT_ACCESS to every service-account role.
      statement.execute(
          """
          INSERT INTO roles_capabilities (role_id, capability)
          SELECT DISTINCT rc.role_id, 'AGENT_DOCUMENT_ACCESS'
          FROM roles_capabilities rc
          WHERE rc.capability = 'AGENT_RUNTIME_ACCESS'
          ON CONFLICT (role_id, capability) DO NOTHING;
          """);

      // 2. Remove ACCESS_DOCUMENTS (which also grants SEARCH) from those same roles only.
      statement.execute(
          """
          DELETE FROM roles_capabilities rc
          WHERE rc.capability = 'ACCESS_DOCUMENTS'
            AND rc.role_id IN (
              SELECT role_id
              FROM roles_capabilities
              WHERE capability = 'AGENT_RUNTIME_ACCESS'
            );
          """);
    }
  }
}
