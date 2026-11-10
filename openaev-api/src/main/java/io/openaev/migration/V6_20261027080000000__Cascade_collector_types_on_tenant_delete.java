package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/** Lets the tenant purge delete a tenant's collector types, like every other tenant table. */
@Component
public class V6_20261027080000000__Cascade_collector_types_on_tenant_delete
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "ALTER TABLE collector_types DROP CONSTRAINT IF EXISTS fk_collector_type_tenant");
      statement.execute(
          """
          ALTER TABLE collector_types
              ADD CONSTRAINT fk_collector_type_tenant
                  FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id) ON DELETE CASCADE
          """);
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_collector_types_tenant_id ON collector_types(tenant_id)");
    }
  }
}
