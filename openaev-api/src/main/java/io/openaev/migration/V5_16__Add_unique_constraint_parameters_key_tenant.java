package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V5_16__Add_unique_constraint_parameters_key_tenant extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement stmt = context.getConnection().createStatement()) {

      // 1. Remove duplicates — keep the most recently created row (latest parameter_id)
      stmt.execute(
          """
          DELETE FROM parameters p1
          USING parameters p2
          WHERE p1.parameter_key = p2.parameter_key
            AND p1.tenant_id IS NOT DISTINCT FROM p2.tenant_id
            AND p1.parameter_id < p2.parameter_id;
          """);

      // 2. Add unique constraint on (parameter_key, tenant_id) for tenant-scoped settings
      stmt.execute(
          """
          CREATE UNIQUE INDEX IF NOT EXISTS uk_parameters_key_tenant
          ON parameters(parameter_key, tenant_id)
          WHERE tenant_id IS NOT NULL;
          """);

      // 3. Add unique constraint on (parameter_key) for platform-scoped settings (tenant_id IS NULL)
      stmt.execute(
          """
          CREATE UNIQUE INDEX IF NOT EXISTS uk_parameters_key_platform
          ON parameters(parameter_key)
          WHERE tenant_id IS NULL;
          """);
    }
  }
}

