package io.openaev.integration.impl.executors.caldera;

import static java.util.Optional.ofNullable;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.openaev.authorisation.HttpClientFactory;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.*;
import io.openaev.executors.ExecutorService;
import io.openaev.executors.caldera.client.CalderaExecutorClient;
import io.openaev.executors.caldera.config.CalderaExecutorConfig;
import io.openaev.executors.caldera.service.CalderaExecutorContextService;
import io.openaev.executors.caldera.service.CalderaExecutorService;
import io.openaev.executors.exception.ExecutorException;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.Integration;
import io.openaev.integration.annotation.QualifiedComponent;
import io.openaev.integration.configuration.BaseIntegrationConfigurationBuilder;
import io.openaev.service.AgentService;
import io.openaev.service.EndpointService;
import io.openaev.service.InjectorService;
import io.openaev.service.PlatformSettingsService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
public class CalderaExecutorIntegration extends Integration {
  public static final String CALDERA_EXECUTOR_DEFAULT_ID = "20696a66-5780-4cbe-b5c1-be43efddb3f7";
  public static final String CALDERA_EXECUTOR_TYPE = "openaev_caldera_executor";
  public static final String CALDERA_EXECUTOR_NAME = "Caldera";
  public static final String CALDERA_BACKGROUND_COLOR = "#8B1316";

  @QualifiedComponent(identifier = CALDERA_EXECUTOR_NAME)
  private CalderaExecutorContextService calderaExecutorContextService;

  private CalderaExecutorService calderaExecutorService;

  private CalderaExecutorConfig config;
  private CalderaExecutorClient client;
  private final AgentService agentService;
  private final EndpointService endpointService;
  private final InjectorService injectorService;
  private final PlatformSettingsService platformSettingsService;
  private final ExecutorService executorService;
  private final ThreadPoolTaskScheduler taskScheduler;
  private final ConnectorInstanceService connectorInstanceService;
  private final HttpClientFactory httpClientFactory;
  private final BaseIntegrationConfigurationBuilder baseIntegrationConfigurationBuilder;
  private final TenantScopedTransaction tenantTx;

  private final List<ScheduledFuture<?>> timers = new ArrayList<>();

  /**
   * The source under which the executor reports to the Caldera unavailable banner, kept at start:
   * the connector instance is gone when stop runs.
   */
  private String bannerSource;

  public CalderaExecutorIntegration(
      ConnectorInstance connectorInstance,
      ConnectorInstanceService connectorInstanceService,
      EndpointService endpointService,
      AgentService agentService,
      ExecutorService executorService,
      ComponentRequestEngine componentRequestEngine,
      PlatformSettingsService platformSettingsService,
      InjectorService injectorService,
      ThreadPoolTaskScheduler taskScheduler,
      BaseIntegrationConfigurationBuilder baseIntegrationConfigurationBuilder,
      HttpClientFactory httpClientFactory,
      TenantScopedTransaction tenantTx) {
    super(componentRequestEngine, connectorInstance, connectorInstanceService);
    this.endpointService = endpointService;
    this.agentService = agentService;
    this.platformSettingsService = platformSettingsService;
    this.injectorService = injectorService;
    this.taskScheduler = taskScheduler;
    this.executorService = executorService;
    this.connectorInstanceService = connectorInstanceService;
    this.httpClientFactory = httpClientFactory;
    this.baseIntegrationConfigurationBuilder = baseIntegrationConfigurationBuilder;
    this.tenantTx = tenantTx;

    // Refresh the context to get the config
    try {
      refresh();
    } catch (Exception e) {
      log.error("Error during initialization of the Caldera Executor", e);
      throw new ExecutorException(
          e, "Error during initialization of the Executor", CALDERA_EXECUTOR_NAME);
    }
  }

  @Override
  protected void innerStart() throws Exception {
    String executorId =
        connectorInstanceService.getConnectorInstanceConfigurationsByIdAndKey(
            getConnectorInstance().getId(), ConnectorType.EXECUTOR.getIdKeyName());
    String executorName =
        ofNullable(
                connectorInstanceService.getConnectorInstanceConfigurationsByIdAndKey(
                    getConnectorInstance().getId(), "EXECUTOR_NAME"))
            .orElseThrow(
                () ->
                    new ExecutorException(
                        "EXECUTOR_NAME configuration is required for the Executor",
                        getConnectorInstance().getId()));

    Executor executor =
        executorService.register(
            getTenantId(),
            executorId,
            CALDERA_EXECUTOR_TYPE,
            executorName,
            null,
            CALDERA_BACKGROUND_COLOR,
            getClass().getResourceAsStream("/img/icon-caldera.png"),
            getClass().getResourceAsStream("/img/banner-caldera.png"),
            new String[] {
              Endpoint.PLATFORM_TYPE.Windows.name(),
              Endpoint.PLATFORM_TYPE.Linux.name(),
              Endpoint.PLATFORM_TYPE.MacOS.name()
            });

    bannerSource = CalderaExecutorService.bannerSource(executor);
    client = new CalderaExecutorClient(config, httpClientFactory);
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
            agentService,
            tenantTx,
            taskScheduler);

    calderaExecutorContextService.registerAbilities();

    timers.add(
        this.taskScheduler.scheduleAtFixedRate(calderaExecutorService, Duration.ofSeconds(60)));
  }

  @Override
  protected void refresh()
      throws JsonProcessingException,
          InvocationTargetException,
          NoSuchMethodException,
          InstantiationException,
          IllegalAccessException {
    this.config = baseIntegrationConfigurationBuilder.build(CalderaExecutorConfig.class);
    this.config.fromConnectorInstanceConfigurationSet(
        this.getConnectorInstance(), CalderaExecutorConfig.class);
  }

  @Override
  protected void innerStop() {
    // Same platform-wide operational status as CalderaExecutorService's banner (see its javadoc).
    // The monitoring job stops an integration inside its tenant transaction, which holds a
    // connection: there the write is handed to the scheduler and opens its own transaction, so
    // stopping never waits for a second connection. A shutdown or a test stops it outside any
    // transaction, where the write runs at once. Either way a failed write never fails the stop.
    // This executor no longer reports: the banner is rewritten from the other executors' reports.
    if (bannerSource != null) {
      Runnable clearBanner = () -> clearBanner(bannerSource);
      if (TransactionSynchronizationManager.isActualTransactionActive()) {
        try {
          taskScheduler.execute(clearBanner);
        } catch (RuntimeException e) {
          log.warn(
              "Could not schedule the Caldera unavailable banner clearing: {}", e.getMessage(), e);
        }
      } else {
        clearBanner.run();
      }
    }
    timers.forEach(timer -> timer.cancel(true));
    timers.clear();
  }

  private void clearBanner(String source) {
    try {
      tenantTx.executeAsPlatformWrite(
          TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID),
          () ->
              this.platformSettingsService.reportMessageSource(
                  BannerMessage.BANNER_KEYS.CALDERA_UNAVAILABLE, source, false));
    } catch (Exception e) {
      log.warn("Failed to clear the Caldera unavailable banner: {}", e.getMessage(), e);
    }
  }
}
