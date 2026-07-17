package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V6_20260710124002823__Fix_unique_security_platform_add_tenant_id
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement stmt = context.getConnection().createStatement()) {
      // Drop the old index that was missing tenant_id
      stmt.execute("DROP INDEX IF EXISTS unique_security_platform_name_type_ci_idx");

      // Recreate with tenant_id to allow same name+type across tenants
      stmt.execute(
          """
                  CREATE UNIQUE INDEX unique_security_platform_name_type_ci_idx
                  ON assets (
                      tenant_id,
                      lower(asset_name::text),
                      security_platform_type
                  )
                  WHERE asset_type::text = 'SecurityPlatform';
              """);
    }
  }
}
