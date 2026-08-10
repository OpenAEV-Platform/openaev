package io.openaev.utils.mapper;

import io.openaev.database.model.*;
import io.openaev.rest.executor.form.ExecutorOutput;
import jakarta.annotation.Nullable;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
public class ExecutorMapper {
  private final CatalogConnectorMapper catalogConnectorMapper;
  private final ConnectorInstanceMapper connectorInstanceMapper;

  public ExecutorOutput toExecutorOutput(
      Executor executor,
      String displayName,
      @Nullable CatalogConnector catalogConnector,
      ConnectorInstance connectorInstance) {
    return ExecutorOutput.builder()
        .id(executor.getId())
        .name(displayName)
        .type(executor.getType())
        .lastExecution(
            (!executor.isExternal()
                    && connectorInstance != null
                    && ConnectorInstance.CURRENT_STATUS_TYPE.started.equals(
                        connectorInstance.getCurrentStatus()))
                ? Instant.now()
                : executor.getUpdatedAt())
        .catalog(catalogConnectorMapper.toCatalogSimpleOutput(catalogConnector))
        .verified(
            !executor.isExternal() || catalogConnector != null && catalogConnector.isVerified())
        .external(executor.isExternal())
        .platforms(executor.getPlatforms())
        .doc(executor.getDoc())
        .backgroundColor(executor.getBackgroundColor())
        // Executors whose connector instance lives only in memory are the built-in,
        // auto-start executors: they must always be running.
        // Only executor instances persisted in the database can be started, stopped,
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
