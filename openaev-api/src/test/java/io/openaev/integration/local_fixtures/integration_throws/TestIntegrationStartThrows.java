package io.openaev.integration.local_fixtures.integration_throws;

import io.openaev.database.model.ConnectorInstance;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.Integration;
import io.openaev.service.connector_instances.ConnectorInstanceService;

public class TestIntegrationStartThrows extends Integration {
  protected TestIntegrationStartThrows(
      ComponentRequestEngine componentRequestEngine,
      ConnectorInstance connectorInstance,
      ConnectorInstanceService connectorInstanceService) {
    super(componentRequestEngine, connectorInstance, connectorInstanceService);
  }

  @Override
  protected void innerStart() throws Exception {
    throw new RuntimeException("throw exception on start()");
  }

  @Override
  protected void refresh() throws Exception {
    // noop
  }

  @Override
  protected void innerStop() {
    // noop
  }
}
