package io.openaev.integration.impl.executors.caldera;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorType;
import io.openaev.executors.ExecutorService;
import io.openaev.executors.caldera.client.CalderaExecutorClient;
import io.openaev.executors.caldera.config.CalderaExecutorConfig;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.Integration;
import io.openaev.integration.IntegrationFactory;
import io.openaev.integration.configuration.BaseIntegrationConfiguration;
import io.openaev.integration.migration.CalderaExecutorConfigurationMigration;
import io.openaev.integrations.InjectorService;
import io.openaev.service.AgentService;
import io.openaev.service.EndpointService;
import io.openaev.service.FileService;
import io.openaev.service.PlatformSettingsService;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

@Service
@Profile("!test")
public class CalderaExecutorIntegrationFactory extends IntegrationFactory {
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
  private final FileService fileService;

  public CalderaExecutorIntegrationFactory(
      ConnectorInstanceService connectorInstanceService,
      CatalogConnectorService catalogConnectorService,
      ExecutorService executorService,
      ComponentRequestEngine componentRequestEngine,
      CalderaExecutorConfigurationMigration calderaExecutorConfigurationMigration,
      CalderaExecutorClient client,
      AgentService agentService,
      EndpointService endpointService,
      InjectorService injectorService,
      PlatformSettingsService platformSettingsService,
      ThreadPoolTaskScheduler taskScheduler,
      FileService fileService) {
    super(connectorInstanceService, catalogConnectorService);
    this.executorService = executorService;
    this.componentRequestEngine = componentRequestEngine;
    this.connectorInstanceService = connectorInstanceService;
    this.catalogConnectorService = catalogConnectorService;
    this.calderaExecutorConfigurationMigration = calderaExecutorConfigurationMigration;
    this.client = client;
    this.agentService = agentService;
    this.endpointService = endpointService;
    this.injectorService = injectorService;
    this.platformSettingsService = platformSettingsService;
    this.taskScheduler = taskScheduler;
    this.fileService = fileService;
  }

  @Override
  protected final String getClassName() {
    return this.getClass().getCanonicalName();
  }

  @Override
  protected void runMigrations() throws Exception {
    calderaExecutorConfigurationMigration.migrate();
  }

  @Override
  protected void insertCatalogEntry() throws Exception {
    String logoFilename = "%s-logo.png".formatted(getClassName());
    fileService.uploadStream(
        FileService.CONNECTORS_LOGO_PATH,
        logoFilename,
        getClass().getResourceAsStream("/img/icon-caldera.png"));
    CatalogConnector connector = new CatalogConnector();
    connector.setTitle("Caldera Executor");
    connector.setSlug(getClassName());
    connector.setLogoUrl(logoFilename);
    connector.setDescription(
        "With Caldera executor register your asset in OpenAEV and enable execution of OpenAEV scenarios through your Caldera instance.");
    connector.setShortDescription(
        "Enable execution of OpenAEV scenarios through your Caldera instance.");
    connector.setClassName(getClassName());
    connector.setSubscriptionLink("https://caldera.mitre.org/");
    connector.setContainerType(ConnectorType.EXECUTOR);
    connector.setCatalogConnectorConfigurations(
        new CalderaExecutorConfig().toCatalogConfigurationSet(connector));
    catalogConnectorService.saveAll(List.of(connector));
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
