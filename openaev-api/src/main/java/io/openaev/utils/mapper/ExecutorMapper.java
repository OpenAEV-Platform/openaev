package io.openaev.utils.mapper;

import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.Executor;
import io.openaev.rest.executor.form.ExecutorOutput;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
public class ExecutorMapper {
  private final CatalogConnectorMapper catalogConnectorMapper;

  public ExecutorOutput toExecutorOutput(
      Executor executor,
      @Nullable CatalogConnector catalogConnector,
      ConnectorInstance connectorInstance,
      boolean existingExecutor) {
    return ExecutorOutput.builder()
        .id(executor.getId())
        .name(executor.getName())
        .type(executor.getType())
        .updatedAt(executor.getUpdatedAt())
        .catalog(catalogConnectorMapper.toCatalogSimpleOutput(catalogConnector))
        .verified(connectorInstance != null)
        .platforms(executor.getPlatforms())
        .doc(executor.getDoc())
        .backgroundColor(executor.getBackgroundColor())
        .currentStatus(connectorInstance != null ? connectorInstance.getCurrentStatus() : null)
        .existing(existingExecutor)
        .build();
  }
}
