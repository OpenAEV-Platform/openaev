package io.openaev.utils.mapper;

import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.Collector;
import io.openaev.database.model.ConnectorInstance;
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
      ConnectorInstance connectorInstance,
      boolean existingCollector) {
    return CollectorOutput.builder()
        .id(collector.getId())
        .name(displayName)
        .type(collector.getType())
        .external(collector.isExternal())
        .lastExecution(lastExecution(collector))
        .catalog(catalogConnectorMapper.toCatalogSimpleOutput(catalogConnector))
        .verified(connectorInstance != null)
        .existing(existingCollector)
        .connectorInstance(
            connectorInstance != null
                ? connectorInstanceMapper.toConnectorInstanceOutput(connectorInstance)
                : null)
        .build();
  }

  /**
   * The last execution shown for a collector. An external collector pings the platform on every
   * run, which stamps {@code updatedAt}. A built-in collector runs inside the platform and never
   * pings: its runs stamp {@code lastExecution} (see {@code CollectorService#updateLastExecution}),
   * so its {@code updatedAt} stays at its registration time. Falls back to {@code updatedAt} for a
   * built-in collector that has not run yet.
   *
   * @param collector the collector
   * @return the instant of its last execution, or null when unknown
   */
  @Nullable
  static Instant lastExecution(Collector collector) {
    if (!collector.isExternal() && collector.getLastExecution() != null) {
      return collector.getLastExecution();
    }
    return collector.getUpdatedAt();
  }
}
