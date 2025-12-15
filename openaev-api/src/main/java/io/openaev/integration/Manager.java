package io.openaev.integration;

import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorInstance.CURRENT_STATUS_TYPE;
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

  /**
   * Kickstart all collected integration factories so that they run their own initialise() routine.
   * Populates the initial collection of known (active, stopped) instances in the manager memory.
   */
  private void initialise() {
    spawnedIntegrations.putAll(
        factories.stream()
            .flatMap(
                factory -> {
                  try {
                    return factory.initialise().stream();
                  } catch (Exception e) {
                    throw new RuntimeException(e);
                  }
                })
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

  public void activateInstance(ConnectorInstance instance) throws Exception {
    Optional<Integration> foundIntegration =
        Optional.ofNullable(spawnedIntegrations.getOrDefault(instance, null));
    if (foundIntegration.isEmpty()) {
      IntegrationFactory factory = getFactory(instance.getClassName());
      Integration integration = factory.spawn(instance);
      integration.initialise();
      spawnedIntegrations.put(integration.getConnectorInstance(), integration);
    } else {
      foundIntegration.get().start();
    }
  }

  public void pauseInstance(ConnectorInstance instance) {
    Optional<Integration> foundIntegration =
        Optional.ofNullable(spawnedIntegrations.getOrDefault(instance, null));
    if (foundIntegration.isEmpty()) {
      log.warn(
          "Requesting pausing instance {} but an related integration was not found.", instance);
      return;
    }
    foundIntegration.get().stop();
  }

  public void destroyInstance(ConnectorInstance instance) {
    this.pauseInstance(instance);
    spawnedIntegrations.remove(instance);
  }

  public <T> T request(ComponentRequest request, Class<T> requestedType) {
    List<T> candidates =
        spawnedIntegrations.entrySet().stream()
            // only consider integrations that are running
            .filter(si -> CURRENT_STATUS_TYPE.started.equals(si.getValue().getCurrentStatus()))
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
