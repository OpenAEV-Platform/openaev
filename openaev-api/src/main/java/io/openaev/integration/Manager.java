package io.openaev.integration;

import io.openaev.database.model.ConnectorInstance;
import io.openaev.integration.migration.ConfigurationMigration;
import io.openaev.rest.connector_instance.service.ConnectorInstanceService;
import io.openaev.service.CatalogConnectorService;
import io.openaev.utils.StringUtils;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;

public class Manager {
  private final List<IntegrationFactory> factories;
  private final List<ConfigurationMigration> migrations;
  private final CatalogConnectorService catalogConnectorService;
  private final ConnectorInstanceService connectorInstanceService;

  @Getter private final List<Integration> spawnedIntegrations = new ArrayList<>();

  public Manager(
      List<IntegrationFactory> factories,
      List<ConfigurationMigration> migrations,
      CatalogConnectorService catalogConnectorService,
      ConnectorInstanceService connectorInstanceService) {
    this.factories = factories;
    this.migrations = migrations;
    this.catalogConnectorService = catalogConnectorService;
    this.connectorInstanceService = connectorInstanceService;

    initialise();
  }

  private void initialise() {
    List<String> classes = factories.stream().map(factory -> factory.getClass().getName()).toList();

    for (String className : classes) {
      if (catalogConnectorService.findByFactoryClassName(className).isEmpty()) {
        catalogConnectorService.createBuiltIn(className);
      }
    }

    // run all migrations if applicable
    migrations.forEach(ConfigurationMigration::migrate);

    connectorInstanceService.connectorInstances().stream()
        .filter(ci -> !StringUtils.isBlank(ci.getCatalogConnector().getClassName()))
        .forEach(
            ci -> {
              try {
                this.activate(ci);
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });
  }

  private IntegrationFactory getFactory(String factoryClass) throws ClassNotFoundException {
    Class<?> clazz = Class.forName(factoryClass);
    return factories.stream()
        .filter(factory -> factory.getClass().equals(clazz))
        .findFirst()
        .orElseThrow();
  }

  public void activate(ConnectorInstance instance) throws Exception {
    IntegrationFactory factory = getFactory(instance.getCatalogConnector().getClassName());
    Integration integration = factory.spawn(instance);
    integration.start();
    spawnedIntegrations.add(integration);
  }

  public <T> T request(ComponentRequest request, Class<T> requestedType) {
    List<T> candidates =
        spawnedIntegrations.stream()
            .map(
                si -> {
                  try {
                    return si.requestComponent(request, requestedType);
                  } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                  }
                })
            .toList();

    if (candidates.isEmpty()) {
      throw new UnsupportedOperationException("No candidate for request");
    }

    return candidates.getFirst();
  }
}
