package io.openaev.service.catalog_connectors;

import static io.openaev.helper.StreamHelper.fromIterable;

import io.openaev.database.model.CatalogConnector;
import io.openaev.database.repository.CatalogConnectorRepository;
import io.openaev.rest.catalog_connector.dto.CatalogConnectorOutput;
import io.openaev.utils.mapper.CatalogConnectorMapper;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class CatalogConnectorService {
  private final CatalogConnectorRepository catalogConnectorRepository;
  private final CatalogConnectorMapper catalogConnectorMapper;

  public List<CatalogConnectorOutput> catalogConnectors() {
    return fromIterable(catalogConnectorRepository.findAll()).stream()
        .map(catalogConnectorMapper::toCatalogConnectorOutput)
        .toList();
  }

  public List<CatalogConnector> saveAll(List<CatalogConnector> connectors) {
    return fromIterable(catalogConnectorRepository.saveAll(connectors));
  }

  public Optional<CatalogConnector> findBySlug(String slug) {
    return catalogConnectorRepository.findBySlugWithConfigurations(slug);
  }

  public Optional<CatalogConnector> findById(String id) {
    return catalogConnectorRepository.findById(id);
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
