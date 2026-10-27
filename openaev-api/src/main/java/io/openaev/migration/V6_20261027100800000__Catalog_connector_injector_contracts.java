package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/** Contracts each catalogue injector registers, listed in the Threat Arsenal before it runs. */
@Component
public class V6_20261027100800000__Catalog_connector_injector_contracts extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "ALTER TABLE catalog_connectors ADD COLUMN IF NOT EXISTS"
              + " catalog_connector_injector_contracts JSONB NOT NULL DEFAULT '[]'::jsonb");
    }
  }
}
