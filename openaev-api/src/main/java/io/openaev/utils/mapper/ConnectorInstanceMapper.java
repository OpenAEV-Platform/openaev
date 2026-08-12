package io.openaev.utils.mapper;

import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorInstancePersisted;
import io.openaev.rest.connector_instance.dto.ConnectorInstanceOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
public class ConnectorInstanceMapper {

  public ConnectorInstanceOutput toConnectorInstanceOutput(ConnectorInstance connectorInstance) {
    ConnectorInstanceOutput.ConnectorInstanceOutputBuilder builder =
        ConnectorInstanceOutput.builder()
            .id(connectorInstance.getId())
            .currentStatus(connectorInstance.getCurrentStatus())
            .requestedStatus(connectorInstance.getRequestedStatus());

    if (connectorInstance instanceof ConnectorInstancePersisted persisted) {
      builder
          .restartCount(persisted.getRestartCount())
          .startedAt(persisted.getStartedAt())
          .isInRebootLoop(persisted.isInRebootLoop());
    }

    return builder.build();
  }
}
