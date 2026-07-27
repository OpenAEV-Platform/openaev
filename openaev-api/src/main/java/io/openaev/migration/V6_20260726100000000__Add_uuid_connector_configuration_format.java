package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds the UUID value to the connector_configuration_format enum. Catalog connector manifests use
 * the standard JSON Schema {@code "format": "uuid"} on identifier properties (connector IDs, ...);
 * without this value the catalog ingestion logged an "Unknown format 'uuid'" warning for every such
 * property at every startup and silently dropped the format information.
 */
@Component
public class V6_20260726100000000__Add_uuid_connector_configuration_format
    extends BaseJavaMigration {
  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          ALTER TYPE connector_configuration_format ADD VALUE IF NOT EXISTS 'UUID';
          """);
    }
  }
}
