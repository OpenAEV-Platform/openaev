package io.openaev.integration;

import io.openaev.integration.migration.ConfigurationMigration;
import io.openaev.service.CatalogConnectorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ManagerFactory {
  private final List<IntegrationFactory> factories;
  private final List<ConfigurationMigration> migrations;
  private final CatalogConnectorService catalogConnectorService;

  private Manager managerInstance;

  public Manager getManager() {
    if(managerInstance == null) {
      managerInstance = new Manager(factories, migrations, catalogConnectorService);
    }
    return managerInstance;
  }
}
