package io.openaev.service;

import static io.openaev.helper.StreamHelper.fromIterable;

import io.openaev.database.model.CatalogConnector;
import io.openaev.database.repository.CatalogConnectorRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


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

  public Optional<CatalogConnector> findBySlug(String slug){
    return catalogConnectorRepository.findBySlugWithConfigurations(slug);
  }


  @Transactional
  public List<CatalogConnector> upsertAll(List<CatalogConnector> connectors) {
    List<CatalogConnector> connectorsToAdd = new ArrayList<>();

    for (CatalogConnector connectorIncoming : connectors) {
      catalogConnectorRepository
          .findByTitle(connectorIncoming.getTitle())
          .ifPresent(existingConnector -> connectorIncoming.setId(existingConnector.getId()));

      connectorsToAdd.add(catalogConnectorRepository.save(connectorIncoming));
    }

    return connectorsToAdd;
  }
}
