package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Renames the stored Splunk ES connector instance configuration keys from the {@code SPLUNK_ES_*}
 * prefix to {@code SPLUNKES_*}, following the catalog schema correction (the collector container
 * reads {@code SPLUNKES_*} environment variables).
 *
 * <p>Without this rename, the catalog ingestion running at startup would treat every stored {@code
 * SPLUNK_ES_*} value as a key removed from the schema and delete it (see {@code
 * CatalogConnectorIngestionService}), silently wiping the base URL, username and password of every
 * deployed Splunk ES collector while the renamed required options start empty.
 *
 * <p>Idempotent: once renamed, the {@code SPLUNK_ES_} pattern no longer matches, and a duplicate
 * guard skips rows whose target key already exists on the same instance. Tenant-safe: the rename is
 * keyed on the row itself and never moves a row across instances or tenants.
 */
@Component
public class V6_20260725120000000__Rename_splunk_es_configuration_keys extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      // 'SPLUNK_ES_' is 10 characters, so the suffix starts at position 11.
      statement.executeUpdate(
          "UPDATE connector_instance_configurations c "
              + "SET connector_instance_configuration_key = "
              + "'SPLUNKES_' || SUBSTRING(c.connector_instance_configuration_key FROM 11) "
              + "WHERE c.connector_instance_configuration_key LIKE 'SPLUNK\\_ES\\_%' "
              + "AND NOT EXISTS ("
              + "  SELECT 1 FROM connector_instance_configurations d "
              + "  WHERE d.connector_instance_id = c.connector_instance_id "
              + "  AND d.connector_instance_configuration_key = "
              + "    'SPLUNKES_' || SUBSTRING(c.connector_instance_configuration_key FROM 11)"
              + ");");
    }
  }
}
