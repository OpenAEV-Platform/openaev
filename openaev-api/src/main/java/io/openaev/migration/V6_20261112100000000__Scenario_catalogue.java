package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V6_20261112100000000__Scenario_catalogue extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          CREATE TABLE IF NOT EXISTS scenario_identities (
            scenario_identity_id VARCHAR(255) NOT NULL PRIMARY KEY,
            tenant_id VARCHAR(255) NOT NULL REFERENCES tenants (tenant_id) ON DELETE CASCADE,
            scenario_identity_repository VARCHAR(255) NOT NULL,
            scenario_identity_external_id VARCHAR(255) NOT NULL,
            scenario_identity_created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
            scenario_identity_updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
            CONSTRAINT scenario_identities_catalogue_external_id_unique
              UNIQUE (tenant_id, scenario_identity_repository, scenario_identity_external_id)
          );
          """);
      statement.execute(
          """
          ALTER TABLE scenarios
            ADD COLUMN IF NOT EXISTS scenario_catalog_identity VARCHAR(255)
              REFERENCES scenario_identities (scenario_identity_id) ON DELETE SET NULL,
            ADD COLUMN IF NOT EXISTS scenario_catalog_version VARCHAR(255);
          """);
      // Installed once per tenant: deleting the scenario uninstalls it, its identity stays.
      statement.execute(
          "CREATE UNIQUE INDEX IF NOT EXISTS scenarios_catalog_identity_unique"
              + " ON scenarios (scenario_catalog_identity);");
    }
  }
}
