package io.openaev.integration.migration;

import io.openaev.executors.mde.config.MdeExecutorConfig;
import io.openaev.integration.impl.executors.mde.MdeExecutorIntegrationFactory;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import io.openaev.service.connector_instances.EncryptionFactory;
import org.springframework.stereotype.Component;

@Component
public class MdeExecutorConfigurationMigration extends ConfigurationMigration {

  public MdeExecutorConfigurationMigration(
      CatalogConnectorService catalogConnectorService,
      ConnectorInstanceService connectorInstanceService,
      MdeExecutorConfig config,
      EncryptionFactory encryptionFactory) {
    super(
        config,
        MdeExecutorIntegrationFactory.class.getCanonicalName(),
        catalogConnectorService,
        connectorInstanceService,
        encryptionFactory);
  }
}
