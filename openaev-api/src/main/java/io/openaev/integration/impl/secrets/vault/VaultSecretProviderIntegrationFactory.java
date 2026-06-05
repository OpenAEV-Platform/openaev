package io.openaev.integration.impl.secrets.vault;

import io.openaev.authorisation.HttpClientFactory;
import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorType;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.executors.ExecutorService;
import io.openaev.executors.crowdstrike.config.CrowdStrikeExecutorConfig;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.Integration;
import io.openaev.integration.IntegrationFactory;
import io.openaev.integration.configuration.BaseIntegrationConfigurationBuilder;
import io.openaev.integration.migration.CrowdStrikeExecutorConfigurationMigration;
import io.openaev.service.AgentService;
import io.openaev.service.AssetGroupService;
import io.openaev.service.EndpointService;
import io.openaev.service.FileService;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

@Service
@Profile("!test")
@Slf4j
public class VaultSecretProviderIntegrationFactory extends IntegrationFactory {
  private final EndpointService endpointService;
  private final AgentService agentService;
  private final AssetGroupService assetGroupService;
  private final ExecutorService executorService;
  private final EnterpriseEditionService enterpriseEditionService;
  private final LicenseCacheManager licenseCacheManager;
  private final ComponentRequestEngine componentRequestEngine;
  private final ThreadPoolTaskScheduler taskScheduler;
  private final CatalogConnectorService catalogConnectorService;
  private final ConnectorInstanceService connectorInstanceService;
  private final CrowdStrikeExecutorConfigurationMigration crowdStrikeExecutorConfigurationMigration;
  private final FileService fileService;
  private final BaseIntegrationConfigurationBuilder baseIntegrationConfigurationBuilder;

  public VaultSecretProviderIntegrationFactory(
      ConnectorInstanceService connectorInstanceService,
      CatalogConnectorService catalogConnectorService,
      EndpointService endpointService,
      AgentService agentService,
      AssetGroupService assetGroupService,
      ExecutorService executorService,
      EnterpriseEditionService enterpriseEditionService,
      LicenseCacheManager licenseCacheManager,
      ComponentRequestEngine componentRequestEngine,
      ThreadPoolTaskScheduler taskScheduler,
      CrowdStrikeExecutorConfigurationMigration crowdStrikeExecutorConfigurationMigration,
      FileService fileService,
      BaseIntegrationConfigurationBuilder baseIntegrationConfigurationBuilder,
      HttpClientFactory httpClientFactory) {
    super(connectorInstanceService, catalogConnectorService, httpClientFactory);
    this.endpointService = endpointService;
    this.agentService = agentService;
    this.assetGroupService = assetGroupService;
    this.executorService = executorService;
    this.enterpriseEditionService = enterpriseEditionService;
    this.licenseCacheManager = licenseCacheManager;
    this.componentRequestEngine = componentRequestEngine;
    this.taskScheduler = taskScheduler;
    this.catalogConnectorService = catalogConnectorService;
    this.connectorInstanceService = connectorInstanceService;
    this.crowdStrikeExecutorConfigurationMigration = crowdStrikeExecutorConfigurationMigration;
    this.fileService = fileService;
    this.baseIntegrationConfigurationBuilder = baseIntegrationConfigurationBuilder;
  }

  @Override
  protected final String getClassName() {
    return VaultSecretProviderIntegrationFactory.class.getCanonicalName();
  }

  @Override
  protected void runMigrations() throws Exception {
    crowdStrikeExecutorConfigurationMigration.migrate();
  }

  @Override
  protected void insertCatalogEntry() throws Exception {
    String logoFilename = "%s-logo.png".formatted("hashicorp-vault");
    fileService.uploadStream(
        FileService.CONNECTORS_LOGO_PATH,
        logoFilename,
        getClass().getResourceAsStream("/img/icon-hashicorp-vault.png"));
    CatalogConnector connector = new CatalogConnector();
    connector.setTitle("HashiCorp Vault");
    connector.setSlug("hashicorp_vault_secret_provider");
    connector.setLogoUrl(logoFilename);
    connector.setDescription(
        """
            HashiCorp Vault is a secrets safe keeping and management solution.
            """);
    connector.setShortDescription(
        "Enable integration with HashiCorp Vault in your OpenAEV instance.");
    connector.setClassName(getClassName());
    connector.setSubscriptionLink("https://www.hashicorp.com/en/products/vault");
    connector.setContainerType(ConnectorType.SECRETS_PROVIDER);
    connector.setCatalogConnectorConfigurations(
        new CrowdStrikeExecutorConfig().toCatalogConfigurationSet(connector));
    catalogConnectorService.saveAll(List.of(connector));
  }

  @Override
  public Integration spawn(ConnectorInstance instance) {
    return new VaultSecretProviderIntegration(
        instance,
        connectorInstanceService,
        endpointService,
        agentService,
        assetGroupService,
        executorService,
        enterpriseEditionService,
        licenseCacheManager,
        componentRequestEngine,
        taskScheduler,
        baseIntegrationConfigurationBuilder,
        httpClientFactory);
  }
}
