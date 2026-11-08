package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds the platform-write policy for dual-scope tables: {@code is_platform_write()} reads the
 * {@code app.platform_write} transaction-local setting, and {@code can_write_tenant(row_tenant_id,
 * allow_platform_write)} is the write-side counterpart of {@code can_access_tenant}. A tenant row
 * (non-null {@code row_tenant_id}) is checked exactly like today, through {@code
 * can_access_tenant}; a platform row ({@code row_tenant_id IS NULL}) is writable only when {@code
 * allow_platform_write} is true AND the transaction set {@code app.platform_write}. {@code
 * app.platform_write} is set only by a small, named list of call sites, listed in {@code
 * io.openaev.aop.PlatformWrite}: the platform settings endpoints, the XTM Composer registration
 * endpoints (also mounted on the tenant route and gated by the {@code CATALOG} write capability),
 * and the platform-write primitives of {@code TenantScopedTransaction} used by startup and the
 * scheduled writers. Any other transaction, a tenant route's included, cannot write a platform row
 * through this predicate.
 */
@Component
public class V6_20261106105000000__Add_platform_write_functions extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          CREATE OR REPLACE FUNCTION is_platform_write()
          RETURNS boolean
          LANGUAGE sql STABLE PARALLEL SAFE AS $$
            SELECT COALESCE(current_setting('app.platform_write', true), '') = 'true'
          $$;
          """);
      statement.execute(
          """
          CREATE OR REPLACE FUNCTION can_write_tenant(
              row_tenant_id       text,
              allow_platform_write boolean DEFAULT false)
          RETURNS boolean
          LANGUAGE sql STABLE PARALLEL SAFE AS $$
            SELECT CASE
              WHEN row_tenant_id IS NOT NULL THEN can_access_tenant(row_tenant_id)
              WHEN allow_platform_write THEN is_platform_write()
              ELSE false
            END
          $$;
          """);
    }
  }
}
