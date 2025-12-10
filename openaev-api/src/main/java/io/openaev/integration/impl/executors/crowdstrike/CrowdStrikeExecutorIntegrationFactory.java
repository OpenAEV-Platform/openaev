package io.openaev.integration.impl.executors.crowdstrike;

import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.ee.Ee;
import io.openaev.executors.ExecutorService;
import io.openaev.executors.crowdstrike.client.CrowdStrikeExecutorClient;
import io.openaev.executors.crowdstrike.config.CrowdStrikeExecutorConfig;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.Integration;
import io.openaev.integration.IntegrationFactory;
import io.openaev.integration.migration.CrowdStrikeExecutorConfigurationMigration;
import io.openaev.rest.connector_instance.service.ConnectorInstanceService;
import io.openaev.service.AgentService;
import io.openaev.service.AssetGroupService;
import io.openaev.service.CatalogConnectorService;
import io.openaev.service.EndpointService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CrowdStrikeExecutorIntegrationFactory implements IntegrationFactory {
  private final CrowdStrikeExecutorClient client;
  private final CrowdStrikeExecutorConfig config;
  private final EndpointService endpointService;
  private final AgentService agentService;
  private final AssetGroupService assetGroupService;
  private final ExecutorService executorService;
  private final Ee eeService;
  private final LicenseCacheManager licenseCacheManager;
  private final ComponentRequestEngine componentRequestEngine;
  private final ThreadPoolTaskScheduler taskScheduler;
  private final CatalogConnectorService catalogConnectorService;
  private final ConnectorInstanceService connectorInstanceService;
  private final CrowdStrikeExecutorConfigurationMigration crowdStrikeExecutorConfigurationMigration;

  @Override
  public List<Integration> initialise() {
    String className = this.getClass().getCanonicalName();
    if (catalogConnectorService.findByFactoryClassName(className).isEmpty()) {
      catalogConnectorService.createBuiltIn(className);
    }

    crowdStrikeExecutorConfigurationMigration.migrate();

    return connectorInstanceService.connectorInstances().stream()
        .filter(
            ci ->
                this.getClass().getCanonicalName().equals(ci.getCatalogConnector().getClassName()))
        .map(
            instance -> {
              try {
                Integration integration = this.spawn(instance);
                integration.initialise();
                return integration;
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            })
        .toList();
  }

  @Override
  public Integration spawn(ConnectorInstance instance) {
    return new CrowdStrikeExecutorIntegration(
        instance,
        client,
        config,
        endpointService,
        agentService,
        assetGroupService,
        executorService,
        eeService,
        licenseCacheManager,
        componentRequestEngine,
        taskScheduler);
  }
}
