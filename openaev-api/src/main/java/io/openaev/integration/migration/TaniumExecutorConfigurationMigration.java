package io.openaev.integration.migration;

import io.openaev.executors.tanium.config.TaniumExecutorConfig;
import io.openaev.integration.impl.executors.tanium.TaniumExecutorIntegrationFactory;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import org.springframework.stereotype.Component;

@Component
public class TaniumExecutorConfigurationMigration extends ConfigurationMigration {
  public TaniumExecutorConfigurationMigration(
      CatalogConnectorService catalogConnectorService,
      ConnectorInstanceService connectorInstanceService,
      TaniumExecutorConfig config) {
    super(
        config,
        TaniumExecutorIntegrationFactory.class.getCanonicalName(),
        catalogConnectorService,
        connectorInstanceService);
  }
}
