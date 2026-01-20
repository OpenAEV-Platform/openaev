package io.openaev.integration.migration;

import io.openaev.executors.sentinelone.config.SentinelOneExecutorConfig;
import io.openaev.integration.impl.executors.sentinelone.SentinelOneExecutorIntegrationFactory;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import io.openaev.service.connector_instances.EncryptionFactory;
import org.springframework.stereotype.Component;

@Component
public class SentinelOneExecutorConfigurationMigration extends ConfigurationMigration {
  public SentinelOneExecutorConfigurationMigration(
      CatalogConnectorService catalogConnectorService,
      ConnectorInstanceService connectorInstanceService,
      SentinelOneExecutorConfig config,
      EncryptionFactory encryptionFactory) {
    super(
        config,
        SentinelOneExecutorIntegrationFactory.class.getCanonicalName(),
        catalogConnectorService,
        connectorInstanceService,
        encryptionFactory);
  }
}
