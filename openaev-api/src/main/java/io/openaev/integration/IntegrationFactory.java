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

  /**
   * Uploads platform assets (e.g. connector logos) to the current tenant's storage. Called on every
   * tenant initialization — not just the first one — to ensure each tenant has the necessary files.
   * Default implementation does nothing; subclasses override to upload their specific assets.
   */
  protected void uploadAssets() throws Exception {
    // Default: no-op. Subclasses override to upload logos/images for the current tenant.
  }

  @Transactional(rollbackFor = Exception.class)
  public void initialise() throws Exception {
    String className = this.getClassName();
    if (catalogConnectorService.findByFactoryClassName(className).isEmpty()) {
      insertCatalogEntry();
    }

    uploadAssets();
    runMigrations();
  }

  public List<Integration> sync(List<ConnectorInstance> instances, String tenantId) {
    List<Integration> list = new ArrayList<>();
    for (ConnectorInstance connectorInstance : instances) {
      try {
        Integration integration = this.spawn(connectorInstance);
        integration.initialise(tenantId);

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
  public List<ConnectorInstance> findRelatedInstances(String tenantId) {
    return connectorInstanceService.connectorInstances().stream()
        .filter(ci -> this.getClassName().equals(ci.getClassName()))
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
