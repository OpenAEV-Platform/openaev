package io.openaev.integration.impl.executors.caldera;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.executors.ExecutorService;
import io.openaev.executors.caldera.client.CalderaExecutorClient;
import io.openaev.executors.caldera.config.CalderaExecutorConfig;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.Integration;
import io.openaev.integration.IntegrationFactory;
import io.openaev.integration.configuration.BaseIntegrationConfiguration;
import io.openaev.integration.migration.CalderaExecutorConfigurationMigration;
import io.openaev.integrations.InjectorService;
import io.openaev.rest.connector_instance.service.ConnectorInstanceService;
import io.openaev.service.AgentService;
import io.openaev.service.EndpointService;
import io.openaev.service.PlatformSettingsService;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CalderaExecutorIntegrationFactory implements IntegrationFactory {
  private final ExecutorService executorService;
  private final ComponentRequestEngine componentRequestEngine;
  private final ConnectorInstanceService connectorInstanceService;
  private final CatalogConnectorService catalogConnectorService;
  private final CalderaExecutorConfigurationMigration calderaExecutorConfigurationMigration;

  private final CalderaExecutorClient client;
  private final AgentService agentService;
  private final EndpointService endpointService;
  private final InjectorService injectorService;
  private final PlatformSettingsService platformSettingsService;
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
          new CalderaExecutorConfig().toCatalogConfigurationSet(connector));
      catalogConnectorService.saveAll(List.of(connector));
    }

    calderaExecutorConfigurationMigration.migrate();

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
    return new CalderaExecutorIntegration(
        instance,
        connectorInstanceService,
        client,
        BaseIntegrationConfiguration.fromConnectorInstanceConfigurationSet(
            instance.getConfigurations(), CalderaExecutorConfig.class),
        endpointService,
        agentService,
        executorService,
        componentRequestEngine,
        platformSettingsService,
        injectorService,
        taskScheduler);
  }
}
