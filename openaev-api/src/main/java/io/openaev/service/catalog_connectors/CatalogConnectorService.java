package io.openaev.service.catalog_connectors;

import static io.openaev.helper.StreamHelper.fromIterable;

import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.CatalogConnectorConfiguration;
import io.openaev.database.model.ConnectorInstancePersisted;
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
  private final String EXCLUDED_CONFIG_KEY = "OPENAEV_TOKEN";
  private final CatalogConnectorRepository catalogConnectorRepository;
  private final CatalogConnectorMapper catalogConnectorMapper;
  private final ConnectorInstanceService connectorInstanceService;

  /**
   * Retrieves all unDeployed catalog connectors in CatalogConnectorOutput format.
   *
   * @return a list of catalog connector outputs with associated instance counts
   */
  public List<CatalogConnectorOutput> getUnDeployedCatalogConnectors() {
    List<ConnectorInstancePersisted> instances = connectorInstanceService.connectorInstances();
    return fromIterable(catalogConnectorRepository.findAll()).stream()
        .filter(
            c -> {
              List<ConnectorInstancePersisted> instancesMatching =
                  instances.stream()
                      .filter(i -> i.getCatalogConnector().getId().equals(c.getId()))
                      .toList();
              return instancesMatching.isEmpty();
            })
        .map(c -> catalogConnectorMapper.toCatalogConnectorOutput(c, 0))
        .toList();
  }

  /**
   * Retrieves a catalog connector by its ID as CatalogConnectorOutput format.
   *
   * @param catalogConnectorId the catalog connector ID to search for
   * @return the catalog connector output with associated instance count
   * @throws ElementNotFoundException if no catalog connector is found with the given ID
   */
  public CatalogConnectorOutput catalogConnectorOutput(String catalogConnectorId)
      throws ElementNotFoundException {
    List<ConnectorInstancePersisted> instances =
        connectorInstanceService.findAllByCatalogConnectorId(catalogConnectorId);

    return this.findById(catalogConnectorId)
        .map(c -> catalogConnectorMapper.toCatalogConnectorOutput(c, instances.size()))
        .orElseThrow(
            () ->
                new ElementNotFoundException("Connector not found with id: " + catalogConnectorId));
  }

  /**
   * Saves a list of catalog connectors.
   *
   * @param connectors the catalog connectors to save
   * @return the saved catalog connectors
   */
  public List<CatalogConnector> saveAll(List<CatalogConnector> connectors) {
    return fromIterable(catalogConnectorRepository.saveAll(connectors));
  }

  /**
   * Finds a catalog connector by its slug, including its configurations.
   *
   * @param slug the catalog connector slug to search for
   * @return an Optional containing the catalog connector if found, empty otherwise
   */
  public Optional<CatalogConnector> findBySlug(String slug) {
    return catalogConnectorRepository.findBySlugWithConfigurations(slug);
  }

  /**
   * Finds a catalog connector by its ID.
   *
   * @param id the catalog connector ID to search for
   * @return an Optional containing the catalog connector if found, empty otherwise
   */
  public Optional<CatalogConnector> findById(String id) {
    return catalogConnectorRepository.findById(id);
  }

  /**
   * Retrieve all catalog connector configurations for a specific catalog connectors
   *
   * @param catalogConnectorId the catalog connector ID to search for the configurations
   * @return a set of catalog connector configurations
   */
  public Set<CatalogConnectorConfiguration> getCatalogConnectorConfigurations(
      String catalogConnectorId) {
    return catalogConnectorRepository
        .findById(catalogConnectorId)
        .map(CatalogConnector::getCatalogConnectorConfigurations)
        .orElse(Collections.emptySet())
        .stream()
        .filter(config -> !EXCLUDED_CONFIG_KEY.equals(config.getConnectorConfigurationKey()))
        .collect(
            Collectors.toCollection(
                () ->
                    new TreeSet<>(
                        Comparator.comparing(
                            CatalogConnectorConfiguration::getConnectorConfigurationKey))));
  }

  public Optional<CatalogConnector> findByFactoryClassName(String factoryClass) {
    return catalogConnectorRepository.findByClassName(factoryClass);
  }
}
