package io.openaev.service;

import io.openaev.database.model.CatalogConnectorConfiguration;
import io.openaev.database.repository.CatalogConnectorConfigurationRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class CatalogConnectorConfigurationService {
  private final CatalogConnectorConfigurationRepository catalogConnectorConfigurationRepository;

  public void upsertAll(List<CatalogConnectorConfiguration> configs) {

    for (CatalogConnectorConfiguration connectorConfiguration : configs) {

      catalogConnectorConfigurationRepository
          .findByCatalogConnectorAndConnectorConfigurationKey(
              connectorConfiguration.getCatalogConnector(),
              connectorConfiguration.getConnectorConfigurationKey())
          .ifPresent(existing -> connectorConfiguration.setId(existing.getId()));

      catalogConnectorConfigurationRepository.save(connectorConfiguration);
    }
  }
}
