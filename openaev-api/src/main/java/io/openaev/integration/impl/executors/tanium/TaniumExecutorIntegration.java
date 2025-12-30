package io.openaev.integration.impl.executors.tanium;

import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Executor;
import io.openaev.ee.Ee;
import io.openaev.executors.ExecutorService;
import io.openaev.executors.tanium.client.TaniumExecutorClient;
import io.openaev.executors.tanium.config.TaniumExecutorConfig;
import io.openaev.executors.tanium.service.TaniumExecutorContextService;
import io.openaev.executors.tanium.service.TaniumExecutorService;
import io.openaev.executors.tanium.service.TaniumGarbageCollectorService;
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

public class TaniumExecutorIntegration extends Integration {
  public static final String TANIUM_EXECUTOR_DEFAULT_ID = "722ddfb1-6c3b-4b97-91e3-9f606d05892e";
  public static final String TANIUM_EXECUTOR_TYPE = "openaev_tanium";
  public static final String TANIUM_EXECUTOR_NAME = "Tanium";
  private static final String TANIUM_EXECUTOR_DOCUMENTATION_LINK =
      "https://docs.openaev.io/latest/deployment/ecosystem/executors/#tanium-agent";
  private static final String TANIUM_EXECUTOR_BACKGROUND_COLOR = "#E03E41";

  @QualifiedComponent(identifier = TANIUM_EXECUTOR_NAME)
  private TaniumExecutorContextService taniumExecutorContextService;

  private TaniumExecutorService taniumExecutorService;
  private TaniumGarbageCollectorService taniumGarbageCollectorService;

  private final TaniumExecutorConfig config;
  private final TaniumExecutorClient client;
  private final AgentService agentService;
  private final EndpointService endpointService;
  private final AssetGroupService assetGroupService;
  private final ExecutorService executorService;
  private final Ee eeService;
  private final LicenseCacheManager licenseCacheManager;
  private final ThreadPoolTaskScheduler taskScheduler;

  private final List<ScheduledFuture<?>> timers = new ArrayList<>();

  public TaniumExecutorIntegration(
      ConnectorInstance connectorInstance,
      ConnectorInstanceService connectorInstanceService,
      TaniumExecutorClient client,
      TaniumExecutorConfig config,
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
            TANIUM_EXECUTOR_TYPE,
            TANIUM_EXECUTOR_NAME,
            TANIUM_EXECUTOR_DOCUMENTATION_LINK,
            TANIUM_EXECUTOR_BACKGROUND_COLOR,
            getClass().getResourceAsStream("/img/icon-tanium.png"),
            getClass().getResourceAsStream("/img/banner-tanium.png"),
            new String[] {
              Endpoint.PLATFORM_TYPE.Windows.name(),
              Endpoint.PLATFORM_TYPE.Linux.name(),
              Endpoint.PLATFORM_TYPE.MacOS.name()
            });

    taniumExecutorContextService =
        new TaniumExecutorContextService(
            eeService, licenseCacheManager, config, client, executorService);
    taniumExecutorService =
        new TaniumExecutorService(
            executor, client, config, endpointService, agentService, assetGroupService);
    taniumGarbageCollectorService =
        new TaniumGarbageCollectorService(config, taniumExecutorContextService, agentService);

    timers.add(
        taskScheduler.scheduleAtFixedRate(
            taniumExecutorService, Duration.ofSeconds(this.config.getApiRegisterInterval())));
    timers.add(
        taskScheduler.scheduleAtFixedRate(
            taniumGarbageCollectorService,
            Duration.ofHours(this.config.getCleanImplantInterval())));
  }

  @Override
  protected void innerStop() {
    timers.forEach(timer -> timer.cancel(true));
  }
}
