package io.openaev.integration;

import io.openaev.database.model.ConnectorInstance;
import io.openaev.integration.migration.ConfigurationMigration;
import io.openaev.service.CatalogConnectorService;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

public class Manager {
  private final List<IntegrationFactory> factories;
  private final List<ConfigurationMigration> migrations;
  private final CatalogConnectorService catalogConnectorService;

  @Getter
  private final List<Integration> spawnedIntegrations = new ArrayList<>();

  public Manager(List<IntegrationFactory> factories, List<ConfigurationMigration> migrations, CatalogConnectorService catalogConnectorService) {
    this.factories = factories;
    this.migrations = migrations;
    this.catalogConnectorService = catalogConnectorService;

    initialise();
  }

  private void initialise() {
    List<String> classes = factories.stream().map(factory -> factory.getClass().getName()).toList();

    for (String className : classes) {
      if(catalogConnectorService.findByFactoryClassName(className).isEmpty()) {
        catalogConnectorService.createBuiltIn(className);
      }
    }

    // run all migrations if applicable
    migrations.forEach(ConfigurationMigration::migrate);
  }

  private IntegrationFactory getFactory(String factoryClass) throws ClassNotFoundException {
    Class<?> clazz = Class.forName(factoryClass);
    return factories.stream().filter(factory -> factory.getClass().equals(clazz)).findFirst().orElseThrow();
  }

  public void activate(ConnectorInstance instance) throws ClassNotFoundException {
    IntegrationFactory factory = getFactory(instance.getCatalogConnector().getClassName());
    spawnedIntegrations.add(factory.spawn(instance));
  }
}
