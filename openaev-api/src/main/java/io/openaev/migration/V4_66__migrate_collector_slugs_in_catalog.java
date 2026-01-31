package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_66__migrate_collector_slugs_in_catalog extends BaseJavaMigration {
  @Override
  public void migrate(Context context) throws Exception {
    try (Statement stmt = context.getConnection().createStatement()) {
      stmt.execute(
          """
          UPDATE catalog_connectors SET catalog_connector_slug = 'openaev_crowdstrike_executor' WHERE catalog_connector_slug = 'openaev_crowdstrike';
          UPDATE catalog_connectors SET catalog_connector_slug = 'openaev_crowdstrike' WHERE catalog_connector_slug = 'openaev_crowdstrike_collector';
          UPDATE catalog_connectors SET catalog_connector_slug = 'openaev_sentinelone_executor' WHERE catalog_connector_slug = 'openaev_sentinelone';
          UPDATE catalog_connectors SET catalog_connector_slug = 'openaev_sentinelone' WHERE catalog_connector_slug = 'openaev_sentinelone_collector';
          """);
    }
  }
}
