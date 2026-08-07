package io.openaev.integration.impl.injectors.prowler;

import io.openaev.database.model.ConnectorInstance;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.IntegrationInMemory;
import io.openaev.service.connector_instances.ConnectorInstanceService;

/**
 * Placeholder in-memory integration for the Prowler injector catalog entry. See {@link
 * io.openaev.injectors.prowler.ProwlerContract} for context: no executor is required since this
 * injector currently has no executable contracts.
 */
public class ProwlerInjectorIntegration extends IntegrationInMemory {
  static final String PROWLER_INJECTOR_NAME = "Prowler";
  public static final String PROWLER_INJECTOR_ID = "8f1a2e63-6c7b-4d4a-9b0e-2d7c5a1f9e64";

  public ProwlerInjectorIntegration(
      ComponentRequestEngine componentRequestEngine,
      ConnectorInstance connectorInstance,
      ConnectorInstanceService connectorInstanceService) {
    super(componentRequestEngine, connectorInstance, connectorInstanceService);
  }

  @Override
  protected void innerStart() {
    // No executor to start: this injector has no executable contracts (catalog placeholder only).
  }

  @Override
  protected void innerStop() {
    // it is not possible to stop this integration
  }
}
