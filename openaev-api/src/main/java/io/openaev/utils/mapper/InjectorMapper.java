package io.openaev.utils.mapper;

import io.openaev.database.model.*;
import io.openaev.rest.injector.form.InjectorOutput;
import jakarta.annotation.Nullable;
import java.time.Instant;
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
      ConnectorInstance connectorInstance) {

    return InjectorOutput.builder()
        .id(injector.getId())
        .name(displayName)
        .type(injector.getType())
        .catalog(catalogConnectorMapper.toCatalogSimpleOutput(catalogConnector))
        // Support semantics (same as OpenCTI): "Supported by Filigran" comes from the
        // CATALOG's verified flag, and built-in connectors
        .verified(
            !injector.isExternal() || catalogConnector != null && catalogConnector.isVerified())
        .external(injector.isExternal())
        .lastExecution(
            (!injector.isExternal()
                    && connectorInstance != null
                    && ConnectorInstance.CURRENT_STATUS_TYPE.started.equals(
                        connectorInstance.getCurrentStatus()))
                ? Instant.now()
                : injector.getUpdatedAt())
        // Can read injectors, because they have an injectorContract attached
        .canRead(true)
        // Injectors with an in-memory connectorInstance are built-in and always started.
        // Injectors without a connectorInstance are installed manually (e.g. via Docker).
        // Only injectors from the catalog with a persisted connectorInstance can be started,
        // stopped, or configured.
        .canManage(catalogConnector != null && connectorInstance != null)
        .connectorInstance(
            connectorInstance != null
                ? connectorInstanceMapper.toConnectorInstanceOutput(connectorInstance)
                : null)
        .build();
  }
}
