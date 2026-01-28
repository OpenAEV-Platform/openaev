package io.openaev.integration.local_fixtures.regular;

import io.openaev.database.model.ConnectorInstance;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.Integration;
import io.openaev.integration.QualifiedComponent;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import io.openaev.service.connector_instances.EncryptionService;

public class TestIntegration extends Integration {
  public static final String TEST_COMPONENT_IDENTIFIER = "test_component_identifier";

  @QualifiedComponent(identifier = TEST_COMPONENT_IDENTIFIER)
  private TestIntegrationComponent testIntegrationComponent;

  public TestIntegration(
      ComponentRequestEngine componentRequestEngine,
      ConnectorInstance connectorInstance,
      ConnectorInstanceService connectorInstanceService,
      EncryptionService encryptionService) {
    super(componentRequestEngine, connectorInstance, connectorInstanceService);
  }

  @Override
  protected void innerStart() throws Exception {
    testIntegrationComponent = new TestIntegrationComponent();
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
