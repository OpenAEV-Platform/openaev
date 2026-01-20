package io.openaev.integration.migration;

import io.openaev.executors.caldera.config.CalderaExecutorConfig;
import io.openaev.integration.impl.executors.caldera.CalderaExecutorIntegrationFactory;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import io.openaev.service.connector_instances.EncryptionFactory;
import org.springframework.stereotype.Component;

@Component
public class CalderaExecutorConfigurationMigration extends ConfigurationMigration {
  protected CalderaExecutorConfigurationMigration(
      CalderaExecutorConfig configuration,
      CatalogConnectorService catalogConnectorService,
      ConnectorInstanceService connectorInstanceService,
      EncryptionFactory encryptionFactory) {
    super(
        configuration,
        CalderaExecutorIntegrationFactory.class.getCanonicalName(),
        catalogConnectorService,
        connectorInstanceService,
        encryptionFactory);
  }
}
