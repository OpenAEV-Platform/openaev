package io.openaev.utils.mapper;

import io.openaev.database.model.*;
import io.openaev.rest.collector.form.CollectorOutput;
import jakarta.annotation.Nullable;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
public class CollectorMapper {

  private final CatalogConnectorMapper catalogConnectorMapper;
  private final ConnectorInstanceMapper connectorInstanceMapper;

  public CollectorOutput toCollectorOutput(
      Collector collector,
      String displayName,
      @Nullable CatalogConnector catalogConnector,
      ConnectorInstance connectorInstance) {
    Instant lastExecution;
    if (!collector.isExternal()
        && connectorInstance != null
        && ConnectorInstance.CURRENT_STATUS_TYPE.started.equals(
            connectorInstance.getCurrentStatus())) {
      lastExecution = Instant.now();
    } else if (collector.getLastExecution() != null && collector.getLastExecution().isAfter(collector.getUpdatedAt())) {
      lastExecution = collector.getLastExecution();
    } else {
      lastExecution = collector.getUpdatedAt();
    }
    return CollectorOutput.builder()
        .id(collector.getId())
        .name(displayName)
        .type(collector.getType())
        .lastExecution(lastExecution)
        .catalog(catalogConnectorMapper.toCatalogSimpleOutput(catalogConnector))
        .verified(
            !collector.isExternal() || catalogConnector != null && catalogConnector.isVerified())
        .external(collector.isExternal())
        // Collectors whose connector instance lives only in memory are the built-in,
        // auto-start collectors: they must always be running.
        // Only collector instances persisted in the database can be started, stopped,
        // or configured.
        .canRead(catalogConnector != null && connectorInstance != null)
        .canManage(catalogConnector != null && connectorInstance != null)
        .connectorInstance(
            connectorInstance != null
                ? connectorInstanceMapper.toConnectorInstanceOutput(connectorInstance)
                : null)
        .build();
  }
}
