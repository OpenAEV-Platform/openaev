package io.openaev.integration.impl.executors.sentinelone;

import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Executor;
import io.openaev.ee.Ee;
import io.openaev.executors.ExecutorService;
import io.openaev.executors.sentinelone.client.SentinelOneExecutorClient;
import io.openaev.executors.sentinelone.config.SentinelOneExecutorConfig;
import io.openaev.executors.sentinelone.service.SentinelOneExecutorContextService;
import io.openaev.executors.sentinelone.service.SentinelOneExecutorService;
import io.openaev.executors.sentinelone.service.SentinelOneGarbageCollectorService;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.Integration;
import io.openaev.integration.QualifiedComponent;
import io.openaev.service.AgentService;
import io.openaev.service.AssetGroupService;
import io.openaev.service.EndpointService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

public class SentinelOneExecutorIntegration extends Integration {
  public static final String SENTINELONE_EXECUTOR_DEFAULT_ID =
      "b586bc98-839c-45bd-b9e4-c10830ebfefa";
  public static final String SENTINELONE_EXECUTOR_TYPE = "openaev_sentinelone";
  public static final String SENTINELONE_EXECUTOR_NAME = "SentinelOne";
  private static final String SENTINELONE_EXECUTOR_DOCUMENTATION_LINK =
      "https://docs.openaev.io/latest/deployment/ecosystem/executors/#sentinelone-agent";
  private static final String SENTINELONE_EXECUTOR_BACKGROUND_COLOR = "#6001FC";

  @QualifiedComponent(identifier = SENTINELONE_EXECUTOR_NAME)
  private SentinelOneExecutorContextService sentinelOneExecutorContextService;

  private SentinelOneExecutorService sentinelOneExecutorService;
  private SentinelOneGarbageCollectorService sentinelOneGarbageCollectorService;

  private final SentinelOneExecutorConfig config;
  private final SentinelOneExecutorClient client;
  private final AgentService agentService;
  private final EndpointService endpointService;
  private final AssetGroupService assetGroupService;
  private final ExecutorService executorService;
  private final Ee eeService;
  private final LicenseCacheManager licenseCacheManager;
  private final ThreadPoolTaskScheduler taskScheduler;

  private final List<ScheduledFuture<?>> timers = new ArrayList<>();

  public SentinelOneExecutorIntegration(
      ConnectorInstance connectorInstance,
      ConnectorInstanceService connectorInstanceService,
      SentinelOneExecutorClient client,
      SentinelOneExecutorConfig config,
      EndpointService endpointService,
      AgentService agentService,
      AssetGroupService assetGroupService,
      Ee eeService,
      LicenseCacheManager licenseCacheManager,
      ComponentRequestEngine componentRequestEngine,
      ExecutorService executorService,
      ThreadPoolTaskScheduler taskScheduler) {
    super(componentRequestEngine, connectorInstance, connectorInstanceService);
    this.client = client;
    this.config = config;
    this.endpointService = endpointService;
    this.agentService = agentService;
    this.assetGroupService = assetGroupService;
    this.eeService = eeService;
    this.licenseCacheManager = licenseCacheManager;
    this.executorService = executorService;
    this.taskScheduler = taskScheduler;
  }

  @Override
  protected void innerStart() throws Exception {
    Executor executor =
        executorService.register(
            config.getId(),
            SENTINELONE_EXECUTOR_TYPE,
            SENTINELONE_EXECUTOR_NAME,
            SENTINELONE_EXECUTOR_DOCUMENTATION_LINK,
            SENTINELONE_EXECUTOR_BACKGROUND_COLOR,
            getClass().getResourceAsStream("/img/icon-sentinelone.png"),
            getClass().getResourceAsStream("/img/banner-sentinelone.png"),
            new String[] {
              Endpoint.PLATFORM_TYPE.Windows.name(),
              Endpoint.PLATFORM_TYPE.Linux.name(),
              Endpoint.PLATFORM_TYPE.MacOS.name()
            });

    sentinelOneExecutorContextService =
        new SentinelOneExecutorContextService(
            config, client, eeService, licenseCacheManager, executorService);
    sentinelOneExecutorService =
        new SentinelOneExecutorService(
            executor, client, endpointService, agentService, assetGroupService);
    sentinelOneGarbageCollectorService =
        new SentinelOneGarbageCollectorService(
            config, sentinelOneExecutorContextService, agentService);

    timers.add(
        taskScheduler.scheduleAtFixedRate(
            sentinelOneExecutorService, Duration.ofSeconds(this.config.getApiRegisterInterval())));
    timers.add(
        taskScheduler.scheduleAtFixedRate(
            sentinelOneGarbageCollectorService,
            Duration.ofHours(this.config.getCleanImplantInterval())));
  }

  @Override
  protected void innerStop() {
    timers.forEach(timer -> timer.cancel(true));
  }
}
