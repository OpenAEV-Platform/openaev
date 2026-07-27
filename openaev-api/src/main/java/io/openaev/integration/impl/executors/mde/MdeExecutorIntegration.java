package io.openaev.integration.impl.executors.mde;

import static java.util.Optional.ofNullable;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.openaev.authorisation.HttpClientFactory;
import io.openaev.config.OpenAEVConfig;
import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorType;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Executor;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.executors.ExecutorService;
import io.openaev.executors.exception.ExecutorException;
import io.openaev.executors.mde.client.MdeExecutorClient;
import io.openaev.executors.mde.config.MdeExecutorConfig;
import io.openaev.executors.mde.service.MdeExecutorContextService;
import io.openaev.executors.mde.service.MdeExecutorService;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.Integration;
import io.openaev.integration.annotation.QualifiedComponent;
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
public class MdeExecutorIntegration extends Integration {

  public static final String MDE_EXECUTOR_DEFAULT_ID = "f9c4a0e1-3b7d-4f8a-9e2c-1d5b6a0f8e3c";
  public static final String MDE_EXECUTOR_TYPE = "openaev_mde_executor";
  public static final String MDE_EXECUTOR_NAME = "Microsoft Defender for Endpoint";
  private static final String MDE_EXECUTOR_DOCUMENTATION_LINK =
      "https://docs.openaev.io/latest/deployment/ecosystem/executors/#microsoft-defender-for-endpoint";
  private static final String MDE_EXECUTOR_BACKGROUND_COLOR = "#0078D4";

  @QualifiedComponent(identifier = MdeExecutorContextService.SERVICE_NAME)
  private MdeExecutorContextService mdeExecutorContextService;

  private MdeExecutorService mdeExecutorService;

  private final List<ScheduledFuture<?>> timers = new ArrayList<>();

  private MdeExecutorClient client;
  private MdeExecutorConfig config;
  private final EndpointService endpointService;
  private final AgentService agentService;
  private final AssetGroupService assetGroupService;
  private final ExecutorService executorService;
  private final EnterpriseEditionService enterpriseEditionService;
  private final LicenseCacheManager licenseCacheManager;
  private final ThreadPoolTaskScheduler taskScheduler;
  private final ConnectorInstanceService connectorInstanceService;
  private final HttpClientFactory httpClientFactory;
  private final BaseIntegrationConfigurationBuilder baseIntegrationConfigurationBuilder;
  private final OpenAEVConfig openAEVConfig;
  private final TenantScopedTransaction tenantTx;

  public MdeExecutorIntegration(
      ConnectorInstance connectorInstance,
      ConnectorInstanceService connectorInstanceService,
      EndpointService endpointService,
      AgentService agentService,
      AssetGroupService assetGroupService,
      ExecutorService executorService,
      EnterpriseEditionService enterpriseEditionService,
      LicenseCacheManager licenseCacheManager,
      ComponentRequestEngine componentRequestEngine,
      ThreadPoolTaskScheduler taskScheduler,
      BaseIntegrationConfigurationBuilder baseIntegrationConfigurationBuilder,
      HttpClientFactory httpClientFactory,
      OpenAEVConfig openAEVConfig,
      TenantScopedTransaction tenantTx) {
    super(componentRequestEngine, connectorInstance, connectorInstanceService);
    this.taskScheduler = taskScheduler;
    this.endpointService = endpointService;
    this.agentService = agentService;
    this.assetGroupService = assetGroupService;
    this.executorService = executorService;
    this.enterpriseEditionService = enterpriseEditionService;
    this.licenseCacheManager = licenseCacheManager;
    this.connectorInstanceService = connectorInstanceService;
    this.httpClientFactory = httpClientFactory;
    this.baseIntegrationConfigurationBuilder = baseIntegrationConfigurationBuilder;
    this.openAEVConfig = openAEVConfig;
    this.tenantTx = tenantTx;

    try {
      refresh();
    } catch (Exception e) {
      log.error("Error during initialization of the MDE Executor", e);
      throw new ExecutorException(
          e, "Error during initialization of the Executor", MDE_EXECUTOR_NAME);
    }
  }

  @Override
  protected void innerStart() throws Exception {
    String instanceId = getConnectorInstance().getId();
    String executorId =
        connectorInstanceService.getConnectorInstanceConfigurationsByIdAndKey(
            instanceId, ConnectorType.EXECUTOR.getIdKeyName());
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
            MDE_EXECUTOR_TYPE,
            executorName,
            MDE_EXECUTOR_DOCUMENTATION_LINK,
            MDE_EXECUTOR_BACKGROUND_COLOR,
            getClass().getResourceAsStream("/img/icon-mde.png"),
            getClass().getResourceAsStream("/img/banner-mde.png"),
            new String[] {
              Endpoint.PLATFORM_TYPE.Windows.name(),
              Endpoint.PLATFORM_TYPE.Linux.name(),
              Endpoint.PLATFORM_TYPE.MacOS.name()
            });

    client = new MdeExecutorClient(config, httpClientFactory);
    mdeExecutorContextService =
        new MdeExecutorContextService(
            config,
            client,
            enterpriseEditionService,
            licenseCacheManager,
            executorService,
            openAEVConfig);
    mdeExecutorService =
        new MdeExecutorService(
            executor, client, config, endpointService, agentService, assetGroupService, tenantTx);

    Integer registerInterval = this.config.getApiRegisterInterval();
    long registerIntervalSeconds =
        registerInterval != null
            ? registerInterval
            : MdeExecutorConfig.DEFAULT_API_REGISTER_INTERVAL;
    timers.add(
        taskScheduler.scheduleAtFixedRate(
            mdeExecutorService, Duration.ofSeconds(registerIntervalSeconds)));
  }

  @Override
  protected void refresh()
      throws JsonProcessingException,
          InvocationTargetException,
          NoSuchMethodException,
          InstantiationException,
          IllegalAccessException {
    this.config = baseIntegrationConfigurationBuilder.build(MdeExecutorConfig.class);
    this.config.fromConnectorInstanceConfigurationSet(
        this.getConnectorInstance(), MdeExecutorConfig.class);
  }

  @Override
  protected void innerStop() {
    timers.forEach(timer -> timer.cancel(true));
    timers.clear();
  }
}
