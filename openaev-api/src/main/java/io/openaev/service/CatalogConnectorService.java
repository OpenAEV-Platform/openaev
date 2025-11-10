package io.openaev.service;

import io.openaev.database.model.CatalogConnector;
import io.openaev.database.repository.CatalogConnectorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import static io.openaev.helper.StreamHelper.fromIterable;

import java.util.List;

@RequiredArgsConstructor
@Service
public class CatalogConnectorService {
  private final CatalogConnectorRepository catalogConnectorRepository;

  public List<CatalogConnector> saveAll(List<CatalogConnector> connectors) {
    return fromIterable(catalogConnectorRepository.saveAll(connectors));
  }
}

