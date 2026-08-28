package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds {@code can_access_tenant(row_tenant_id, allow_platform)}, used to filter rows against the
 * tenant scope held in the {@code app.current_tenants} setting. Returns false when no scope is set
 * (fail-closed); a null (platform) tenant passes only when {@code allow_platform} is true.
 */
@Component
public class V5_27__Add_can_access_tenant_function extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          CREATE OR REPLACE FUNCTION can_access_tenant(
              row_tenant_id  text,
              allow_platform boolean DEFAULT false)
          RETURNS boolean
          LANGUAGE sql STABLE PARALLEL SAFE AS $$
            SELECT CASE
              WHEN current_setting('app.current_tenants', true) IS NULL
                OR current_setting('app.current_tenants', true) = '' THEN false
              ELSE COALESCE(
                     (allow_platform AND row_tenant_id IS NULL)
                       OR row_tenant_id = ANY (
                            string_to_array(current_setting('app.current_tenants', true), ',')),
                     false)
            END
          $$;
          """);
    }
  }
}
