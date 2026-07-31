package io.openaev.service.connector_instances;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorInstancePersisted;
import io.openaev.rest.exception.BadRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A started connector instance can never be deleted (OpenCTI parity): deletion requires a stop
 * requested (requested status stopping) or effective (current status stopped). Mirrors OpenCTI's
 * canDeleteConnector rule for managed connectors.
 */
class ConnectorInstanceDeletionGuardTest {

  private ConnectorInstancePersisted instance(
      ConnectorInstance.CURRENT_STATUS_TYPE current,
      ConnectorInstance.REQUESTED_STATUS_TYPE requested) {
    ConnectorInstancePersisted instance = new ConnectorInstancePersisted();
    instance.setCurrentStatus(current);
    instance.setRequestedStatus(requested);
    return instance;
  }

  @Test
  @DisplayName("A started instance without a stop request cannot be deleted")
  void startedInstanceCannotBeDeleted() {
    assertThatThrownBy(
            () ->
                ConnectorInstanceService.throwIfInstanceRunning(
                    instance(
                        ConnectorInstance.CURRENT_STATUS_TYPE.started,
                        ConnectorInstance.REQUESTED_STATUS_TYPE.starting)))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("stop it before deleting it");
  }

  @Test
  @DisplayName("A started instance with no requested status at all cannot be deleted")
  void startedInstanceWithoutRequestedStatusCannotBeDeleted() {
    assertThatThrownBy(
            () ->
                ConnectorInstanceService.throwIfInstanceRunning(
                    instance(ConnectorInstance.CURRENT_STATUS_TYPE.started, null)))
        .isInstanceOf(BadRequestException.class);
  }

  @Test
  @DisplayName("A started instance whose stop has been requested can be deleted")
  void stopRequestedInstanceCanBeDeleted() {
    assertThatCode(
            () ->
                ConnectorInstanceService.throwIfInstanceRunning(
                    instance(
                        ConnectorInstance.CURRENT_STATUS_TYPE.started,
                        ConnectorInstance.REQUESTED_STATUS_TYPE.stopping)))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("A stopped instance can be deleted")
  void stoppedInstanceCanBeDeleted() {
    assertThatCode(
            () ->
                ConnectorInstanceService.throwIfInstanceRunning(
                    instance(
                        ConnectorInstance.CURRENT_STATUS_TYPE.stopped,
                        ConnectorInstance.REQUESTED_STATUS_TYPE.starting)))
        .doesNotThrowAnyException();
  }
}
