package io.openaev.utils.mapper;

import io.openaev.api.secrets_providers.form.SecretsProviderOutput;
import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.secrets.provider.SecretsProvider;
import jakarta.annotation.Nullable;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
public class SecretsProviderMapper {
  private final CatalogConnectorMapper catalogConnectorMapper;
  private final ConnectorInstanceMapper connectorInstanceMapper;

  public SecretsProviderOutput toSecretsProviderOutput(
      SecretsProvider secretsProvider,
      String displayName,
      @Nullable CatalogConnector catalogConnector,
      ConnectorInstance connectorInstance) {
    return SecretsProviderOutput.builder()
        .id(secretsProvider.getId())
        .name(displayName)
        .type(secretsProvider.getType())
        .catalog(catalogConnectorMapper.toCatalogSimpleOutput(catalogConnector))
        .verified(
            !secretsProvider.isExternal()
                || catalogConnector != null && catalogConnector.isVerified())
        .external(secretsProvider.isExternal())
        .lastExecution(Instant.now()) // builtIn
        .connectorInstance(
            connectorInstance != null
                ? connectorInstanceMapper.toConnectorInstanceOutput(connectorInstance)
                : null)
        // SecretProviders are not persisted in the database — all are built-in connectors.
        // Only SecretProviders from the catalog with a connectorInstance (including in-memory ones)
        // can be started, stopped, configured, or read.
        .canRead(catalogConnector != null && connectorInstance != null)
        .canManage(catalogConnector != null && connectorInstance != null)
        .build();
  }
}
