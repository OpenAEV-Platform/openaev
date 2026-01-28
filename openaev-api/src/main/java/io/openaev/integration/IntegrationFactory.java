package io.openaev.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.openaev.authorisation.HttpClientFactory;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
public abstract class IntegrationFactory {
  protected final ConnectorInstanceService connectorInstanceService;
  protected final CatalogConnectorService catalogConnectorService;
  protected final HttpClientFactory httpClientFactory;

  protected abstract void runMigrations() throws Exception;

  protected abstract void insertCatalogEntry() throws Exception;

  protected abstract String getClassName();

  @Transactional(rollbackFor = Exception.class)
  public void initialise() throws Exception {
    String className = this.getClass().getCanonicalName();
    if (catalogConnectorService.findByFactoryClassName(className).isEmpty()) {
      insertCatalogEntry();
    }

    runMigrations();
  }

  public List<Integration> sync(List<ConnectorInstance> instances) {
    List<Integration> list = new ArrayList<>();
    for (ConnectorInstance connectorInstance : instances) {
      try {
        Integration integration = this.spawn(connectorInstance);
        integration.initialise();

        list.add(integration);
      } catch (Exception e) {
        log.error(
            "There was a problem initialising the integration from instance id '{}' from factory type {}.",
            connectorInstance.getId(),
            connectorInstance.getClassName(),
            e);
        // do not rethrow; don't break the loop
      }
    }
    return list;
  }

  @Transactional
  public List<ConnectorInstance> findRelatedInstances() {
    return connectorInstanceService.connectorInstances().stream()
        .filter(ci -> this.getClass().getCanonicalName().equals(ci.getClassName()))
        .map(ci -> (ConnectorInstance) ci)
        .toList();
  }

  public abstract Integration spawn(ConnectorInstance instance)
      throws JsonProcessingException,
          InvocationTargetException,
          NoSuchMethodException,
          InstantiationException,
          IllegalAccessException;
}
