package io.openaev.integration.migration;

import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.ConnectorInstancePersisted;
import io.openaev.integration.configuration.BaseIntegrationConfiguration;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import io.openaev.service.connector_instances.EncryptionFactory;
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

  @Transactional
  public void migrate() throws Exception {
    Optional<CatalogConnector> connector =
        catalogConnectorService.findByFactoryClassName(factoryClassName);

    if (connector.isEmpty()) {
      log.error("Configuration found for {} but no related connector in catalog", factoryClassName);
      throw new IllegalArgumentException(
          "Configuration found for %s but no related connector in catalog"
              .formatted(factoryClassName));
    }

    Set<ConnectorInstancePersisted> instances = connector.get().getInstances();
    if (instances.stream()
        .anyMatch(
            i -> i.getSource().equals(ConnectorInstancePersisted.SOURCE.PROPERTIES_MIGRATION))) {
      log.warn("Already migrated {}; aborting.", configuration);
      return;
    }

    log.info("Migrating config for {}", configuration);
    ConnectorInstancePersisted instance = new ConnectorInstancePersisted();
    instance.setCatalogConnector(connector.get());

    instance.setCurrentStatus(ConnectorInstancePersisted.CURRENT_STATUS_TYPE.stopped);
    if (configuration.isEnable()) {
      instance.setRequestedStatus(ConnectorInstancePersisted.REQUESTED_STATUS_TYPE.starting);
    } else {
      instance.setRequestedStatus(ConnectorInstancePersisted.REQUESTED_STATUS_TYPE.stopping);
    }
    instance.setSource(ConnectorInstancePersisted.SOURCE.PROPERTIES_MIGRATION);

    instance.setConfigurations(
        configuration.toInstanceConfigurationSet(
            instance, encryptionFactory.getEncryptionService(instance.getCatalogConnector())));

    connectorInstanceService.save(instance);
  }
}
