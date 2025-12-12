package io.openaev.integration.impl.executors.openaev;

import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.repository.AssetAgentJobRepository;
import io.openaev.executors.ExecutorService;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.Integration;
import io.openaev.integration.IntegrationFactory;
import io.openaev.rest.connector_instance.service.ConnectorInstanceService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OpenAEVExecutorIntegrationFactory implements IntegrationFactory {
  private final ExecutorService executorService;
  private final ComponentRequestEngine componentRequestEngine;
  private final AssetAgentJobRepository assetAgentJobRepository;
  private final ConnectorInstanceService connectorInstanceService;

  @Override
  public List<Integration> initialise() {
    // specifically don't register a catalog object
    // create an in-memory connector instance
    Integration integration = this.spawn(connectorInstanceService.createAutostartInstance());
    try {
      integration.initialise();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return List.of(integration);
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
