package io.openaev.utils.mapper;

import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.Injector;
import io.openaev.rest.injector.form.InjectorOutput;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
public class InjectorMapper {
  private final CatalogConnectorMapper catalogConnectorMapper;
  private final ConnectorInstanceMapper connectorInstanceMapper;

  public InjectorOutput toInjectorOutput(
      Injector injector,
      @Nullable CatalogConnector catalogConnector,
      ConnectorInstance instance,
      boolean isVerified) {
    return InjectorOutput.builder()
        .id(injector.getId())
        .name(injector.getName())
        .type(injector.getType())
        .external(injector.isExternal())
        .catalog(catalogConnectorMapper.toCatalogSimpleOutput(catalogConnector))
        .connectorInstance(
            instance != null ? connectorInstanceMapper.toConnectorInstanceOutput(instance) : null)
        .verified(isVerified)
        .updatedAt(injector.getUpdatedAt())
        .build();
  }
}
