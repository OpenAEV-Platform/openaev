package io.openaev.integration.migration;

import io.openaev.executors.crowdstrike.config.CrowdStrikeExecutorConfig;
import io.openaev.integration.impl.executors.crowdstrike.CrowdStrikeExecutorIntegrationFactory;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import io.openaev.service.connector_instances.EncryptionFactory;
import org.springframework.stereotype.Component;

@Component
public class CrowdStrikeExecutorConfigurationMigration extends ConfigurationMigration {
  public CrowdStrikeExecutorConfigurationMigration(
      CatalogConnectorService catalogConnectorService,
      ConnectorInstanceService connectorInstanceService,
      CrowdStrikeExecutorConfig config,
      EncryptionFactory encryptionFactory) {
    super(
        config,
        CrowdStrikeExecutorIntegrationFactory.class.getCanonicalName(),
        catalogConnectorService,
        connectorInstanceService,
        encryptionFactory);
  }
}
