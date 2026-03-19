package io.openaev.integration;

import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorInstance.CURRENT_STATUS_TYPE;
import io.openaev.injectors.email.EmailContract;
import java.util.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
public class Manager {
  private final List<IntegrationFactory> factories;

  @Getter private final Map<ConnectorInstance, Integration> spawnedIntegrations = new HashMap<>();

  public Manager(List<IntegrationFactory> factories) throws Exception {
    this.factories = factories;

    initialise();
  }

  /**
   * Kickstart all collected integration factories so that they run their own initialise() routine.
   * Populates the initial collection of known (active, stopped) instances in the manager memory.
   */
  private void initialise() throws Exception {
    for (IntegrationFactory factory : factories) {
      try {
        factory.initialise();
      } catch (Exception e) {
        log.error("Initialisation of integration factory {} failed.", factory.getClassName(), e);
        throw e;
      }
    }
  }

  /**
   * Returns a qualified component of the requested type matching the request, found within one of
   * the spawned integrations managed by this Manager instance
   *
   * @param request a request object with the desired matching criteria
   * @param requestedType a Java class representing the desired type
   * @return an instance of an object of the requested type, if found. If more than one instance
   *     matches the request, the first occurrence is returned with no guarantee on order.
   * @param <T> the desired type of the returned object
   * @exception NoSuchElementException if no component matching the request or the requested type is
   *     found
   */
  public <T> T request(ComponentRequest request, Class<T> requestedType)
      throws IllegalStateException, NoSuchElementException {
    // only consider integrations that are running
    List<T> candidates = new ArrayList<>();
    for (Map.Entry<ConnectorInstance, Integration> si : spawnedIntegrations.entrySet()) {
      if (CURRENT_STATUS_TYPE.started.equals(si.getValue().getCurrentStatus())) {
        candidates.addAll(si.getValue().requestComponent(request, requestedType));
      }
    }

    if (candidates.isEmpty()) {
      throw new NoSuchElementException(
          String.format(
              "No candidate found for requestId=%s, requestedType=%s",
              request.identifier(), requestedType.getCanonicalName()));
    }

    return candidates.getFirst();
  }

  public io.openaev.executors.Injector requestEmailInjector() {
    return this.request(
        new ComponentRequest(EmailContract.TYPE), io.openaev.executors.Injector.class);
  }

  public io.openaev.executors.Injector requestInjectorExecutorByType(String injectorType) {
    return this.request(new ComponentRequest(injectorType), io.openaev.executors.Injector.class);
  }

  /** Not thread-safe */
  @Transactional
  public void monitorIntegrations() {
    for (IntegrationFactory factory : factories) {
      List<ConnectorInstance> newInstances =
          factory.findRelatedInstances().stream()
              .filter(ci -> !spawnedIntegrations.containsKey(ci))
              .toList();

      List<Integration> newIntegrations = factory.sync(newInstances);

      for (Integration integration : newIntegrations) {
        spawnedIntegrations.put(integration.getConnectorInstance(), integration);
      }
    }

    for (Map.Entry<ConnectorInstance, Integration> entry : spawnedIntegrations.entrySet()) {
      try {
        entry.getValue().initialise();
        if (entry.getValue().getConnectorInstance() == null) {
          spawnedIntegrations.remove(entry.getKey());
        }
      } catch (Exception e) {
        log.error(
            "There was a problem maintaining the state of integration id '{}' of type {}.",
            entry.getKey().getId(),
            entry.getValue().getClass().getCanonicalName(),
            e);
        // do not rethrow; don't break the loop
      }
    }
  }
}
