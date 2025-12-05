package io.openaev.service.catalog_connectors;

import static io.openaev.helper.StreamHelper.fromIterable;

import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.CatalogConnectorConfiguration;
import io.openaev.database.repository.CatalogConnectorRepository;
import io.openaev.rest.catalog_connector.dto.CatalogConnectorOutput;
import io.openaev.utils.mapper.CatalogConnectorMapper;
import java.util.*;
import java.util.stream.Collectors;
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

  public Set<CatalogConnectorConfiguration> getCatalogConnectorConfigurations(
      String catalogConnectorId) {

    Set<String> EXCLUDED_CONFIG_KEYS = Set.of("OPENAEV_TOKEN");

    return catalogConnectorRepository
        .findById(catalogConnectorId)
        .map(CatalogConnector::getCatalogConnectorConfigurations)
        .orElse(Collections.emptySet())
        .stream()
        .filter(config -> !EXCLUDED_CONFIG_KEYS.contains(config.getConnectorConfigurationKey()))
        .collect(
            Collectors.toCollection(
                () ->
                    new TreeSet<>(
                        Comparator.comparing(
                            CatalogConnectorConfiguration::getConnectorConfigurationKey))));
  }
}
