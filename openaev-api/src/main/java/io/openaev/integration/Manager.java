package io.openaev.integration;

import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorInstancePersisted;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Slf4j
public class Manager implements Runnable {
  private final List<IntegrationFactory> factories;
  private final ScheduledFuture<?> refreshInstancesTimer;

  @Getter private final Map<ConnectorInstance, Integration> spawnedIntegrations = new HashMap<>();

  public Manager(List<IntegrationFactory> factories, ThreadPoolTaskScheduler taskScheduler) {
    this.factories = factories;

    initialise();

    this.refreshInstancesTimer = taskScheduler.scheduleAtFixedRate(this, Duration.ofSeconds(15));
  }

  private void initialise() {
    // some factories are meant to be a catalog entry
    // some others not
    spawnedIntegrations.putAll(
        factories.stream()
            .flatMap(factory -> factory.initialise().stream())
            .map(integration -> Map.entry(integration.getConnectorInstance(), integration))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
  }

  private IntegrationFactory getFactory(String factoryClass) throws ClassNotFoundException {
    Class<?> clazz = Class.forName(factoryClass);
    return factories.stream()
        .filter(factory -> factory.getClass().equals(clazz))
        .findFirst()
        .orElseThrow();
  }

  public void activateInstance(ConnectorInstancePersisted instance) throws Exception {
    Optional<Integration> foundIntegration =
        Optional.ofNullable(spawnedIntegrations.getOrDefault(instance, null));
    if (foundIntegration.isEmpty()) {
      IntegrationFactory factory = getFactory(instance.getCatalogConnector().getClassName());
      Integration integration = factory.spawn(instance);
      integration.initialise();
      spawnedIntegrations.put(integration.getConnectorInstance(), integration);
    } else {
      foundIntegration.get().start();
    }
  }

  public void pauseInstance(ConnectorInstancePersisted instance) {
    Optional<Integration> foundIntegration =
        Optional.ofNullable(spawnedIntegrations.getOrDefault(instance, null));
    if (foundIntegration.isEmpty()) {
      log.warn(
          "Requesting pausing instance {} but an active integration was not found",
          instance.getId());
      return;
    }
    foundIntegration.get().stop();
  }

  public void destroyInstance(ConnectorInstancePersisted instance) {
    this.pauseInstance(instance);
    spawnedIntegrations.remove(instance);
  }

  public <T> T request(ComponentRequest request, Class<T> requestedType) {
    List<T> candidates =
        spawnedIntegrations.entrySet().stream()
            .flatMap(
                si -> {
                  try {
                    return si.getValue().requestComponent(request, requestedType).stream();
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

  @Override
  public void run() {
    spawnedIntegrations.forEach(
        (connectorInstance, integration) -> {
          try {
            integration.initialise();
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }
}
