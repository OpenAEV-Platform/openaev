package io.openaev.integration;

import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorInstancePersisted;
import io.openaev.rest.connector_instance.service.ConnectorInstanceService;
import io.openaev.utils.reflection.FieldUtils;
import java.lang.reflect.Field;
import java.util.List;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class Integration {
  private final ComponentRequestEngine componentRequestEngine;
  @Getter private ConnectorInstance connectorInstance;
  private final ConnectorInstanceService connectorInstanceService;

  @Getter
  protected ConnectorInstance.CURRENT_STATUS_TYPE currentStatus =
      ConnectorInstance.CURRENT_STATUS_TYPE.stopped;

  protected Integration(
      ComponentRequestEngine componentRequestEngine,
      ConnectorInstance connectorInstance,
      ConnectorInstanceService connectorInstanceService) {
    this.componentRequestEngine = componentRequestEngine;
    this.connectorInstance = connectorInstance;
    this.connectorInstanceService = connectorInstanceService;
  }

  protected abstract void innerStart() throws Exception;

  public void start() throws Exception {
    if (ConnectorInstancePersisted.CURRENT_STATUS_TYPE.stopped.equals(this.currentStatus)) {
      this.innerStart();
      this.currentStatus = ConnectorInstance.CURRENT_STATUS_TYPE.started;
    } else {
      log.warn("Trying to start already started instance.");
    }
  }

  protected abstract void innerStop();

  public void stop() {
    this.innerStop();
    this.currentStatus = ConnectorInstancePersisted.CURRENT_STATUS_TYPE.stopped;
  }

  public void initialise() throws Exception {
    this.connectorInstance = connectorInstanceService.refresh(this.connectorInstance);
    // only try to start stopped instances
    if (ConnectorInstancePersisted.REQUESTED_STATUS_TYPE.starting.equals(
            this.connectorInstance.getRequestedStatus())
        && ConnectorInstancePersisted.CURRENT_STATUS_TYPE.stopped.equals(this.currentStatus)) {
      this.start();
    }

    // stop instances in any state
    if (ConnectorInstancePersisted.REQUESTED_STATUS_TYPE.stopping.equals(
        this.connectorInstance.getRequestedStatus())) {
      this.stop();
    }

    if (!this.currentStatus.equals(this.connectorInstance.getCurrentStatus())) {
      this.connectorInstance.setCurrentStatus(this.currentStatus);
      this.connectorInstanceService.save(connectorInstance);
    }
  }

  public <T> List<T> requestComponent(ComponentRequest request, Class<T> componentType)
      throws IllegalAccessException {
    List<Field> candidates =
        componentRequestEngine.validate(
            request,
            FieldUtils.getAllFields(this.getClass()).stream()
                .filter(f -> componentType.isAssignableFrom(f.getType()))
                .toList());

    if (candidates.size() > 1) {
      throw new IllegalStateException("Too many components qualify for request.");
    }

    return candidates.stream().map(candidate -> (T) FieldUtils.getField(this, candidate)).toList();
  }
}
