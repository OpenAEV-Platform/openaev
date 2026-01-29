package io.openaev.integration.local_fixtures.regular;

import io.openaev.integration.migration.ConfigurationMigration;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import io.openaev.service.connector_instances.EncryptionFactory;
import org.springframework.stereotype.Component;

@Component
public class TestIntegrationConfigurationMigration extends ConfigurationMigration {
  protected TestIntegrationConfigurationMigration(
      TestIntegrationConfiguration configuration,
      CatalogConnectorService catalogConnectorService,
      ConnectorInstanceService connectorInstanceService,
      EncryptionFactory encryptionFactory) {
    super(
        configuration,
        TestIntegrationFactory.class.getCanonicalName(),
        catalogConnectorService,
        connectorInstanceService,
        encryptionFactory);
  }
}
