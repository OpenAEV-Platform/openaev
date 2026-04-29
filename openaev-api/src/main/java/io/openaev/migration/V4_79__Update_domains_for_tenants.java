package io.openaev.migration;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_79__Update_domains_for_tenants extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      // Add foreign keys with index, auto set default tenant id with default value
      statement.execute(
          String.format(
              """
                  ALTER TABLE domains
                     ADD COLUMN tenant_id VARCHAR(255) NOT NULL DEFAULT '%s',
                     ADD CONSTRAINT fk_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id) ON DELETE CASCADE;
                  """,
              DEFAULT_TENANT_UUID));
      statement.execute(
          """
                  CREATE INDEX IF NOT EXISTS idx_tenant_id ON domains(tenant_id);
                  """);
      // Delete the unique name constraint and recreate for name/tenant_id to allow same domain name
      // for different tenants
      statement.execute(
          """
                    ALTER TABLE domains
                    DROP CONSTRAINT domains_domain_name_key;
                    """);
      statement.execute(
          """
                    ALTER TABLE domains
                    ADD CONSTRAINT domains_domain_name_tenant_key UNIQUE (domain_name, tenant_id);
                    """);
    }
  }
}
