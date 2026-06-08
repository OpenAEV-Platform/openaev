package io.openaev.integration;

import io.openaev.authorisation.HttpClientFactory;
import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
   * Ensures the catalog logo exists in MinIO. Called on every startup (best-effort), even when the
   * catalog entry already exists in the database. Override in subclasses that upload a logo during
   * {@link #insertCatalogEntry()}.
   *
   * <p>Default implementation is a no-op for factories that do not manage a catalog logo (e.g.
   * built-in executors).
   */
  protected void ensureCatalogLogo() throws Exception {
    // no-op by default
  }

  @Transactional(rollbackFor = Exception.class)
  public void initialise() throws Exception {
    String className = this.getClassName();
    Optional<CatalogConnector> existing = catalogConnectorService.findByFactoryClassName(className);
    if (existing.isEmpty()) {
      insertCatalogEntry();
      existing = catalogConnectorService.findByFactoryClassName(className);
    } else {
      try {
        ensureCatalogLogo();
      } catch (Exception e) {
        log.warn("Failed to ensure catalog logo for {}: {}", className, e.getMessage());
      }
    }

    // Factory-managed connectors (built-in executors and injectors) are built and
    // maintained by Filigran: always surface them as verified ("Supported by
    // Filigran"). Also heals entries created before this rule existed.
    existing
        .filter(connector -> !connector.isVerified())
        .ifPresent(
            connector -> {
              connector.setVerified(true);
              catalogConnectorService.saveAll(List.of(connector));
            });

    runMigrations();
  }

  @Transactional(rollbackFor = Exception.class)
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

  @Transactional(readOnly = true)
  public List<ConnectorInstance> findRelatedInstances(String tenantId) {
    return new ArrayList<>(
        connectorInstanceService.connectorInstancesByTenantIdAndClassName(
            tenantId, this.getClassName()));
  }

  public abstract Integration spawn(ConnectorInstance instance) throws Exception;
}
