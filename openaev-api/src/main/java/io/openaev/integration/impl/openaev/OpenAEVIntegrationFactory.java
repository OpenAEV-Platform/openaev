package io.openaev.integration.impl.openaev;

import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.repository.AssetAgentJobRepository;
import io.openaev.executors.ExecutorService;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.Integration;
import io.openaev.integration.IntegrationFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OpenAEVIntegrationFactory implements IntegrationFactory {
  private final ExecutorService executorService;
  private final ComponentRequestEngine componentRequestEngine;
  private final AssetAgentJobRepository assetAgentJobRepository;

  @Override
  public Integration spawn(ConnectorInstance instance) {
    return new OpenAEVIntegration(executorService, assetAgentJobRepository, componentRequestEngine);
  }
}
