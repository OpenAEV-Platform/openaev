package io.openaev.service;

import static io.openaev.helper.StreamHelper.fromIterable;

import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.CatalogConnector;
import io.openaev.database.repository.CatalogConnectorRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class CatalogConnectorService {
  private final CatalogConnectorRepository catalogConnectorRepository;

  public List<CatalogConnector> catalogConnectors() {
    return fromIterable(catalogConnectorRepository.findAll());
  }

  public List<CatalogConnector> saveAll(List<CatalogConnector> connectors) {
    return fromIterable(catalogConnectorRepository.saveAll(connectors));
  }

  public List<CatalogConnector> upsertAll(List<CatalogConnector> connectors) {
    List<CatalogConnector> connectorsToAdd = new ArrayList<>();

    for (CatalogConnector connectorIncoming : connectors) {
      Optional<CatalogConnector> connector = catalogConnectorRepository
          .findByTitle(connectorIncoming.getTitle());
      if (connector.isPresent()) {
        CatalogConnector catalogConnectorFromDb = connector.get();
        catalogConnectorFromDb.setDescription(connectorIncoming.getDescription());
        connectorsToAdd.add(catalogConnectorRepository.save(catalogConnectorFromDb));
      }
      else {
        connectorsToAdd.add(catalogConnectorRepository.save(connectorIncoming));
      }

    }

    return connectorsToAdd;
  }
}
