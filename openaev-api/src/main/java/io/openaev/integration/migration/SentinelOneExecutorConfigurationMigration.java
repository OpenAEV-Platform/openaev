package io.openaev.integration.migration;

import io.openaev.executors.sentinelone.config.SentinelOneExecutorConfig;
import io.openaev.integration.impl.executors.sentinelone.SentinelOneExecutorIntegrationFactory;
import io.openaev.rest.connector_instance.service.ConnectorInstanceService;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SentinelOneExecutorConfigurationMigration extends ConfigurationMigration {
  public SentinelOneExecutorConfigurationMigration(
      CatalogConnectorService catalogConnectorService,
      ConnectorInstanceService connectorInstanceService,
      SentinelOneExecutorConfig config) {
    super(
        config,
        SentinelOneExecutorIntegrationFactory.class.getCanonicalName(),
        catalogConnectorService,
        connectorInstanceService);
  }
}
