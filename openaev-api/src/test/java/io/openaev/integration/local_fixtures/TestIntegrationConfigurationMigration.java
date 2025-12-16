package io.openaev.integration.local_fixtures;

import io.openaev.integration.migration.ConfigurationMigration;
import io.openaev.rest.connector_instance.service.ConnectorInstanceService;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import org.springframework.stereotype.Component;

@Component
public class TestIntegrationConfigurationMigration extends ConfigurationMigration {
  protected TestIntegrationConfigurationMigration(
      TestIntegrationConfiguration configuration,
      CatalogConnectorService catalogConnectorService,
      ConnectorInstanceService connectorInstanceService) {
    super(
        configuration,
        TestIntegrationFactory.class.getCanonicalName(),
        catalogConnectorService,
        connectorInstanceService);
  }
}
