package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V6_20261119100000000__Catalog_connector_injector_contracts extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          CREATE TABLE IF NOT EXISTS catalog_connector_injector_contracts (
            catalog_connector_injector_contract_id VARCHAR(255) NOT NULL PRIMARY KEY,
            catalog_connector_id VARCHAR(255) NOT NULL
              REFERENCES catalog_connectors (catalog_connector_id) ON DELETE CASCADE,
            catalog_connector_injector_contract_external_id VARCHAR(255) NOT NULL,
            catalog_connector_injector_contract_labels hstore,
            catalog_connector_injector_contract_domains text[] NOT NULL DEFAULT '{}',
            catalog_connector_injector_contract_platforms text[] NOT NULL DEFAULT '{}',
            catalog_connector_injector_contract_attack_patterns text[] NOT NULL DEFAULT '{}',
            CONSTRAINT catalog_connector_injector_contracts_external_id_unique
              UNIQUE (catalog_connector_id, catalog_connector_injector_contract_external_id)
          );
          """);
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_catalog_connector_injector_contracts_connector"
              + " ON catalog_connector_injector_contracts (catalog_connector_id);");
    }
  }
}
