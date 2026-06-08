package io.openaev.integration.impl.secrets.vault;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.openaev.authorisation.HttpClientFactory;
import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.Integration;
import io.openaev.integration.annotation.QualifiedComponent;
import io.openaev.integration.configuration.BaseIntegrationConfigurationBuilder;
import io.openaev.secrets.provider.impl.vault.VaultSecretProviderConfig;
import io.openaev.secrets.provider.impl.vault.VaultSecretsProvider;
import io.openaev.secrets.provider.impl.vault.api.VaultClient;
import io.openaev.secrets.provider.impl.vault.scheduler.VaultSecretsSyncJob;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Slf4j
public class VaultSecretProviderIntegration extends Integration {

  @QualifiedComponent(identifier = "secrets-provider")
  @QualifiedComponent(identifier = "hashicorp_vault_secrets_provider")
  private VaultSecretsProvider vaultSecretsProvider;

  private VaultSecretsSyncJob vaultSecretsSyncJob;

  private final List<ScheduledFuture<?>> timers = new ArrayList<>();

  private VaultClient client;
  private VaultSecretProviderConfig config;
  private final EnterpriseEditionService enterpriseEditionService;
  private final LicenseCacheManager licenseCacheManager;
  private final ThreadPoolTaskScheduler taskScheduler;
  private final ConnectorInstanceService connectorInstanceService;
  private final HttpClientFactory httpClientFactory;
  private final BaseIntegrationConfigurationBuilder baseIntegrationConfigurationBuilder;

  public VaultSecretProviderIntegration(
      ConnectorInstance connectorInstance,
      ConnectorInstanceService connectorInstanceService,
      EnterpriseEditionService enterpriseEditionService,
      LicenseCacheManager licenseCacheManager,
      ComponentRequestEngine componentRequestEngine,
      ThreadPoolTaskScheduler taskScheduler,
      BaseIntegrationConfigurationBuilder baseIntegrationConfigurationBuilder,
      HttpClientFactory httpClientFactory)
      throws Exception {
    super(componentRequestEngine, connectorInstance, connectorInstanceService);
    this.taskScheduler = taskScheduler;
    this.enterpriseEditionService = enterpriseEditionService;
    this.licenseCacheManager = licenseCacheManager;
    this.connectorInstanceService = connectorInstanceService;
    this.httpClientFactory = httpClientFactory;
    this.baseIntegrationConfigurationBuilder = baseIntegrationConfigurationBuilder;

    refresh();
  }

  @Override
  protected void innerStart() throws Exception {
    client = new VaultClient(httpClientFactory, config);
    vaultSecretsProvider = new VaultSecretsProvider(client, config);
    vaultSecretsSyncJob = new VaultSecretsSyncJob(vaultSecretsProvider);
    timers.add(
        taskScheduler.scheduleAtFixedRate(
            vaultSecretsSyncJob, Duration.ofSeconds(this.config.getSecretsRefreshInterval())));
  }

  @Override
  protected void refresh()
      throws JsonProcessingException,
          InvocationTargetException,
          NoSuchMethodException,
          InstantiationException,
          IllegalAccessException {
    this.config = baseIntegrationConfigurationBuilder.build(VaultSecretProviderConfig.class);
    this.config.fromConnectorInstanceConfigurationSet(
        this.getConnectorInstance(), VaultSecretProviderConfig.class);
  }

  @Override
  protected void innerStop() {
    timers.forEach(timer -> timer.cancel(true));
    timers.clear();
  }
}
