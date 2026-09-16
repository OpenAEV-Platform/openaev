package io.openaev.integration.impl.executors.openaev;

import io.openaev.authorisation.HttpClientFactory;
import io.openaev.config.OpenAEVConfig;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorType;
import io.openaev.database.model.Endpoint;
import io.openaev.database.repository.AssetAgentJobRepository;
import io.openaev.executors.ExecutorService;
import io.openaev.integration.BuiltinIntegrationFactory;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.Integration;
import io.openaev.service.account.ServiceAccountPrivilegeService;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!test")
public class OpenAEVExecutorIntegrationFactory extends BuiltinIntegrationFactory {
  private final ExecutorService executorService;
  private final ComponentRequestEngine componentRequestEngine;
  private final AssetAgentJobRepository assetAgentJobRepository;
  private final ServiceAccountPrivilegeService serviceAccountPrivilegeService;
  private final OpenAEVConfig openAEVConfig;

  public OpenAEVExecutorIntegrationFactory(
      ConnectorInstanceService connectorInstanceService,
      CatalogConnectorService catalogConnectorService,
      ExecutorService executorService,
      ComponentRequestEngine componentRequestEngine,
      AssetAgentJobRepository assetAgentJobRepository,
      HttpClientFactory httpClientFactory,
      ServiceAccountPrivilegeService serviceAccountPrivilegeService,
      OpenAEVConfig openAEVConfig) {
    super(connectorInstanceService, catalogConnectorService, httpClientFactory);
    this.executorService = executorService;
    this.componentRequestEngine = componentRequestEngine;
    this.assetAgentJobRepository = assetAgentJobRepository;
    this.serviceAccountPrivilegeService = serviceAccountPrivilegeService;
    this.openAEVConfig = openAEVConfig;
  }

  @Override
  protected final String getClassName() {
    return OpenAEVExecutorIntegrationFactory.class.getCanonicalName();
  }

  @Override
  protected void runMigrations(String tenantId) throws Exception {
    // noop
  }

  @Override
  protected void insertCatalogEntry() throws Exception {
    // noop
  }

  @Override
  public List<ConnectorInstance> findRelatedInstances(String tenantId) {
    return List.of(
        connectorInstanceService.createAutostartInstance(
            OpenAEVExecutorIntegration.OPENAEV_EXECUTOR_ID,
            this.getClassName(),
            ConnectorType.EXECUTOR));
  }

  @Override
  public Integration spawn(ConnectorInstance instance) {
    return new OpenAEVExecutorIntegration(
        instance,
        connectorInstanceService,
        assetAgentJobRepository,
        componentRequestEngine,
        serviceAccountPrivilegeService,
        openAEVConfig);
  }

  @Override
  public void registerConnectorForTenant(String tenantId) throws Exception {
    executorService.register(
        tenantId,
        OpenAEVExecutorIntegration.OPENAEV_EXECUTOR_ID,
        OpenAEVExecutorIntegration.OPENAEV_EXECUTOR_TYPE,
        OpenAEVExecutorIntegration.OPENAEV_EXECUTOR_NAME,
        OpenAEVExecutorIntegration.OPENAEV_EXECUTOR_DOCUMENTATION_LINK,
        OpenAEVExecutorIntegration.OPENAEV_EXECUTOR_BACKGROUND_COLOR,
        getClass().getResourceAsStream("/img/icon-openaev.png"),
        getClass().getResourceAsStream("/img/banner-openaev.png"),
        new String[] {
          Endpoint.PLATFORM_TYPE.Windows.name(),
          Endpoint.PLATFORM_TYPE.Linux.name(),
          Endpoint.PLATFORM_TYPE.MacOS.name()
        },
        false);
  }
}
