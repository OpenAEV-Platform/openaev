package io.openaev.integration.local_fixtures;

import io.openaev.authorisation.HttpClientFactory;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.integration.Integration;
import io.openaev.integration.IntegrationFactory;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import org.springframework.stereotype.Service;

@Service
public class TestIntegrationFactoryNoCatalog extends IntegrationFactory {

  public TestIntegrationFactoryNoCatalog(
      ConnectorInstanceService connectorInstanceService,
      CatalogConnectorService catalogConnectorService,
      HttpClientFactory httpClientFactory) {
    super(connectorInstanceService, catalogConnectorService, httpClientFactory);
  }

  @Override
  protected void runMigrations() throws Exception {}

  @Override
  protected void insertCatalogEntry() throws Exception {}

  @Override
  protected String getClassName() {
    return "";
  }

  @Override
  public Integration spawn(ConnectorInstance instance) {
    return null;
  }
}
