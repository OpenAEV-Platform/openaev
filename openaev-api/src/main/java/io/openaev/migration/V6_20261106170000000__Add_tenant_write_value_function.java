package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds {@code tenant_write_value(row_tenant_id, allow_platform_write)}: it returns {@code
 * row_tenant_id} when {@code require_tenant_write} allows writing a row of that tenant (a platform
 * row needs the platform-write marker, a tenant row needs its tenant in the scope) and raises when
 * it does not. The statement inspector wraps the tenant a write assigns to a dual-scope table in
 * it, in an {@code INSERT ... VALUES}, an {@code UPDATE ... SET} and an {@code ON CONFLICT DO
 * UPDATE SET}, so the value is checked where it is written and a statement keeps the shape
 * PostgreSQL types its other columns by.
 */
@Component
public class V6_20261106170000000__Add_tenant_write_value_function extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          CREATE OR REPLACE FUNCTION tenant_write_value(
              row_tenant_id        text,
              allow_platform_write boolean DEFAULT false)
          RETURNS text
          LANGUAGE plpgsql STABLE PARALLEL SAFE AS $$
          BEGIN
            IF require_tenant_write(row_tenant_id, allow_platform_write) THEN
              RETURN row_tenant_id;
            END IF;
            RETURN NULL;
          END
          $$;
          """);
    }
  }
}
