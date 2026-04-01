package io.openaev.migration;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_88__Update_cwes_for_tenants extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      // Add foreign keys with index, auto set default tenant id with default value
      statement.execute(
          String.format(
              """
                          ALTER TABLE cwes
                             ADD COLUMN tenant_id VARCHAR(255) NOT NULL DEFAULT '%s',
                             ADD CONSTRAINT fk_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id) ON DELETE CASCADE;
                          """,
              DEFAULT_TENANT_UUID));
      statement.execute(
          """
                      CREATE INDEX IF NOT EXISTS idx_tenant_id ON cwes(tenant_id);
                      """);
      // Delete the unique external id constraint and recreate for external id/tenant_id to allow
      // same cwe/vulnerability external id for different tenants
      statement.execute(
          """
                        ALTER TABLE cwes
                        DROP CONSTRAINT cwes_cwe_external_id_key;
                        """);
      statement.execute(
          """
                        ALTER TABLE cwes
                        ADD CONSTRAINT cwes_cwe_external_id_tenant_key UNIQUE (cwe_external_id, tenant_id);
                        """);
      statement.execute(
          """
                        ALTER TABLE vulnerabilities
                        DROP CONSTRAINT cves_cve_external_id_key;
                        """);
      statement.execute(
          """
                        ALTER TABLE vulnerabilities
                        ADD CONSTRAINT cves_cve_external_id_tenant_key UNIQUE (vulnerability_external_id, tenant_id);
                        """);
    }
  }
}
