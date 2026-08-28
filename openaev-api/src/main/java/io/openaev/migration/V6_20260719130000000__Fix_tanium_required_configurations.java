package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Marks the mandatory Tanium executor configuration keys (API URL, API key, Windows/Unix package
 * ids) as required in the catalog so they render as top-level fields in the deploy drawer instead
 * of being buried under "Advanced options". The source annotations in {@code TaniumExecutorConfig}
 * were fixed at the same time, but the catalog entry is only inserted when missing, so existing
 * installations need this data heal.
 *
 * <p>Idempotent and lock-light (targeted UPDATE on a handful of rows).
 */
@Component
public class V6_20260719130000000__Fix_tanium_required_configurations extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "UPDATE catalog_connectors_configuration cfg "
              + "SET connector_configuration_required = true "
              + "FROM catalog_connectors c "
              + "WHERE cfg.connector_configuration_catalog_id = c.catalog_connector_id "
              + "AND c.catalog_connector_slug = 'openaev_tanium' "
              + "AND cfg.connector_configuration_key IN ("
              + "'EXECUTOR_TANIUM_API_URL',"
              + "'EXECUTOR_TANIUM_API_KEY',"
              + "'EXECUTOR_TANIUM_WINDOWS_PACKAGE_ID',"
              + "'EXECUTOR_TANIUM_UNIX_PACKAGE_ID') "
              + "AND cfg.connector_configuration_required IS NOT TRUE;");
    }
  }
}
