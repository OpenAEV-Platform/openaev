package io.openaev.integration.migration;

import io.openaev.executors.tanium.config.TaniumExecutorConfig;
import io.openaev.integration.impl.executors.tanium.TaniumExecutorIntegrationFactory;
import io.openaev.rest.connector_instance.service.ConnectorInstanceService;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
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
