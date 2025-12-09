package io.openaev.utils.mapper;

import io.openaev.database.model.ConnectorInstance;
import io.openaev.rest.connector_instance.dto.ConnectorInstanceOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
public class ConnectorInstanceMapper {

  public ConnectorInstanceOutput toConnectorInstanceOutput(ConnectorInstance connectorInstance) {
    return ConnectorInstanceOutput.builder()
        .id(connectorInstance.getId())
        .currentStatus(connectorInstance.getCurrentStatus())
        .requestedStatus(connectorInstance.getRequestedStatus())
        .build();
  }
}
