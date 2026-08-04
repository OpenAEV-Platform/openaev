package io.openaev.utils.mapper;

import io.openaev.api.secrets_providers.form.SecretsProviderOutput;
import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.secrets.provider.AbstractSecretsProvider;
import jakarta.annotation.Nullable;
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
      AbstractSecretsProvider secretsProvider,
      String displayName,
      @Nullable CatalogConnector catalogConnector,
      ConnectorInstance connectorInstance,
      boolean existingConnector) {
    return SecretsProviderOutput.builder()
        .id(secretsProvider.getId())
        .name(displayName)
        .type(secretsProvider.getType())
        .catalog(catalogConnectorMapper.toCatalogSimpleOutput(catalogConnector))
        .verified(connectorInstance != null)
        .connectorInstance(
            connectorInstance != null
                ? connectorInstanceMapper.toConnectorInstanceOutput(connectorInstance)
                : null)
        .existing(existingConnector)
        .build();
  }
}
