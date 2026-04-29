package io.openaev.integration.impl.executors.openaev;

import io.openaev.authorisation.HttpClientFactory;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorType;
import io.openaev.database.model.Endpoint;
import io.openaev.database.repository.AssetAgentJobRepository;
import io.openaev.executors.ExecutorService;
import io.openaev.integration.BuiltinIntegrationFactory;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.Integration;
import io.openaev.rest.exception.ElementNotFoundException;
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

  public OpenAEVExecutorIntegrationFactory(
      ConnectorInstanceService connectorInstanceService,
      CatalogConnectorService catalogConnectorService,
      ExecutorService executorService,
      ComponentRequestEngine componentRequestEngine,
      AssetAgentJobRepository assetAgentJobRepository,
      HttpClientFactory httpClientFactory) {
    super(connectorInstanceService, catalogConnectorService, httpClientFactory);
    this.executorService = executorService;
    this.componentRequestEngine = componentRequestEngine;
    this.assetAgentJobRepository = assetAgentJobRepository;
  }

  @Override
  protected final String getClassName() {
    return OpenAEVExecutorIntegrationFactory.class.getCanonicalName();
  }

  @Override
  protected void runMigrations() throws Exception {
    // noop
  }

  @Override
  protected void insertCatalogEntry() throws Exception {
    // noop
  }

  @Override
  public List<ConnectorInstance> findRelatedInstances() {
    return List.of(
        connectorInstanceService.createAutostartInstance(
            OpenAEVExecutorIntegration.OPENAEV_EXECUTOR_ID,
            this.getClassName(),
            ConnectorType.EXECUTOR));
  }

  @Override
  public Integration spawn(ConnectorInstance instance) {
    return new OpenAEVExecutorIntegration(
        instance, connectorInstanceService, assetAgentJobRepository, componentRequestEngine);
  }

  @Override
  public void registerConnectorForTenant() throws Exception {
    try {
      executorService.executor(OpenAEVExecutorIntegration.OPENAEV_EXECUTOR_ID);
    } catch (ElementNotFoundException e) {
      executorService.register(
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
          });
    }
  }
}
