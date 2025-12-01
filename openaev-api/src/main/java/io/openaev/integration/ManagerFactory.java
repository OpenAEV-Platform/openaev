package io.openaev.integration;

import io.openaev.integration.migration.ConfigurationMigration;
import io.openaev.rest.connector_instance.service.ConnectorInstanceService;
import io.openaev.service.CatalogConnectorService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ManagerFactory {
  private final List<IntegrationFactory> factories;
  private final List<ConfigurationMigration> migrations;
  private final CatalogConnectorService catalogConnectorService;
  private final ConnectorInstanceService connectorInstanceService;

  private Manager managerInstance;

  public Manager getManager() {
    if (managerInstance == null) {
      managerInstance =
          new Manager(factories, migrations, catalogConnectorService, connectorInstanceService);
    }
    return managerInstance;
  }
}
