package io.openaev.integration.impl.executors.crowdstrike;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.openaev.authorisation.HttpClientFactory;
import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorType;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Executor;
import io.openaev.ee.Ee;
import io.openaev.executors.ExecutorService;
import io.openaev.executors.crowdstrike.client.CrowdStrikeExecutorClient;
import io.openaev.executors.crowdstrike.config.CrowdStrikeExecutorConfig;
import io.openaev.executors.crowdstrike.service.CrowdStrikeExecutorContextService;
import io.openaev.executors.crowdstrike.service.CrowdStrikeExecutorService;
import io.openaev.executors.crowdstrike.service.CrowdStrikeGarbageCollectorService;
import io.openaev.executors.exception.ExecutorException;
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
public class CrowdStrikeExecutorIntegration extends Integration {
  public static final String CROWDSTRIKE_EXECUTOR_DEFAULT_ID =
      "b522d9bc-7ed6-44ac-9984-810dfb18f7be";
  public static final String CROWDSTRIKE_EXECUTOR_TYPE = "openaev_crowdstrike_executor";
  public static final String CROWDSTRIKE_EXECUTOR_NAME = "CrowdStrike";
  private static final String CROWDSTRIKE_EXECUTOR_DOCUMENTATION_LINK =
      "https://docs.openaev.io/latest/deployment/ecosystem/executors/#crowdstrike-falcon-agent";

  private static final String CROWDSTRIKE_EXECUTOR_BACKGROUND_COLOR = "#E12E37";

  @QualifiedComponent(identifier = CrowdStrikeExecutorContextService.SERVICE_NAME)
  private CrowdStrikeExecutorContextService crowdStrikeExecutorContextService;

  private CrowdStrikeExecutorService crowdStrikeExecutorService;
  private CrowdStrikeGarbageCollectorService crowdStrikeGarbageCollectorService;

  private final List<ScheduledFuture<?>> timers = new ArrayList<>();

  private CrowdStrikeExecutorClient client;
  private CrowdStrikeExecutorConfig config;
  private final EndpointService endpointService;
  private final AgentService agentService;
  private final AssetGroupService assetGroupService;
  private final ExecutorService executorService;
  private final Ee eeService;
  private final LicenseCacheManager licenseCacheManager;
  private final ThreadPoolTaskScheduler taskScheduler;
  private final ConnectorInstanceService connectorInstanceService;
  private final ConnectorInstance connectorInstance;
  private final HttpClientFactory httpClientFactory;
  private final BaseIntegrationConfigurationBuilder baseIntegrationConfigurationBuilder;

  public CrowdStrikeExecutorIntegration(
      ConnectorInstance connectorInstance,
      ConnectorInstanceService connectorInstanceService,
      EndpointService endpointService,
      AgentService agentService,
      AssetGroupService assetGroupService,
      ExecutorService executorService,
      Ee eeService,
      LicenseCacheManager licenseCacheManager,
      ComponentRequestEngine componentRequestEngine,
      ThreadPoolTaskScheduler taskScheduler,
      BaseIntegrationConfigurationBuilder baseIntegrationConfigurationBuilder,
      HttpClientFactory httpClientFactory) {
    super(componentRequestEngine, connectorInstance, connectorInstanceService);
    this.taskScheduler = taskScheduler;
    this.endpointService = endpointService;
    this.agentService = agentService;
    this.assetGroupService = assetGroupService;
    this.executorService = executorService;
    this.eeService = eeService;
    this.licenseCacheManager = licenseCacheManager;
    this.connectorInstanceService = connectorInstanceService;
    this.connectorInstance = connectorInstance;
    this.httpClientFactory = httpClientFactory;
    this.baseIntegrationConfigurationBuilder = baseIntegrationConfigurationBuilder;

    // Refresh the context to get the config
    try {
      refresh();
    } catch (Exception e) {
      log.error("Error during initialization of the CrowdStrike Executor", e);
      throw new ExecutorException(
          e, "Error during initialization of the Executor", CROWDSTRIKE_EXECUTOR_NAME);
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
            CROWDSTRIKE_EXECUTOR_TYPE,
            CROWDSTRIKE_EXECUTOR_NAME,
            CROWDSTRIKE_EXECUTOR_DOCUMENTATION_LINK,
            CROWDSTRIKE_EXECUTOR_BACKGROUND_COLOR,
            getClass().getResourceAsStream("/img/icon-crowdstrike.png"),
            getClass().getResourceAsStream("/img/banner-crowdstrike.png"),
            new String[] {
              Endpoint.PLATFORM_TYPE.Windows.name(),
              Endpoint.PLATFORM_TYPE.Linux.name(),
              Endpoint.PLATFORM_TYPE.MacOS.name()
            });

    client = new CrowdStrikeExecutorClient(config, httpClientFactory);
    crowdStrikeExecutorContextService =
        new CrowdStrikeExecutorContextService(
            config, client, eeService, licenseCacheManager, executorService);
    crowdStrikeExecutorService =
        new CrowdStrikeExecutorService(
            executor, client, config, endpointService, agentService, assetGroupService);
    crowdStrikeGarbageCollectorService =
        new CrowdStrikeGarbageCollectorService(
            config, crowdStrikeExecutorContextService, agentService);

    timers.add(
        taskScheduler.scheduleAtFixedRate(
            crowdStrikeExecutorService, Duration.ofSeconds(this.config.getApiRegisterInterval())));
    timers.add(
        taskScheduler.scheduleAtFixedRate(
            crowdStrikeGarbageCollectorService,
            Duration.ofHours(this.config.getCleanImplantInterval())));
  }

  @Override
  protected void refresh()
      throws JsonProcessingException,
          InvocationTargetException,
          NoSuchMethodException,
          InstantiationException,
          IllegalAccessException {
    this.config = baseIntegrationConfigurationBuilder.build(CrowdStrikeExecutorConfig.class);
    this.config.fromConnectorInstanceConfigurationSet(
        this.getConnectorInstance(), CrowdStrikeExecutorConfig.class);
  }

  @Override
  protected void innerStop() {
    timers.forEach(timer -> timer.cancel(true));
    timers.clear();
  }
}
