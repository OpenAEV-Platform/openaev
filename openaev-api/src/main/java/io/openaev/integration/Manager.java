package io.openaev.integration;

import io.openaev.database.model.ConnectorInstance;
import io.openaev.integration.migration.ConfigurationMigration;
import io.openaev.rest.connector_instance.service.ConnectorInstanceService;
import io.openaev.service.CatalogConnectorService;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;

public class Manager {
  private final List<IntegrationFactory> factories;

  @Getter private final List<Integration> spawnedIntegrations = new ArrayList<>();

  public Manager(
      List<IntegrationFactory> factories,
      List<ConfigurationMigration> migrations,
      CatalogConnectorService catalogConnectorService,
      ConnectorInstanceService connectorInstanceService) {
    this.factories = factories;

    initialise();
  }

  private void initialise() {
    // some factories are meant to be a catalog entry
    // some others not
    spawnedIntegrations.addAll(
        factories.stream().flatMap(factory -> factory.initialise().stream()).toList());
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
    integration.initialise();
    spawnedIntegrations.add(integration);
  }

  public <T> T request(ComponentRequest request, Class<T> requestedType) {
    List<T> candidates =
        spawnedIntegrations.stream()
            .flatMap(
                si -> {
                  try {
                    return si.requestComponent(request, requestedType).stream();
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
