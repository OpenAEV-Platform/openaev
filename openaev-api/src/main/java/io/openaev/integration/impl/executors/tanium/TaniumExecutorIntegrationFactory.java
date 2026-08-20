package io.openaev.integration.impl.executors.tanium;

import static io.openaev.integration.impl.executors.tanium.TaniumExecutorIntegration.TANIUM_EXECUTOR_TYPE;

import io.openaev.authorisation.HttpClientFactory;
import io.openaev.config.OpenAEVConfig;
import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorType;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.executors.ExecutorService;
import io.openaev.executors.tanium.config.TaniumExecutorConfig;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.Integration;
import io.openaev.integration.IntegrationFactory;
import io.openaev.integration.configuration.BaseIntegrationConfigurationBuilder;
import io.openaev.integration.migration.TaniumExecutorConfigurationMigration;
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
public class TaniumExecutorIntegrationFactory extends IntegrationFactory {
  private final ExecutorService executorService;
  private final ComponentRequestEngine componentRequestEngine;
  private final ConnectorInstanceService connectorInstanceService;
  private final CatalogConnectorService catalogConnectorService;
  private final TaniumExecutorConfigurationMigration taniumExecutorConfigurationMigration;

  private final AgentService agentService;
  private final EndpointService endpointService;
  private final AssetGroupService assetGroupService;
  private final EnterpriseEditionService enterpriseEditionService;
  private final LicenseCacheManager licenseCacheManager;
  private final ThreadPoolTaskScheduler taskScheduler;
  private final FileService fileService;
  private final BaseIntegrationConfigurationBuilder baseIntegrationConfigurationBuilder;
  private final OpenAEVConfig openAEVConfig;
  private final TenantScopedTransaction tenantTx;

  public TaniumExecutorIntegrationFactory(
      ConnectorInstanceService connectorInstanceService,
      CatalogConnectorService catalogConnectorService,
      ExecutorService executorService,
      ComponentRequestEngine componentRequestEngine,
      TaniumExecutorConfigurationMigration taniumExecutorConfigurationMigration,
      AgentService agentService,
      EndpointService endpointService,
      AssetGroupService assetGroupService,
      EnterpriseEditionService enterpriseEditionService,
      LicenseCacheManager licenseCacheManager,
      ThreadPoolTaskScheduler taskScheduler,
      FileService fileService,
      BaseIntegrationConfigurationBuilder baseIntegrationConfigurationBuilder,
      HttpClientFactory httpClientFactory,
      OpenAEVConfig openAEVConfig,
      TenantScopedTransaction tenantTx) {
    super(connectorInstanceService, catalogConnectorService, httpClientFactory);
    this.executorService = executorService;
    this.componentRequestEngine = componentRequestEngine;
    this.connectorInstanceService = connectorInstanceService;
    this.catalogConnectorService = catalogConnectorService;
    this.taniumExecutorConfigurationMigration = taniumExecutorConfigurationMigration;
    this.agentService = agentService;
    this.endpointService = endpointService;
    this.assetGroupService = assetGroupService;
    this.enterpriseEditionService = enterpriseEditionService;
    this.licenseCacheManager = licenseCacheManager;
    this.taskScheduler = taskScheduler;
    this.fileService = fileService;
    this.baseIntegrationConfigurationBuilder = baseIntegrationConfigurationBuilder;
    this.openAEVConfig = openAEVConfig;
    this.tenantTx = tenantTx;
  }

  @Override
  protected final String getClassName() {
    return TaniumExecutorIntegrationFactory.class.getCanonicalName();
  }

  @Override
  protected void runMigrations(String tenantId) throws Exception {
    taniumExecutorConfigurationMigration.migrate(tenantId);
  }

  private String getLogoFilename() {
    return "%s-logo.png".formatted(TANIUM_EXECUTOR_TYPE);
  }

  @Override
  protected void ensureCatalogLogo() throws Exception {
    ensureCatalogLogo(getLogoFilename());
  }

  private void ensureCatalogLogo(String logoFilename) throws Exception {
    fileService.uploadCatalogLogo(
        FileService.CONNECTORS_LOGO_PATH,
        logoFilename,
        getClass().getResourceAsStream("/img/icon-tanium.png"));
  }

  @Override
  protected void insertCatalogEntry() throws Exception {
    String logoFilename = getLogoFilename();
    ensureCatalogLogo(logoFilename);
    CatalogConnector connector = new CatalogConnector();
    connector.setTitle("Tanium Executor");
    connector.setSlug(TANIUM_EXECUTOR_TYPE);
    connector.setLogoUrl(logoFilename);
    connector.setDescription(
        "Register your Tanium-managed endpoints as OpenAEV executors and run simulated attacks"
            + " on them through Tanium, so you can validate detection and prevention on real"
            + " endpoints without deploying the OpenAEV agent.");
    connector.setShortDescription("Run OpenAEV simulations on your Tanium endpoints.");
    connector.setClassName(getClassName());
    connector.setSubscriptionLink("https://www.tanium.com");
    connector.setContainerType(ConnectorType.EXECUTOR);
    connector.setCatalogConnectorConfigurations(
        new TaniumExecutorConfig().toCatalogConfigurationSet(connector));
    catalogConnectorService.saveAll(List.of(connector));
  }

  @Override
  public Integration spawn(ConnectorInstance instance) {
    return new TaniumExecutorIntegration(
        instance,
        connectorInstanceService,
        endpointService,
        agentService,
        assetGroupService,
        enterpriseEditionService,
        licenseCacheManager,
        componentRequestEngine,
        executorService,
        taskScheduler,
        baseIntegrationConfigurationBuilder,
        httpClientFactory,
        openAEVConfig,
        tenantTx);
  }
}
