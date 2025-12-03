package io.openaev.integration;

import io.openaev.integration.migration.ConfigurationMigration;
import io.openaev.rest.connector_instance.service.ConnectorInstanceService;
import io.openaev.service.CatalogConnectorService;
import java.util.List;
import lombok.Getter;
import org.springframework.stereotype.Service;

@Service
public class ManagerFactory {
  @Getter private final Manager manager;

  public ManagerFactory(
      List<IntegrationFactory> factories,
      List<ConfigurationMigration> migrations,
      CatalogConnectorService catalogConnectorService,
      ConnectorInstanceService connectorInstanceService) {
    manager = new Manager(factories, migrations, catalogConnectorService, connectorInstanceService);
  }
}
