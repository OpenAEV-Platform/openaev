package io.openaev.integration.migration;

import io.openaev.injectors.opencti.config.OpenctiInjectorConfig;
import io.openaev.integration.impl.injectors.opencti.OpenctiInjectorIntegrationFactory;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import io.openaev.service.connector_instances.EncryptionFactory;
import org.springframework.stereotype.Component;

@Component
public class OpenctiInjectorConfigurationMigration extends ConfigurationMigration {
  public OpenctiInjectorConfigurationMigration(
      CatalogConnectorService catalogConnectorService,
      ConnectorInstanceService connectorInstanceService,
      OpenctiInjectorConfig config,
      EncryptionFactory encryptionFactory) {
    super(
        config,
        OpenctiInjectorIntegrationFactory.class.getCanonicalName(),
        catalogConnectorService,
        connectorInstanceService,
            encryptionFactory);
  }
}
