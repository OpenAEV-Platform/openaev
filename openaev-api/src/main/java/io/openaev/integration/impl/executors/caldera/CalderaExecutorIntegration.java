package io.openaev.integration.impl.executors.caldera;

import io.openaev.database.model.*;
import io.openaev.executors.ExecutorService;
import io.openaev.executors.caldera.client.CalderaExecutorClient;
import io.openaev.executors.caldera.config.CalderaExecutorConfig;
import io.openaev.executors.caldera.service.CalderaExecutorContextService;
import io.openaev.executors.caldera.service.CalderaExecutorService;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.Integration;
import io.openaev.integration.QualifiedComponent;
import io.openaev.integrations.InjectorService;
import io.openaev.service.AgentService;
import io.openaev.service.EndpointService;
import io.openaev.service.PlatformSettingsService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

public class CalderaExecutorIntegration extends Integration {
  public static final String CALDERA_EXECUTOR_DEFAULT_ID = "20696a66-5780-4cbe-b5c1-be43efddb3f7";
  public static final String CALDERA_EXECUTOR_TYPE = "openaev_caldera";
  public static final String CALDERA_EXECUTOR_NAME = "Caldera";
  public static final String CALDERA_BACKGROUND_COLOR = "#8B1316";

  @QualifiedComponent(identifier = CALDERA_EXECUTOR_NAME)
  private CalderaExecutorContextService calderaExecutorContextService;

  private CalderaExecutorService calderaExecutorService;

  private final CalderaExecutorConfig config;
  private final CalderaExecutorClient client;
  private final AgentService agentService;
  private final EndpointService endpointService;
  private final InjectorService injectorService;
  private final PlatformSettingsService platformSettingsService;
  private final ExecutorService executorService;
  private final ThreadPoolTaskScheduler taskScheduler;

  private final List<ScheduledFuture<?>> timers = new ArrayList<>();

  public CalderaExecutorIntegration(
      ConnectorInstance connectorInstance,
      ConnectorInstanceService connectorInstanceService,
      CalderaExecutorClient client,
      CalderaExecutorConfig config,
      EndpointService endpointService,
      AgentService agentService,
      ExecutorService executorService,
      ComponentRequestEngine componentRequestEngine,
      PlatformSettingsService platformSettingsService,
      InjectorService injectorService,
      ThreadPoolTaskScheduler taskScheduler) {
    super(componentRequestEngine, connectorInstance, connectorInstanceService);
    this.client = client;
    this.config = config;
    this.endpointService = endpointService;
    this.agentService = agentService;
    this.platformSettingsService = platformSettingsService;
    this.injectorService = injectorService;
    this.taskScheduler = taskScheduler;
    this.executorService = executorService;
  }

  @Override
  protected void innerStart() throws Exception {
    Executor executor =
        executorService.register(
            config.getId(),
            CALDERA_EXECUTOR_TYPE,
            CALDERA_EXECUTOR_NAME,
            null,
            CALDERA_BACKGROUND_COLOR,
            getClass().getResourceAsStream("/img/icon-caldera.png"),
            getClass().getResourceAsStream("/img/banner-caldera.png"),
            new String[] {
              Endpoint.PLATFORM_TYPE.Windows.name(),
              Endpoint.PLATFORM_TYPE.Linux.name(),
              Endpoint.PLATFORM_TYPE.MacOS.name()
            });

    calderaExecutorContextService =
        new CalderaExecutorContextService(config, injectorService, client);
    calderaExecutorService =
        new CalderaExecutorService(
            executor,
            client,
            config,
            calderaExecutorContextService,
            endpointService,
            injectorService,
            platformSettingsService,
            agentService);

    calderaExecutorContextService.registerAbilities();

    timers.add(
        this.taskScheduler.scheduleAtFixedRate(calderaExecutorService, Duration.ofSeconds(60)));
  }

  @Override
  protected void innerStop() {
    this.platformSettingsService.cleanMessage(BannerMessage.BANNER_KEYS.CALDERA_UNAVAILABLE);
    timers.forEach(timer -> timer.cancel(true));
  }
}
