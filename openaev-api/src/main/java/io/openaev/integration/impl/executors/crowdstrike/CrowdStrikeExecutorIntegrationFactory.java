package io.openaev.integration.impl.executors.crowdstrike;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.ee.Ee;
import io.openaev.executors.ExecutorService;
import io.openaev.executors.crowdstrike.client.CrowdStrikeExecutorClient;
import io.openaev.executors.crowdstrike.config.CrowdStrikeExecutorConfig;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.Integration;
import io.openaev.integration.IntegrationFactory;
import io.openaev.integration.configuration.BaseIntegrationConfiguration;
import io.openaev.integration.migration.CrowdStrikeExecutorConfigurationMigration;
import io.openaev.rest.connector_instance.service.ConnectorInstanceService;
import io.openaev.service.AgentService;
import io.openaev.service.AssetGroupService;
import io.openaev.service.EndpointService;
import io.openaev.service.FileService;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CrowdStrikeExecutorIntegrationFactory implements IntegrationFactory {
  private final CrowdStrikeExecutorClient client;
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
  private final FileService fileService;

  @Override
  @Transactional
  public List<Integration> initialise() throws Exception {
    String className = this.getClass().getCanonicalName();
    if (catalogConnectorService.findByFactoryClassName(className).isEmpty()) {
      String logoFilename = "%s-logo.png".formatted(className);
      fileService.uploadStream(
          FileService.CONNECTORS_LOGO_PATH,
          logoFilename,
          getClass().getResourceAsStream("/img/icon-crowdstrike.png"));
      CatalogConnector connector = new CatalogConnector();
      connector.setTitle("Crowdstrike Executor");
      connector.setSlug(className);
      connector.setLogoUrl(logoFilename);
      connector.setDescription(
          """
              CrowdStrike Falcon Intelligence is an integral threat intelligence module within the Falcon platform, crafted to enhance the speed and effectiveness of threat detection, investigation, and response. It equips SOC teams to work more swiftly and intelligently, leveraging automation, enrichment, and high-fidelity data to optimize their cybersecurity operations.

              With Crowdstrike executor register your asset in OpenAEV and enable execution of OpenAEV scenarios through your Crowdstrike instance.
              """);
      connector.setShortDescription(
          "Enable execution of OpenAEV scenarios through your Crowdstrike instance.");
      connector.setClassName(className);
      connector.setSubscriptionLink("https://www.crowdstrike.com");
      connector.setContainerType(CatalogConnector.CONNECTOR_TYPE.EXECUTOR);
      connector.setCatalogConnectorConfigurations(
          new CrowdStrikeExecutorConfig().toCatalogConfigurationSet(connector));
      catalogConnectorService.saveAll(List.of(connector));
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
  public Integration spawn(ConnectorInstance instance)
      throws JsonProcessingException,
          InvocationTargetException,
          NoSuchMethodException,
          InstantiationException,
          IllegalAccessException {
    return new CrowdStrikeExecutorIntegration(
        instance,
        connectorInstanceService,
        client,
        BaseIntegrationConfiguration.fromConnectorInstanceConfigurationSet(
            instance.getConfigurations(), CrowdStrikeExecutorConfig.class),
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
