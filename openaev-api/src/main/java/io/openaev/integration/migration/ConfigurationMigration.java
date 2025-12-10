package io.openaev.integration.migration;

import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.integration.configuration.BaseIntegrationConfiguration;
import io.openaev.rest.connector_instance.service.ConnectorInstanceService;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import java.util.HashSet;
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

  protected ConfigurationMigration(
      BaseIntegrationConfiguration configuration,
      String factoryClassName,
      CatalogConnectorService catalogConnectorService,
      ConnectorInstanceService connectorInstanceService) {
    this.configuration = configuration;
    this.factoryClassName = factoryClassName;
    this.catalogConnectorService = catalogConnectorService;
    this.connectorInstanceService = connectorInstanceService;
  }

  @Transactional
  public void migrate() {
    Optional<CatalogConnector> connector =
        catalogConnectorService.findByFactoryClassName(factoryClassName);

    if (connector.isEmpty()) {
      log.error("Configuration found for {} but no related connector in catalog", factoryClassName);
      return;
    }

    Set<ConnectorInstance> instances = connector.get().getInstances();
    if (instances.stream()
        .anyMatch(i -> i.getSource().equals(ConnectorInstance.SOURCE.PROPERTIES_MIGRATION))) {
      log.warn("Already migrated {}; aborting.", configuration);
      return;
    }

    log.info("Migrating config for {}", configuration);
    ConnectorInstance instance = new ConnectorInstance();
    instance.setCatalogConnector(connector.get());
    // add configs
    instance.setConfigurations(new HashSet<>());

    instance.setCurrentStatus(ConnectorInstance.CURRENT_STATUS_TYPE.stopped);
    if (configuration.isEnable()) {
      instance.setRequestedStatus(ConnectorInstance.REQUESTED_STATUS_TYPE.starting);
    } else {
      instance.setRequestedStatus(ConnectorInstance.REQUESTED_STATUS_TYPE.stopping);
    }
    instance.setSource(ConnectorInstance.SOURCE.PROPERTIES_MIGRATION);

    instance.setConfigurations(configuration.toInstanceConfigurationSet(instance));

    connectorInstanceService.save(instance);
  }
}
