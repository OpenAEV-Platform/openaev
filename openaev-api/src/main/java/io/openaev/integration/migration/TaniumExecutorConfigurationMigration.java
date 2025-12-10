package io.openaev.integration.migration;

import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.executors.tanium.config.TaniumExecutorConfig;
import io.openaev.integration.impl.executors.tanium.TaniumExecutorIntegrationFactory;
import io.openaev.rest.connector_instance.service.ConnectorInstanceService;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
@RequiredArgsConstructor
public class TaniumExecutorConfigurationMigration implements ConfigurationMigration {
  private final CatalogConnectorService catalogConnectorService;
  private final ConnectorInstanceService connectorInstanceService;
  private final TaniumExecutorConfig config;
  private final String factoryClass = TaniumExecutorIntegrationFactory.class.getName();

  @Override
  @Transactional
  public void migrate() {
    Optional<CatalogConnector> connector =
        catalogConnectorService.findByFactoryClassName(factoryClass);

    if (connector.isEmpty()) {
      log.error("Configuration found for {} but no related connector in catalog", factoryClass);
      return;
    }

    Set<ConnectorInstance> instances = connector.get().getInstances();
    if (instances.stream()
        .anyMatch(i -> i.getSource().equals(ConnectorInstance.SOURCE.PROPERTIES_MIGRATION))) {
      log.warn("Already migrated {}; aborting.", config);
      return;
    }

    log.info("Migrating config for {}", config);
    ConnectorInstance instance = new ConnectorInstance();
    instance.setCatalogConnector(connector.get());
    // add configs
    instance.setConfigurations(new HashSet<>());

    instance.setCurrentStatus(ConnectorInstance.CURRENT_STATUS_TYPE.stopped);
    if (config.isEnable()) {
      instance.setRequestedStatus(ConnectorInstance.REQUESTED_STATUS_TYPE.starting);
    } else {
      instance.setRequestedStatus(ConnectorInstance.REQUESTED_STATUS_TYPE.stopping);
    }
    instance.setSource(ConnectorInstance.SOURCE.PROPERTIES_MIGRATION);

    instance.setConfigurations(config.toInstanceConfigurationSet(instance));

    connectorInstanceService.save(instance);
  }
}
