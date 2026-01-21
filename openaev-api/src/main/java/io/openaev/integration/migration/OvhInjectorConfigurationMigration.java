package io.openaev.integration.migration;

import io.openaev.injectors.ovh.config.OvhSmsInjectorConfig;
import io.openaev.integration.impl.injectors.ovh.OvhInjectorIntegrationFactory;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import io.openaev.service.connector_instances.EncryptionFactory;
import org.springframework.stereotype.Component;

@Component
public class OvhInjectorConfigurationMigration extends ConfigurationMigration {
  public OvhInjectorConfigurationMigration(
      CatalogConnectorService catalogConnectorService,
      ConnectorInstanceService connectorInstanceService,
      OvhSmsInjectorConfig config,
      EncryptionFactory encryptionFactory) {
    super(
        config,
        OvhInjectorIntegrationFactory.class.getCanonicalName(),
        catalogConnectorService,
        connectorInstanceService,
        encryptionFactory);
  }
}
