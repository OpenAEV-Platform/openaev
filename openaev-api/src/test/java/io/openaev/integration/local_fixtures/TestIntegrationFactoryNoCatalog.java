package io.openaev.integration.local_fixtures;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.integration.Integration;
import io.openaev.integration.IntegrationFactory;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import java.lang.reflect.InvocationTargetException;
import org.springframework.stereotype.Service;

@Service
public class TestIntegrationFactoryNoCatalog extends IntegrationFactory {

  public TestIntegrationFactoryNoCatalog(
      ConnectorInstanceService connectorInstanceService,
      CatalogConnectorService catalogConnectorService) {
    super(connectorInstanceService, catalogConnectorService);
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
  public Integration spawn(ConnectorInstance instance)
      throws JsonProcessingException,
          InvocationTargetException,
          NoSuchMethodException,
          InstantiationException,
          IllegalAccessException {
    return null;
  }
}
