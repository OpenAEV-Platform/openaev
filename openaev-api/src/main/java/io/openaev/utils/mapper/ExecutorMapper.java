package io.openaev.utils.mapper;

import io.openaev.database.model.CatalogConnector;
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
      Executor executor, @Nullable CatalogConnector catalogConnector, boolean isVerified) {
    return ExecutorOutput.builder()
        .id(executor.getId())
        .name(executor.getName())
        .type(executor.getType())
        .updatedAt(executor.getUpdatedAt())
        .catalog(catalogConnectorMapper.toCatalogSimpleOutput(catalogConnector))
        .verified(isVerified)
        .platforms(executor.getPlatforms())
        .doc(executor.getDoc())
        .backgroundColor(executor.getBackgroundColor())
        .build();
  }
}
