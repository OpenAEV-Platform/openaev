package io.openaev.service;

import static io.openaev.helper.StreamHelper.fromIterable;

import io.openaev.database.model.CatalogConnector;
import io.openaev.database.repository.CatalogConnectorRepository;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class CatalogConnectorService {
  private final CatalogConnectorRepository catalogConnectorRepository;

  public List<CatalogConnector> saveAll(List<CatalogConnector> connectors) {
    return fromIterable(catalogConnectorRepository.saveAll(connectors));
  }

  public Optional<CatalogConnector> findByFactoryClassName(String factoryClass) {
    return catalogConnectorRepository.findByClassName(factoryClass);
  }

  public CatalogConnector createBuiltIn(String className) {
    CatalogConnector connector = new CatalogConnector();
    connector.setTitle(className);
    connector.setSlug(className);
    connector.setClassName(className);
    connector.setContainerType(CatalogConnector.CONNECTOR_TYPE.EXECUTOR);
    return catalogConnectorRepository.save(connector);
  }
}
