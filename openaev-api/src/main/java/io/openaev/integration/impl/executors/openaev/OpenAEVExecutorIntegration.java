package io.openaev.integration.impl.executors.openaev;

import io.openaev.config.OpenAEVConfig;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.repository.AssetAgentJobRepository;
import io.openaev.executors.openaev.service.OpenAEVExecutorContextService;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.Integration;
import io.openaev.integration.annotation.QualifiedComponent;
import io.openaev.service.account.ServiceAccountPrivilegeService;
import io.openaev.service.connector_instances.ConnectorInstanceService;

public class OpenAEVExecutorIntegration extends Integration {
  private final AssetAgentJobRepository assetAgentJobRepository;

  public static final String OPENAEV_EXECUTOR_ID = "2f9a0936-c327-4e95-b406-d161d32a2501";
  public static final String OPENAEV_EXECUTOR_TYPE = "openaev_agent";
  public static final String OPENAEV_EXECUTOR_NAME = "OpenAEV Agent";
  public static final String OPENAEV_EXECUTOR_DOCUMENTATION_LINK =
      "https://docs.openaev.io/latest/usage/openaev-agent/";
  public static final String OPENAEV_EXECUTOR_BACKGROUND_COLOR = "#001BDB";

  @QualifiedComponent(identifier = OPENAEV_EXECUTOR_NAME)
  private OpenAEVExecutorContextService openAEVExecutorContextService;

  private final ServiceAccountPrivilegeService serviceAccountPrivilegeService;
  private final OpenAEVConfig openAEVConfig;

  public OpenAEVExecutorIntegration(
      ConnectorInstance connectorInstance,
      ConnectorInstanceService connectorInstanceService,
      AssetAgentJobRepository assetAgentJobRepository,
      ComponentRequestEngine componentRequestEngine,
      ServiceAccountPrivilegeService serviceAccountPrivilegeService,
      OpenAEVConfig openAEVConfig) {
    super(componentRequestEngine, connectorInstance, connectorInstanceService);
    this.assetAgentJobRepository = assetAgentJobRepository;
    this.serviceAccountPrivilegeService = serviceAccountPrivilegeService;
    this.openAEVConfig = openAEVConfig;
  }

  @Override
  protected void innerStart() throws Exception {

    this.openAEVExecutorContextService =
        new OpenAEVExecutorContextService(
            assetAgentJobRepository, serviceAccountPrivilegeService, openAEVConfig);
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
