package io.openaev.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.rest.connector_instance.service.ConnectorInstanceService;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
public abstract class IntegrationFactory {
  private final ConnectorInstanceService connectorInstanceService;
  private final CatalogConnectorService catalogConnectorService;

  protected abstract void runMigrations() throws Exception;

  protected abstract void insertCatalogEntry() throws Exception;

  protected abstract String getClassName();

  @Transactional
  public List<Integration> initialise() throws Exception {
    String className = this.getClass().getCanonicalName();
    if (catalogConnectorService.findByFactoryClassName(className).isEmpty()) {
      insertCatalogEntry();
    }

    runMigrations();

    return connectorInstanceService.connectorInstances().stream()
        .filter(
            ci ->
                this.getClass().getCanonicalName().equals(ci.getCatalogConnector().getClassName()))
        .map(
            instance -> {
              try {
                Integration integration = this.spawn(instance);
                integration.initialise();
                return integration;
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            })
        .toList();
  }

  public abstract Integration spawn(ConnectorInstance instance)
      throws JsonProcessingException,
          InvocationTargetException,
          NoSuchMethodException,
          InstantiationException,
          IllegalAccessException;
}
