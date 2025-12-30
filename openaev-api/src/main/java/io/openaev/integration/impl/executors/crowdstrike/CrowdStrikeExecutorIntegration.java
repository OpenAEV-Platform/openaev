package io.openaev.integration.impl.executors.crowdstrike;

import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Executor;
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
import io.openaev.service.connector_instances.ConnectorInstanceService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

public class CrowdStrikeExecutorIntegration extends Integration {
  public static final String CROWDSTRIKE_EXECUTOR_DEFAULT_ID =
      "b522d9bc-7ed6-44ac-9984-810dfb18f7be";
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
  private final ThreadPoolTaskScheduler taskScheduler;

  public CrowdStrikeExecutorIntegration(
      ConnectorInstance connectorInstance,
      ConnectorInstanceService connectorInstanceService,
      CrowdStrikeExecutorClient client,
      CrowdStrikeExecutorConfig config,
      EndpointService endpointService,
      AgentService agentService,
      AssetGroupService assetGroupService,
      ExecutorService executorService,
      Ee eeService,
      LicenseCacheManager licenseCacheManager,
      ComponentRequestEngine componentRequestEngine,
      ThreadPoolTaskScheduler taskScheduler) {
    super(componentRequestEngine, connectorInstance, connectorInstanceService);
    this.taskScheduler = taskScheduler;
    this.client = client;
    this.config = config;
    this.endpointService = endpointService;
    this.agentService = agentService;
    this.assetGroupService = assetGroupService;
    this.executorService = executorService;
    this.eeService = eeService;
    this.licenseCacheManager = licenseCacheManager;
  }

  @Override
  protected void innerStart() throws Exception {
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
  protected void innerStop() {
    timers.forEach(timer -> timer.cancel(true));
  }
}
