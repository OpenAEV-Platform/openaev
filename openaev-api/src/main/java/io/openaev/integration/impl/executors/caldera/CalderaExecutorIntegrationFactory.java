package io.openaev.integration.impl.executors.caldera;

import static io.openaev.integration.impl.executors.caldera.CalderaExecutorIntegration.CALDERA_EXECUTOR_TYPE;

import io.openaev.authorisation.HttpClientFactory;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorType;
import io.openaev.executors.ExecutorService;
import io.openaev.executors.caldera.config.CalderaExecutorConfig;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.Integration;
import io.openaev.integration.IntegrationFactory;
import io.openaev.integration.configuration.BaseIntegrationConfigurationBuilder;
import io.openaev.integration.migration.CalderaExecutorConfigurationMigration;
import io.openaev.service.AgentService;
import io.openaev.service.EndpointService;
import io.openaev.service.FileService;
import io.openaev.service.InjectorService;
import io.openaev.service.PlatformSettingsService;
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
public class CalderaExecutorIntegrationFactory extends IntegrationFactory {
  private final ExecutorService executorService;
  private final ComponentRequestEngine componentRequestEngine;
  private final ConnectorInstanceService connectorInstanceService;
  private final CatalogConnectorService catalogConnectorService;
  private final CalderaExecutorConfigurationMigration calderaExecutorConfigurationMigration;

  private final AgentService agentService;
  private final EndpointService endpointService;
  private final InjectorService injectorService;
  private final PlatformSettingsService platformSettingsService;
  private final ThreadPoolTaskScheduler taskScheduler;
  private final FileService fileService;
  private final BaseIntegrationConfigurationBuilder baseIntegrationConfigurationBuilder;
  private final TenantScopedTransaction tenantTx;

  public CalderaExecutorIntegrationFactory(
      ConnectorInstanceService connectorInstanceService,
      CatalogConnectorService catalogConnectorService,
      ExecutorService executorService,
      ComponentRequestEngine componentRequestEngine,
      CalderaExecutorConfigurationMigration calderaExecutorConfigurationMigration,
      AgentService agentService,
      EndpointService endpointService,
      InjectorService injectorService,
      PlatformSettingsService platformSettingsService,
      ThreadPoolTaskScheduler taskScheduler,
      FileService fileService,
      BaseIntegrationConfigurationBuilder baseIntegrationConfigurationBuilder,
      HttpClientFactory httpClientFactory,
      TenantScopedTransaction tenantTx) {
    super(connectorInstanceService, catalogConnectorService, httpClientFactory);
    this.executorService = executorService;
    this.componentRequestEngine = componentRequestEngine;
    this.connectorInstanceService = connectorInstanceService;
    this.catalogConnectorService = catalogConnectorService;
    this.calderaExecutorConfigurationMigration = calderaExecutorConfigurationMigration;
    this.agentService = agentService;
    this.endpointService = endpointService;
    this.injectorService = injectorService;
    this.platformSettingsService = platformSettingsService;
    this.taskScheduler = taskScheduler;
    this.fileService = fileService;
    this.baseIntegrationConfigurationBuilder = baseIntegrationConfigurationBuilder;
    this.tenantTx = tenantTx;
  }

  @Override
  protected final String getClassName() {
    return CalderaExecutorIntegrationFactory.class.getCanonicalName();
  }

  @Override
  protected void runMigrations() throws Exception {
    calderaExecutorConfigurationMigration.migrate();
  }

  private String getLogoFilename() {
    return "%s-logo.png".formatted(CALDERA_EXECUTOR_TYPE);
  }

  @Override
  protected void ensureCatalogLogo() throws Exception {
    ensureCatalogLogo(getLogoFilename());
  }

  private void ensureCatalogLogo(String logoFilename) throws Exception {
    fileService.uploadCatalogLogo(
        FileService.CONNECTORS_LOGO_PATH,
        logoFilename,
        getClass().getResourceAsStream("/img/icon-caldera.png"));
  }

  @Override
  protected void insertCatalogEntry() throws Exception {
    String logoFilename = getLogoFilename();
    ensureCatalogLogo(logoFilename);
    CatalogConnector connector = new CatalogConnector();
    connector.setTitle("Caldera Executor");
    connector.setSlug(CALDERA_EXECUTOR_TYPE);
    connector.setLogoUrl(logoFilename);
    connector.setDescription(
        "Register hosts managed by your MITRE Caldera instance as OpenAEV executors and run"
            + " simulated attacks on them through Caldera, so you can validate detection and"
            + " prevention on real endpoints without deploying the OpenAEV agent.");
    connector.setShortDescription("Run OpenAEV simulations on hosts managed by MITRE Caldera.");
    connector.setClassName(getClassName());
    connector.setSubscriptionLink("https://caldera.mitre.org/");
    connector.setContainerType(ConnectorType.EXECUTOR);
    connector.setCatalogConnectorConfigurations(
        new CalderaExecutorConfig().toCatalogConfigurationSet(connector));
    catalogConnectorService.saveAll(List.of(connector));
  }

  @Override
  public Integration spawn(ConnectorInstance instance) {
    return new CalderaExecutorIntegration(
        instance,
        connectorInstanceService,
        endpointService,
        agentService,
        executorService,
        componentRequestEngine,
        platformSettingsService,
        injectorService,
        taskScheduler,
        baseIntegrationConfigurationBuilder,
        httpClientFactory,
        tenantTx);
  }
}
