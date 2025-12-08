package io.openaev.service.catalog_connectors;

import static io.openaev.helper.StreamHelper.fromIterable;

import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.CatalogConnectorConfiguration;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.repository.CatalogConnectorRepository;
import io.openaev.rest.catalog_connector.dto.CatalogConnectorOutput;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.service.connector_instances.ConnectorInstanceService;
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
  private final ConnectorInstanceService connectorInstanceService;

  public List<CatalogConnectorOutput> catalogConnectors() {
    List<ConnectorInstance> instances = connectorInstanceService.connectorInstances();
    return fromIterable(catalogConnectorRepository.findAll()).stream()
        .map(c-> {
          List<ConnectorInstance> instancesMatching = instances.stream().filter(i -> i.getCatalogConnector().getId().equals(c.getId())).toList();
          return catalogConnectorMapper.toCatalogConnectorOutput(c, instancesMatching.size());
        })
        .toList();
  }

  public CatalogConnectorOutput catalogConnectorOutput(String catalogConnectorId) {
    List<ConnectorInstance> instances = connectorInstanceService.findAllByCatalogConnectorId(catalogConnectorId);

    return this.findById(catalogConnectorId)
            .map(c-> catalogConnectorMapper.toCatalogConnectorOutput(c, instances.size()))
            .orElseThrow(
                    () -> new ElementNotFoundException("Connector not found with id: " + catalogConnectorId));
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
