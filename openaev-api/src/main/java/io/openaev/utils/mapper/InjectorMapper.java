package io.openaev.utils.mapper;

import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.Injector;
import io.openaev.rest.injector.form.InjectorOutput;
import io.openaev.rest.injector.output.InjectorSimple;
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
      String displayName,
      @Nullable CatalogConnector catalogConnector,
      ConnectorInstance connectorInstance,
      boolean existingInjector) {
    return InjectorOutput.builder()
        .id(injector.getId())
        .name(displayName)
        .type(injector.getType())
        .external(injector.isExternal())
        .catalog(catalogConnectorMapper.toCatalogSimpleOutput(catalogConnector))
        .verified(connectorInstance != null)
        .updatedAt(injector.getUpdatedAt())
        .existing(existingInjector)
        .connectorInstance(
            connectorInstance != null
                ? connectorInstanceMapper.toConnectorInstanceOutput(connectorInstance)
                : null)
        .build();
  }

  public InjectorSimple toInjectorSimple(@Nullable Injector injector) {
    if (injector == null) {
      return null;
    }
    return InjectorSimple.builder()
        .id(injector.getId())
        .name(injector.getName())
        .type(injector.getType())
        .build();
  }
}
