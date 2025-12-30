package io.openaev.integration.impl.executors.openaev;

import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.repository.AssetAgentJobRepository;
import io.openaev.executors.ExecutorService;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.Integration;
import io.openaev.integration.IntegrationFactory;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!test")
public class OpenAEVExecutorIntegrationFactory extends IntegrationFactory {
  private final ExecutorService executorService;
  private final ComponentRequestEngine componentRequestEngine;
  private final AssetAgentJobRepository assetAgentJobRepository;
  private final ConnectorInstanceService connectorInstanceService;

  public OpenAEVExecutorIntegrationFactory(
      ConnectorInstanceService connectorInstanceService,
      CatalogConnectorService catalogConnectorService,
      ExecutorService executorService,
      ComponentRequestEngine componentRequestEngine,
      AssetAgentJobRepository assetAgentJobRepository) {
    super(connectorInstanceService, catalogConnectorService);
    this.executorService = executorService;
    this.componentRequestEngine = componentRequestEngine;
    this.assetAgentJobRepository = assetAgentJobRepository;
    this.connectorInstanceService = connectorInstanceService;
  }

  @Override
  protected final String getClassName() {
    return this.getClass().getCanonicalName();
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
            OpenAEVExecutorIntegration.OPENAEV_EXECUTOR_ID));
  }

  @Override
  public Integration spawn(ConnectorInstance instance) {
    return new OpenAEVExecutorIntegration(
        instance,
        connectorInstanceService,
        executorService,
        assetAgentJobRepository,
        componentRequestEngine);
  }
}
