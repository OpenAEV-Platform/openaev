package io.openaev.integration;

import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorInstancePersisted;
import io.openaev.helper.ConnectorInstanceHashHelper;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import io.openaev.utils.reflection.FieldUtils;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Objects;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
public abstract class Integration {
  private final ComponentRequestEngine componentRequestEngine;
  @Getter private ConnectorInstance connectorInstance;
  private final ConnectorInstanceService connectorInstanceService;

  @Getter
  protected ConnectorInstance.CURRENT_STATUS_TYPE currentStatus =
      ConnectorInstance.CURRENT_STATUS_TYPE.stopped;

  private String appliedHash;

  protected Integration(
      ComponentRequestEngine componentRequestEngine,
      ConnectorInstance connectorInstance,
      ConnectorInstanceService connectorInstanceService) {
    this.componentRequestEngine = componentRequestEngine;
    this.connectorInstance = connectorInstance;
    this.connectorInstanceService = connectorInstanceService;
  }

  protected abstract void innerStart() throws Exception;

  private void start() throws Exception {
    if (ConnectorInstancePersisted.CURRENT_STATUS_TYPE.stopped.equals(this.currentStatus)) {
      this.innerStart();
      this.currentStatus = ConnectorInstance.CURRENT_STATUS_TYPE.started;
      this.appliedHash = ConnectorInstanceHashHelper.computeInstanceHash(this.connectorInstance);
    } else {
      log.warn("Trying to start already started instance.");
    }
  }

  protected abstract void innerStop();

  private void stop() {
    this.innerStop();
    this.currentStatus = ConnectorInstancePersisted.CURRENT_STATUS_TYPE.stopped;
  }

  @Transactional
  public void initialise() throws Exception {
    try {
      this.connectorInstance = connectorInstanceService.refresh(this.connectorInstance);
      if (connectorInstance == null) {
        // the instance cannot be found again in the DB
        // exit early to finally block
        this.stop();
        return;
      }

      final String instanceHash =
          ConnectorInstanceHashHelper.computeInstanceHash(this.connectorInstance);

      final boolean isRunning =
          ConnectorInstancePersisted.CURRENT_STATUS_TYPE.started.equals(this.currentStatus);

      final boolean isStopped =
          ConnectorInstancePersisted.CURRENT_STATUS_TYPE.stopped.equals(this.currentStatus);

      final boolean isStoppingRequested =
          ConnectorInstancePersisted.REQUESTED_STATUS_TYPE.stopping.equals(
              this.connectorInstance.getRequestedStatus());

      final boolean isStartingRequested =
          ConnectorInstancePersisted.REQUESTED_STATUS_TYPE.starting.equals(
              this.connectorInstance.getRequestedStatus());

      final boolean hasHashChanged =
          this.appliedHash != null && !instanceHash.equals(this.appliedHash);

      if (isRunning && isStoppingRequested) {
        this.stop();
        return;
      }
      if (isRunning && hasHashChanged) {
        this.stop();
        this.start();
        return;
      }
      if (isStartingRequested && isStopped) {
        this.start();
      }

    } finally {
      // always save instance if applicable (e.g. state has changed)
      // even if something went wrong when starting the integration
      if (this.connectorInstance != null
          && !this.currentStatus.equals(this.connectorInstance.getCurrentStatus())) {
        this.connectorInstance.setCurrentStatus(this.currentStatus);
        this.connectorInstanceService.save(connectorInstance);
      }
    }
  }

  public <T> List<T> requestComponent(ComponentRequest request, Class<T> componentType)
      throws IllegalStateException {
    List<Field> candidates =
        componentRequestEngine.validate(
            request,
            FieldUtils.getAllFields(this.getClass()).stream()
                .filter(f -> componentType.isAssignableFrom(f.getType()))
                .toList());

    if (candidates.size() > 1) {
      throw new IllegalStateException("Too many components qualify for request.");
    }

    return candidates.stream()
        .map(candidate -> (T) FieldUtils.getField(this, candidate))
        .filter(Objects::nonNull)
        .toList();
  }
}
