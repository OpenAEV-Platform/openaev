package io.openaev.integration;

import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorInstance.CURRENT_STATUS_TYPE;
import java.util.*;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
public class Manager implements Runnable {
  private final List<IntegrationFactory> factories;

  @Getter private final Map<ConnectorInstance, Integration> spawnedIntegrations = new HashMap<>();

  public Manager(List<IntegrationFactory> factories) {
    this.factories = factories;

    initialise();
  }

  /**
   * Kickstart all collected integration factories so that they run their own initialise() routine.
   * Populates the initial collection of known (active, stopped) instances in the manager memory.
   */
  private void initialise() {
    factories.forEach(
        factory -> {
          try {
            factory.initialise();
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

  public void activateInstance(ConnectorInstance instance) throws Exception {
    Optional<Integration> foundIntegration = Optional.ofNullable(spawnedIntegrations.get(instance));
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
    Optional<Integration> foundIntegration = Optional.ofNullable(spawnedIntegrations.get(instance));
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
  @Transactional
  public void run() {
    Map<ConnectorInstance, Integration> newIntegrationsMap =
        factories.stream()
            .flatMap(
                factory -> {
                  try {
                    List<ConnectorInstance> newInstances =
                        factory.findRelatedInstances().stream()
                            .filter(ci -> !spawnedIntegrations.containsKey(ci))
                            .toList();
                    return factory.sync(newInstances).stream();
                  } catch (Exception e) {
                    throw new RuntimeException(e);
                  }
                })
            .map(integration -> Map.entry(integration.getConnectorInstance(), integration))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    spawnedIntegrations.putAll(newIntegrationsMap);

    Set<Map.Entry<ConnectorInstance, Integration>> iterator =
        new HashSet<>(spawnedIntegrations.entrySet());
    iterator.forEach(
        entry -> {
          try {
            entry.getValue().initialise();
            if (entry.getValue().getConnectorInstance() == null) {
              spawnedIntegrations.remove(entry.getKey());
            }
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }
}
