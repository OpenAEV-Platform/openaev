package io.openaev.integration.impl.executors.openaev;

import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.Endpoint;
import io.openaev.database.repository.AssetAgentJobRepository;
import io.openaev.executors.ExecutorService;
import io.openaev.executors.openaev.service.OpenAEVExecutorContextService;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.Integration;
import io.openaev.integration.QualifiedComponent;
import io.openaev.service.connector_instances.ConnectorInstanceService;

public class OpenAEVExecutorIntegration extends Integration {
  private final ExecutorService executorService;
  private final AssetAgentJobRepository assetAgentJobRepository;

  public static final String OPENAEV_EXECUTOR_ID = "2f9a0936-c327-4e95-b406-d161d32a2501";
  public static final String OPENAEV_EXECUTOR_TYPE = "openaev_agent";
  public static final String OPENAEV_EXECUTOR_NAME = "OpenAEV Agent";
  public static final String OPENAEV_EXECUTOR_DOCUMENTATION_LINK =
      "https://docs.openaev.io/latest/usage/openaev-agent/";
  public static final String OPENAEV_EXECUTOR_BACKGROUND_COLOR = "#001BDB";

  @QualifiedComponent(identifier = OPENAEV_EXECUTOR_NAME)
  private OpenAEVExecutorContextService openAEVExecutorContextService;

  public OpenAEVExecutorIntegration(
      ConnectorInstance connectorInstance,
      ConnectorInstanceService connectorInstanceService,
      ExecutorService executorService,
      AssetAgentJobRepository assetAgentJobRepository,
      ComponentRequestEngine componentRequestEngine) {
    super(componentRequestEngine, connectorInstance, connectorInstanceService);
    this.assetAgentJobRepository = assetAgentJobRepository;
    this.executorService = executorService;
  }

  @Override
  protected void innerStart() throws Exception {
    executorService.register(
        OPENAEV_EXECUTOR_ID,
        OPENAEV_EXECUTOR_TYPE,
        OPENAEV_EXECUTOR_NAME,
        OPENAEV_EXECUTOR_DOCUMENTATION_LINK,
        OPENAEV_EXECUTOR_BACKGROUND_COLOR,
        getClass().getResourceAsStream("/img/icon-openaev.png"),
        getClass().getResourceAsStream("/img/banner-openaev.png"),
        new String[] {
          Endpoint.PLATFORM_TYPE.Windows.name(),
          Endpoint.PLATFORM_TYPE.Linux.name(),
          Endpoint.PLATFORM_TYPE.MacOS.name()
        });

    this.openAEVExecutorContextService = new OpenAEVExecutorContextService(assetAgentJobRepository);
  }

  @Override
  protected void refresh() throws Exception {
    // Nothing to refresh from DB
  }

  @Override
  protected void innerStop() {
    // it is not possible to stop this integration
  }
}
