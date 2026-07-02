package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Fixes the unique index on security platforms to include {@code tenant_id}, allowing the same
 * security platform name + type to exist in different tenants.
 *
 * <p>Also fixes agents with mismatched {@code tenant_id} (not matching their executor's tenant),
 * which causes composite FK violations on update.
 */
@Component
public class V6_20260701174700000__Fix_security_platform_unique_idx_add_tenant
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement stmt = context.getConnection().createStatement()) {

      // 1. Drop the non-tenant-scoped unique index
      stmt.execute("DROP INDEX IF EXISTS unique_security_platform_name_type_ci_idx");

      // 2. Re-create with tenant_id included
      stmt.execute(
          """
          CREATE UNIQUE INDEX unique_security_platform_name_type_ci_idx
          ON assets (lower(asset_name::text), security_platform_type, tenant_id)
          WHERE asset_type::text = 'SecurityPlatform'
          """);

      // 3. Fix agents with tenant_id mismatching their executor's tenant
      stmt.execute(
          """
          UPDATE agents a
          SET tenant_id = e.tenant_id
          FROM executors e
          WHERE e.executor_id = a.agent_executor
            AND a.tenant_id != e.tenant_id
          """);
    }
  }
}
