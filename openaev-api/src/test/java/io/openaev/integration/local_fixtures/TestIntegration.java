package io.openaev.integration.local_fixtures;

import io.openaev.database.model.ConnectorInstance;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.Integration;
import io.openaev.rest.connector_instance.service.ConnectorInstanceService;

public class TestIntegration extends Integration {
  protected TestIntegration(
      ComponentRequestEngine componentRequestEngine,
      ConnectorInstance connectorInstance,
      ConnectorInstanceService connectorInstanceService) {
    super(componentRequestEngine, connectorInstance, connectorInstanceService);
  }

  @Override
  protected void innerStart() throws Exception {
    // noop
  }

  @Override
  protected void innerStop() {
    // noop
  }
}
