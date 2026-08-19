package io.openaev.integration.impl.executors.mde;

import static io.openaev.integration.impl.executors.mde.MdeExecutorIntegration.MDE_EXECUTOR_TYPE;

import io.openaev.authorisation.HttpClientFactory;
import io.openaev.config.OpenAEVConfig;
import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorType;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.executors.ExecutorService;
import io.openaev.executors.mde.config.MdeExecutorConfig;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.Integration;
import io.openaev.integration.IntegrationFactory;
import io.openaev.integration.configuration.BaseIntegrationConfigurationBuilder;
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
public class MdeExecutorIntegrationFactory extends IntegrationFactory {

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
  private final FileService fileService;
  private final BaseIntegrationConfigurationBuilder baseIntegrationConfigurationBuilder;
  private final OpenAEVConfig openAEVConfig;
  private final TenantScopedTransaction tenantTx;

  public MdeExecutorIntegrationFactory(
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
      FileService fileService,
      BaseIntegrationConfigurationBuilder baseIntegrationConfigurationBuilder,
      HttpClientFactory httpClientFactory,
      OpenAEVConfig openAEVConfig,
      TenantScopedTransaction tenantTx) {
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
    this.fileService = fileService;
    this.baseIntegrationConfigurationBuilder = baseIntegrationConfigurationBuilder;
    this.openAEVConfig = openAEVConfig;
    this.tenantTx = tenantTx;
  }

  @Override
  protected final String getClassName() {
    return MdeExecutorIntegrationFactory.class.getCanonicalName();
  }

  @Override
  protected void runMigrations(String tenantId) throws Exception {
    // Catalog-first executor: no legacy properties to migrate (same pattern as PaloAltoCortex).
  }

  private String getLogoFilename() {
    return "%s-logo.png".formatted(MDE_EXECUTOR_TYPE);
  }

  @Override
  protected void ensureCatalogLogo() throws Exception {
    ensureCatalogLogo(getLogoFilename());
  }

  private void ensureCatalogLogo(String logoFilename) throws Exception {
    fileService.uploadCatalogLogo(
        FileService.CONNECTORS_LOGO_PATH,
        logoFilename,
        getClass().getResourceAsStream("/img/icon-mde.png"));
  }

  @Override
  protected void insertCatalogEntry() throws Exception {
    String logoFilename = getLogoFilename();
    ensureCatalogLogo(logoFilename);
    CatalogConnector connector = new CatalogConnector();
    connector.setTitle("Microsoft Defender for Endpoint Executor");
    connector.setSlug(MDE_EXECUTOR_TYPE);
    connector.setLogoUrl(logoFilename);
    connector.setDescription(
        """
Microsoft Defender for Endpoint (MDE) is a comprehensive enterprise endpoint security platform that provides preventive protection, post-breach detection, automated investigation, and response capabilities.

With the MDE executor, register your endpoints in OpenAEV and enable execution of OpenAEV scenarios through your Microsoft Defender for Endpoint instance via Live Response.
""");
    connector.setShortDescription(
        "Enable execution of OpenAEV scenarios through your Microsoft Defender for Endpoint"
            + " instance.");
    connector.setClassName(getClassName());
    connector.setSubscriptionLink(
        "https://www.microsoft.com/en-us/security/business/endpoint-security/microsoft-defender-endpoint");
    connector.setContainerType(ConnectorType.EXECUTOR);
    connector.setCatalogConnectorConfigurations(
        new MdeExecutorConfig().toCatalogConfigurationSet(connector));
    catalogConnectorService.saveAll(List.of(connector));
  }

  @Override
  public Integration spawn(ConnectorInstance instance) {
    return new MdeExecutorIntegration(
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
        httpClientFactory,
        openAEVConfig,
        tenantTx);
  }
}
