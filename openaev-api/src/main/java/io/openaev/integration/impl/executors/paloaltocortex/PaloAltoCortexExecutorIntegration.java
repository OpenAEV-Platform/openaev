package io.openaev.integration.impl.executors.paloaltocortex;

import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Executor;
import io.openaev.ee.Ee;
import io.openaev.executors.ExecutorService;
import io.openaev.executors.paloaltocortex.client.PaloAltoCortexExecutorClient;
import io.openaev.executors.paloaltocortex.config.PaloAltoCortexExecutorConfig;
import io.openaev.executors.paloaltocortex.service.PaloAltoCortexExecutorContextService;
import io.openaev.executors.paloaltocortex.service.PaloAltoCortexExecutorService;
import io.openaev.executors.paloaltocortex.service.PaloAltoCortexGarbageCollectorService;
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

public class PaloAltoCortexExecutorIntegration extends Integration {
  public static final String PALOALTOCORTEX_EXECUTOR_DEFAULT_ID =
      "2177ceeb-a9e2-4a33-bf30-1bf7c47f150a";
  public static final String PALOALTOCORTEX_EXECUTOR_TYPE = "openaev_paloaltocortex";
  public static final String PALOALTOCORTEX_EXECUTOR_NAME = "PaloAltoCortex";
  private static final String PALOALTOCORTEX_EXECUTOR_DOCUMENTATION_LINK =
      "https://docs.openaev.io/latest/deployment/ecosystem/executors/#paloaltocortex-agent";
  private static final String PALOALTOCORTEX_EXECUTOR_BACKGROUND_COLOR = "#00CC66";

  @QualifiedComponent(identifier = PALOALTOCORTEX_EXECUTOR_NAME)
  private PaloAltoCortexExecutorContextService paloAltoCortexExecutorContextService;

  private PaloAltoCortexExecutorService paloAltoCortexExecutorService;
  private PaloAltoCortexGarbageCollectorService paloAltoCortexGarbageCollectorService;

  private final PaloAltoCortexExecutorConfig config;
  private final PaloAltoCortexExecutorClient client;
  private final AgentService agentService;
  private final EndpointService endpointService;
  private final AssetGroupService assetGroupService;
  private final ExecutorService executorService;
  private final Ee eeService;
  private final LicenseCacheManager licenseCacheManager;
  private final ThreadPoolTaskScheduler taskScheduler;

  private final List<ScheduledFuture<?>> timers = new ArrayList<>();

  public PaloAltoCortexExecutorIntegration(
      ConnectorInstance connectorInstance,
      ConnectorInstanceService connectorInstanceService,
      PaloAltoCortexExecutorClient client,
      PaloAltoCortexExecutorConfig config,
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

    paloAltoCortexExecutorContextService =
        new PaloAltoCortexExecutorContextService(
            config, client, eeService, licenseCacheManager, executorService);
    paloAltoCortexExecutorService =
        new PaloAltoCortexExecutorService(
            executor, client, endpointService, agentService, assetGroupService);
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
  protected void innerStop() {
    timers.forEach(timer -> timer.cancel(true));
  }
}
