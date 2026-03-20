package io.openaev.integration.impl.executors.paloaltocortex;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.openaev.authorisation.HttpClientFactory;
import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorType;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Executor;
import io.openaev.ee.Ee;
import io.openaev.executors.ExecutorService;
import io.openaev.executors.exception.ExecutorException;
import io.openaev.executors.paloaltocortex.client.PaloAltoCortexExecutorClient;
import io.openaev.executors.paloaltocortex.config.PaloAltoCortexExecutorConfig;
import io.openaev.executors.paloaltocortex.service.PaloAltoCortexExecutorContextService;
import io.openaev.executors.paloaltocortex.service.PaloAltoCortexExecutorService;
import io.openaev.executors.paloaltocortex.service.PaloAltoCortexGarbageCollectorService;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.Integration;
import io.openaev.integration.QualifiedComponent;
import io.openaev.integration.configuration.BaseIntegrationConfigurationBuilder;
import io.openaev.service.AgentService;
import io.openaev.service.AssetGroupService;
import io.openaev.service.EndpointService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Slf4j
public class PaloAltoCortexExecutorIntegration extends Integration {
  public static final String PALOALTOCORTEX_EXECUTOR_DEFAULT_ID =
      "2177ceeb-a9e2-4a33-bf30-1bf7c47f150a";
  public static final String PALOALTOCORTEX_EXECUTOR_TYPE = "openaev_paloaltocortex_executor";
  public static final String PALOALTOCORTEX_EXECUTOR_NAME = "PaloAltoCortex";
  private static final String PALOALTOCORTEX_EXECUTOR_DOCUMENTATION_LINK =
      "https://docs.openaev.io/latest/deployment/ecosystem/executors/#paloaltocortex-agent";
  private static final String PALOALTOCORTEX_EXECUTOR_BACKGROUND_COLOR = "#00CC66";

  @QualifiedComponent(identifier = PALOALTOCORTEX_EXECUTOR_NAME)
  private PaloAltoCortexExecutorContextService paloAltoCortexExecutorContextService;

  private PaloAltoCortexExecutorService paloAltoCortexExecutorService;
  private PaloAltoCortexGarbageCollectorService paloAltoCortexGarbageCollectorService;

  private PaloAltoCortexExecutorConfig config;
  private PaloAltoCortexExecutorClient client;
  private final AgentService agentService;
  private final EndpointService endpointService;
  private final AssetGroupService assetGroupService;
  private final ExecutorService executorService;
  private final Ee enterpriseEditionService;
  private final LicenseCacheManager licenseCacheManager;
  private final ThreadPoolTaskScheduler taskScheduler;
  private final ConnectorInstanceService connectorInstanceService;
  private final ConnectorInstance connectorInstance;
  private final HttpClientFactory httpClientFactory;
  private final BaseIntegrationConfigurationBuilder baseIntegrationConfigurationBuilder;

  private final List<ScheduledFuture<?>> timers = new ArrayList<>();

  public PaloAltoCortexExecutorIntegration(
      ConnectorInstance connectorInstance,
      ConnectorInstanceService connectorInstanceService,
      EndpointService endpointService,
      AgentService agentService,
      AssetGroupService assetGroupService,
      Ee enterpriseEditionService,
      LicenseCacheManager licenseCacheManager,
      ComponentRequestEngine componentRequestEngine,
      ExecutorService executorService,
      ThreadPoolTaskScheduler taskScheduler,
      BaseIntegrationConfigurationBuilder baseIntegrationConfigurationBuilder,
      HttpClientFactory httpClientFactory) {
    super(componentRequestEngine, connectorInstance, connectorInstanceService);
    this.endpointService = endpointService;
    this.agentService = agentService;
    this.assetGroupService = assetGroupService;
    this.enterpriseEditionService = enterpriseEditionService;
    this.licenseCacheManager = licenseCacheManager;
    this.executorService = executorService;
    this.taskScheduler = taskScheduler;
    this.connectorInstanceService = connectorInstanceService;
    this.connectorInstance = connectorInstance;
    this.httpClientFactory = httpClientFactory;
    this.baseIntegrationConfigurationBuilder = baseIntegrationConfigurationBuilder;

    // Refresh the context to get the config
    try {
      refresh();
    } catch (Exception e) {
      log.error("Error during initialization of the Palo Alto Cortex Executor", e);
      throw new ExecutorException(
          e, "Error during initialization of the Executor", PALOALTOCORTEX_EXECUTOR_NAME);
    }
  }

  @Override
  protected void innerStart() throws Exception {
    String executorId =
        connectorInstanceService.getConnectorInstanceConfigurationsByIdAndKey(
            connectorInstance.getId(), ConnectorType.EXECUTOR.getIdKeyName());

    Executor executor =
        executorService.register(
            executorId,
            PALOALTOCORTEX_EXECUTOR_TYPE,
            PALOALTOCORTEX_EXECUTOR_NAME,
            PALOALTOCORTEX_EXECUTOR_DOCUMENTATION_LINK,
            PALOALTOCORTEX_EXECUTOR_BACKGROUND_COLOR,
            getClass().getResourceAsStream("/img/icon-paloaltocortex.png"),
            getClass().getResourceAsStream("/img/banner-paloaltocortex.png"),
            new String[] {
              Endpoint.PLATFORM_TYPE.Windows.name(),
              Endpoint.PLATFORM_TYPE.Linux.name(),
              Endpoint.PLATFORM_TYPE.MacOS.name()
            });

    client = new PaloAltoCortexExecutorClient(config, httpClientFactory);
    paloAltoCortexExecutorContextService =
        new PaloAltoCortexExecutorContextService(
            config, client, enterpriseEditionService, licenseCacheManager, executorService);
    paloAltoCortexExecutorService =
        new PaloAltoCortexExecutorService(
            executor, client, config, endpointService, agentService, assetGroupService);
    paloAltoCortexGarbageCollectorService =
        new PaloAltoCortexGarbageCollectorService(
            config, paloAltoCortexExecutorContextService, agentService);

    timers.add(
        taskScheduler.scheduleAtFixedRate(
            paloAltoCortexExecutorService,
            Duration.ofSeconds(this.config.getApiRegisterInterval())));
    timers.add(
        taskScheduler.scheduleAtFixedRate(
            paloAltoCortexGarbageCollectorService,
            Duration.ofHours(this.config.getCleanImplantInterval())));
  }

  @Override
  protected void refresh()
      throws JsonProcessingException,
          InvocationTargetException,
          NoSuchMethodException,
          InstantiationException,
          IllegalAccessException {
    this.config = baseIntegrationConfigurationBuilder.build(PaloAltoCortexExecutorConfig.class);
    this.config.fromConnectorInstanceConfigurationSet(
        this.getConnectorInstance(), PaloAltoCortexExecutorConfig.class);
  }

  @Override
  protected void innerStop() {
    timers.forEach(timer -> timer.cancel(true));
  }
}
