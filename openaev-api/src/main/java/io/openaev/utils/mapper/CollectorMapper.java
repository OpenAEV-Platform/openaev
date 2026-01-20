package io.openaev.utils.mapper;

import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.Collector;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.rest.collector.form.CollectorOutput;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
public class CollectorMapper {

  private final CatalogConnectorMapper catalogConnectorMapper;

  public CollectorOutput toCollectorOutput(
      Collector collector,
      @Nullable CatalogConnector catalogConnector,
      ConnectorInstance connectorInstance,
      boolean existingCollector) {
    return CollectorOutput.builder()
        .id(collector.getId())
        .name(collector.getName())
        .type(collector.getType())
        .external(collector.isExternal())
        .lastExecution(collector.getUpdatedAt())
        .catalog(catalogConnectorMapper.toCatalogSimpleOutput(catalogConnector))
        .verified(connectorInstance != null)
        .currentStatus(connectorInstance != null ? connectorInstance.getCurrentStatus() : null)
        .existing(existingCollector)
        .build();
  }
}
