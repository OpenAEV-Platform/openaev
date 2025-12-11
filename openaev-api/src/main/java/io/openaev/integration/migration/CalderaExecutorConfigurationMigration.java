package io.openaev.integration.migration;

import io.openaev.executors.caldera.config.CalderaExecutorConfig;
import io.openaev.integration.impl.executors.caldera.CalderaExecutorIntegrationFactory;
import io.openaev.rest.connector_instance.service.ConnectorInstanceService;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import org.springframework.stereotype.Component;

@Component
public class CalderaExecutorConfigurationMigration extends ConfigurationMigration {
  protected CalderaExecutorConfigurationMigration(
      CalderaExecutorConfig configuration,
      CatalogConnectorService catalogConnectorService,
      ConnectorInstanceService connectorInstanceService) {
    super(
        configuration,
        CalderaExecutorIntegrationFactory.class.getCanonicalName(),
        catalogConnectorService,
        connectorInstanceService);
  }
}
