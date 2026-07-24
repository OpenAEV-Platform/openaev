package io.openaev.integration.migration;

import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.ConnectorInstancePersisted;
import io.openaev.integration.configuration.BaseIntegrationConfiguration;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import io.openaev.service.connector_instances.EncryptionFactory;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
public abstract class ConfigurationMigration {
  private final BaseIntegrationConfiguration configuration;
  private final String factoryClassName;
  private final CatalogConnectorService catalogConnectorService;
  private final ConnectorInstanceService connectorInstanceService;
  private final EncryptionFactory encryptionFactory;

  protected ConfigurationMigration(
      BaseIntegrationConfiguration configuration,
      String factoryClassName,
      CatalogConnectorService catalogConnectorService,
      ConnectorInstanceService connectorInstanceService,
      EncryptionFactory encryptionFactory) {
    this.configuration = configuration;
    this.factoryClassName = factoryClassName;
    this.catalogConnectorService = catalogConnectorService;
    this.connectorInstanceService = connectorInstanceService;
    this.encryptionFactory = encryptionFactory;
  }

  // rollbackFor: migrate() throws checked exceptions (e.g. encryption failures); a partial
  // commit (instance saved without the migrated marker) would re-arm the migration and
  // reintroduce restart-time re-seeding.
  @Transactional(rollbackFor = Exception.class)
  public void migrate() throws Exception {
    Optional<CatalogConnector> connectorOptional =
        catalogConnectorService.findByFactoryClassName(factoryClassName);

    if (connectorOptional.isEmpty()) {
      log.error("Configuration found for {} but no related connector in catalog", factoryClassName);
      throw new IllegalArgumentException(
          "Configuration found for %s but no related connector in catalog"
              .formatted(factoryClassName));
    }
    CatalogConnector connector = connectorOptional.get();

    // One-shot: once migrated, never run again - deleting the migrated instance
    // must not resurrect it on the next startup.
    if (connector.isPropertiesMigrated()) {
      return;
    }

    // Legacy marker (installs migrated before the persistent flag existed):
    // record the flag so a later instance deletion sticks.
    Set<ConnectorInstancePersisted> instances = connector.getInstances();
    if (instances.stream()
        .anyMatch(
            i -> i.getSource().equals(ConnectorInstancePersisted.SOURCE.PROPERTIES_MIGRATION))) {
      log.warn("Already migrated {}; aborting.", configuration);
      markMigrated(connector);
      return;
    }

    // Nothing enabled in the legacy properties: nothing to migrate. Do NOT seed a
    // stopped instance - connectors only exist once deployed from the catalog.
    if (!configuration.isEnable()) {
      markMigrated(connector);
      return;
    }

    log.info("Migrating config for {}", configuration);
    ConnectorInstancePersisted instance = new ConnectorInstancePersisted();
    instance.setCatalogConnector(connector);

    instance.setCurrentStatus(ConnectorInstancePersisted.CURRENT_STATUS_TYPE.stopped);
    instance.setRequestedStatus(ConnectorInstancePersisted.REQUESTED_STATUS_TYPE.starting);
    instance.setSource(ConnectorInstancePersisted.SOURCE.PROPERTIES_MIGRATION);

    instance.setConfigurations(
        configuration.toInstanceConfigurationSet(
            instance, encryptionFactory.getEncryptionService(instance.getCatalogConnector())));

    connectorInstanceService.save(instance);
    markMigrated(connector);
  }

  private void markMigrated(CatalogConnector connector) {
    connector.setPropertiesMigrated(true);
    catalogConnectorService.saveAll(List.of(connector));
  }
}
