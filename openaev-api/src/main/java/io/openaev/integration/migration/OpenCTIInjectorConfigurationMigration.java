package io.openaev.integration.migration;

import io.openaev.injectors.opencti.config.OpenCTIInjectorConfig;
import io.openaev.integration.impl.injectors.opencti.OpenCTIInjectorIntegrationFactory;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import io.openaev.service.connector_instances.EncryptionFactory;
import org.springframework.stereotype.Component;

@Component
public class OpenCTIInjectorConfigurationMigration extends ConfigurationMigration {
  public OpenCTIInjectorConfigurationMigration(
      CatalogConnectorService catalogConnectorService,
      ConnectorInstanceService connectorInstanceService,
      OpenCTIInjectorConfig config,
      EncryptionFactory encryptionFactory) {
    super(
        config,
        OpenCTIInjectorIntegrationFactory.class.getCanonicalName(),
        catalogConnectorService,
        connectorInstanceService,
        encryptionFactory);
  }
}
