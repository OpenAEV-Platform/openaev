package io.openaev.integration.migration;

import io.openaev.executors.paloaltocortex.config.PaloAltoCortexExecutorConfig;
import io.openaev.integration.impl.executors.paloaltocortex.PaloAltoCortexExecutorIntegrationFactory;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import io.openaev.service.connector_instances.EncryptionFactory;
import org.springframework.stereotype.Component;

@Component
public class PaloAltoCortexExecutorConfigurationMigration extends ConfigurationMigration {
  public PaloAltoCortexExecutorConfigurationMigration(
      CatalogConnectorService catalogConnectorService,
      ConnectorInstanceService connectorInstanceService,
      PaloAltoCortexExecutorConfig config,
      EncryptionFactory encryptionFactory) {
    super(
        config,
        PaloAltoCortexExecutorIntegrationFactory.class.getCanonicalName(),
        catalogConnectorService,
        connectorInstanceService,
        encryptionFactory);
  }
}
