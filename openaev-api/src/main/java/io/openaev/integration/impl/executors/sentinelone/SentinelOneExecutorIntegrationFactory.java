package io.openaev.integration.impl.executors.sentinelone;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.ee.Ee;
import io.openaev.executors.ExecutorService;
import io.openaev.executors.sentinelone.client.SentinelOneExecutorClient;
import io.openaev.executors.sentinelone.config.SentinelOneExecutorConfig;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.Integration;
import io.openaev.integration.IntegrationFactory;
import io.openaev.integration.configuration.BaseIntegrationConfiguration;
import io.openaev.integration.migration.SentinelOneExecutorConfigurationMigration;
import io.openaev.rest.connector_instance.service.ConnectorInstanceService;
import io.openaev.service.AgentService;
import io.openaev.service.AssetGroupService;
import io.openaev.service.EndpointService;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SentinelOneExecutorIntegrationFactory implements IntegrationFactory {
  private final ExecutorService executorService;
  private final ComponentRequestEngine componentRequestEngine;
  private final ConnectorInstanceService connectorInstanceService;
  private final CatalogConnectorService catalogConnectorService;
  private final SentinelOneExecutorConfigurationMigration sentinelOneExecutorConfigurationMigration;

  private final SentinelOneExecutorClient client;
  private final AgentService agentService;
  private final EndpointService endpointService;
  private final AssetGroupService assetGroupService;
  private final Ee eeService;
  private final LicenseCacheManager licenseCacheManager;
  private final ThreadPoolTaskScheduler taskScheduler;

  @Override
  @Transactional
  public List<Integration> initialise() {
    String className = this.getClass().getCanonicalName();
    if (catalogConnectorService.findByFactoryClassName(className).isEmpty()) {
      CatalogConnector connector = new CatalogConnector();
      connector.setTitle(className);
      connector.setSlug(className);
      connector.setClassName(className);
      connector.setContainerType(CatalogConnector.CONNECTOR_TYPE.EXECUTOR);
      connector.setCatalogConnectorConfigurations(
          new SentinelOneExecutorConfig().toCatalogConfigurationSet(connector));
      catalogConnectorService.saveAll(List.of(connector));
    }

    sentinelOneExecutorConfigurationMigration.migrate();

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
  public Integration spawn(ConnectorInstance instance)
      throws JsonProcessingException,
          InvocationTargetException,
          NoSuchMethodException,
          InstantiationException,
          IllegalAccessException {
    return new SentinelOneExecutorIntegration(
        instance,
        connectorInstanceService,
        client,
        BaseIntegrationConfiguration.fromConnectorInstanceConfigurationSet(
            instance.getConfigurations(), SentinelOneExecutorConfig.class),
        endpointService,
        agentService,
        assetGroupService,
        eeService,
        licenseCacheManager,
        componentRequestEngine,
        executorService,
        taskScheduler);
  }
}
