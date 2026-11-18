package io.openaev.utils.mapper;

import static io.openaev.service.catalog_connectors.CatalogConnectorIngestionService.OPENAEV_KEY_TOKEN;

import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorInstanceConfiguration;
import io.openaev.database.model.ConnectorInstancePersisted;
import io.openaev.rest.connector_instance.dto.ConnectorInstanceOutput;
import java.util.stream.Collectors;
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

  /**
   * Whether a configuration may be sent back to a user: not an encrypted one, and not the OpenAEV
   * token.
   */
  public static boolean isReadableConfiguration(ConnectorInstanceConfiguration configuration) {
    return !configuration.isEncrypted()
        && !OPENAEV_KEY_TOKEN.equalsIgnoreCase(configuration.getKey());
  }

  /**
   * Copies a newly created connector instance with only its readable configurations (see {@link
   * #isReadableConfiguration}).
   */
  public ConnectorInstancePersisted toCreatedConnectorInstanceResponse(
      ConnectorInstancePersisted instance) {
    ConnectorInstancePersisted response = new ConnectorInstancePersisted();
    response.setId(instance.getId());
    response.setCatalogConnector(instance.getCatalogConnector());
    response.setRestartCount(instance.getRestartCount());
    response.setStartedAt(instance.getStartedAt());
    response.setInRebootLoop(instance.isInRebootLoop());
    response.setLogs(instance.getLogs());
    response.setCurrentStatus(instance.getCurrentStatus());
    response.setSource(instance.getSource());
    response.setRequestedStatus(instance.getRequestedStatus());
    response.setConfigurations(
        instance.getConfigurations().stream()
            .filter(ConnectorInstanceMapper::isReadableConfiguration)
            .collect(Collectors.toSet()));
    return response;
  }
}
