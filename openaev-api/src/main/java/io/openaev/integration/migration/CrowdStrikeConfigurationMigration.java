package io.openaev.integration.migration;

import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.executors.crowdstrike.config.CrowdStrikeExecutorConfig;
import io.openaev.integration.CrowdStrikeIntegrationFactory;
import io.openaev.rest.connector_instance.service.ConnectorInstanceService;
import io.openaev.service.CatalogConnectorService;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Component
@Slf4j
@RequiredArgsConstructor
public class CrowdStrikeConfigurationMigration implements ConfigurationMigration {
  private final CatalogConnectorService catalogConnectorService;
  private final ConnectorInstanceService connectorInstanceService;
  private final CrowdStrikeExecutorConfig config;
  private final String factoryClass = CrowdStrikeIntegrationFactory.class.getName();

  @Override
  @Transactional
  public void migrate() {
    log.error("Migrating config for {}", config);
    Optional<CatalogConnector> connector = catalogConnectorService.findByFactoryClassName(factoryClass);

    if(connector.isEmpty()) {
      log.error("Configuration found for {} but no related connector in catalog", factoryClass);
      return;
    }

    Set<ConnectorInstance> instances = connector.get().getInstances();

    ConnectorInstance instance = new ConnectorInstance();
    instance.setCatalogConnector(connector.get());
    // add configs
    instance.setConfigurations(new HashSet<>());
    instance.setCurrentStatus(ConnectorInstance.CURRENT_STATUS_TYPE.stopped);
    instance.setRequestedStatus(ConnectorInstance.REQUESTED_STATUS_TYPE.stopping);

    connectorInstanceService.save(instance);
  }
}
