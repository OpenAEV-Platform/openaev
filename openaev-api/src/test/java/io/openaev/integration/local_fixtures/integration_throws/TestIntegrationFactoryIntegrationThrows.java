package io.openaev.integration.local_fixtures.integration_throws;

import static io.openaev.integration.local_fixtures.integration_throws.TestIntegrationStartThrows.THROWING_INTEGRATION_ID;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.openaev.authorisation.HttpClientFactory;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorType;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.Integration;
import io.openaev.integration.IntegrationFactory;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

public class TestIntegrationFactoryIntegrationThrows extends IntegrationFactory {
  private final ComponentRequestEngine componentRequestEngine;
  private final ConnectorInstanceService connectorInstanceService;

  public TestIntegrationFactoryIntegrationThrows(
      ConnectorInstanceService connectorInstanceService,
      CatalogConnectorService catalogConnectorService,
      HttpClientFactory httpClientFactory,
      ComponentRequestEngine componentRequestEngine) {
    super(connectorInstanceService, catalogConnectorService, httpClientFactory);
    this.componentRequestEngine = componentRequestEngine;
    this.connectorInstanceService = connectorInstanceService;
  }

  @Override
  protected void runMigrations() throws Exception {}

  @Override
  protected void insertCatalogEntry() throws Exception {}

  @Override
  protected String getClassName() {
    return this.getClass().getCanonicalName();
  }

  @Override
  public List<ConnectorInstance> findRelatedInstances() {
    return List.of(
        connectorInstanceService.createAutostartInstance(
            THROWING_INTEGRATION_ID, this.getClassName(), ConnectorType.INJECTOR));
  }

  @Override
  public Integration spawn(ConnectorInstance instance)
      throws JsonProcessingException,
          InvocationTargetException,
          NoSuchMethodException,
          InstantiationException,
          IllegalAccessException {
    return new TestIntegrationStartThrows(
        componentRequestEngine, instance, connectorInstanceService);
  }
}
