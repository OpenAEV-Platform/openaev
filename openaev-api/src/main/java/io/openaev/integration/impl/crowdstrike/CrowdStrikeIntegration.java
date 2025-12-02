package io.openaev.integration.impl.crowdstrike;

import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Executor;
import io.openaev.database.repository.ExecutionTraceRepository;
import io.openaev.ee.Ee;
import io.openaev.executors.ExecutorService;
import io.openaev.executors.crowdstrike.client.CrowdStrikeExecutorClient;
import io.openaev.executors.crowdstrike.config.CrowdStrikeExecutorConfig;
import io.openaev.executors.crowdstrike.service.CrowdStrikeExecutorContextService;
import io.openaev.executors.crowdstrike.service.CrowdStrikeExecutorService;
import io.openaev.executors.crowdstrike.service.CrowdStrikeGarbageCollectorService;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.Integration;
import io.openaev.integration.QualifiedComponent;
import io.openaev.service.AgentService;
import io.openaev.service.AssetGroupService;
import io.openaev.service.EndpointService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

public class CrowdStrikeIntegration extends Integration {
  public static final String CROWDSTRIKE_EXECUTOR_TYPE = "openaev_crowdstrike";
  public static final String CROWDSTRIKE_EXECUTOR_NAME = "CrowdStrike";
  private static final String CROWDSTRIKE_EXECUTOR_DOCUMENTATION_LINK =
      "https://docs.openaev.io/latest/deployment/ecosystem/executors/#crowdstrike-falcon-agent";

  private static final String CROWDSTRIKE_EXECUTOR_BACKGROUND_COLOR = "#E12E37";

  @QualifiedComponent(identifier = CrowdStrikeExecutorContextService.SERVICE_NAME)
  private CrowdStrikeExecutorContextService crowdStrikeExecutorContextService;

  private CrowdStrikeExecutorService crowdStrikeExecutorService;
  private CrowdStrikeGarbageCollectorService crowdStrikeGarbageCollectorService;

  private final List<ScheduledFuture<?>> timers = new ArrayList<>();

  private final CrowdStrikeExecutorClient client;
  private final CrowdStrikeExecutorConfig config;
  private final EndpointService endpointService;
  private final AgentService agentService;
  private final AssetGroupService assetGroupService;
  private final ExecutorService executorService;
  private final Ee eeService;
  private final LicenseCacheManager licenseCacheManager;
  private final ExecutionTraceRepository executionTraceRepository;
  private final ThreadPoolTaskScheduler taskScheduler;

  public CrowdStrikeIntegration(
      ConnectorInstance connectorInstance,
      CrowdStrikeExecutorClient client,
      CrowdStrikeExecutorConfig config,
      EndpointService endpointService,
      AgentService agentService,
      AssetGroupService assetGroupService,
      ExecutorService executorService,
      Ee eeService,
      LicenseCacheManager licenseCacheManager,
      ExecutionTraceRepository executionTraceRepository,
      ComponentRequestEngine componentRequestEngine,
      ThreadPoolTaskScheduler taskScheduler) {
    super(componentRequestEngine, connectorInstance);
    this.taskScheduler = taskScheduler;
    this.client = client;
    this.config = config;
    this.endpointService = endpointService;
    this.agentService = agentService;
    this.assetGroupService = assetGroupService;
    this.executorService = executorService;
    this.eeService = eeService;
    this.licenseCacheManager = licenseCacheManager;
    this.executionTraceRepository = executionTraceRepository;
  }

  @Override
  public void start() throws Exception {
    Executor executor =
        executorService.register(
            config.getId(),
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

    crowdStrikeExecutorService =
        new CrowdStrikeExecutorService(
            executor, client, config, endpointService, agentService, assetGroupService);
    crowdStrikeGarbageCollectorService =
        new CrowdStrikeGarbageCollectorService(config, client, agentService);
    crowdStrikeExecutorContextService =
        new CrowdStrikeExecutorContextService(
            config, client, eeService, licenseCacheManager, executionTraceRepository);

    timers.add(
        taskScheduler.scheduleAtFixedRate(
            crowdStrikeExecutorService, Duration.ofSeconds(this.config.getApiRegisterInterval())));
    timers.add(
        taskScheduler.scheduleAtFixedRate(crowdStrikeGarbageCollectorService, Duration.ofHours(6)));
  }

  @Override
  public void stop() {
    executorService.remove(config.getId());
    timers.forEach(timer -> timer.cancel(true));
  }
}
