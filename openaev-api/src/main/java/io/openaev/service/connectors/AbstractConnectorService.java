package io.openaev.service.connectors;

import io.openaev.database.model.*;
import io.openaev.database.repository.ConnectorInstanceConfigurationRepository;
import io.openaev.rest.catalog_connector.dto.ConnectorIds;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import io.openaev.utils.mapper.CatalogConnectorMapper;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class AbstractConnectorService<T extends BaseConnectorEntity, Output> {
  protected final ConnectorType connectorType;
  protected final ConnectorInstanceConfigurationRepository connectorInstanceConfigurationRepository;
  protected final CatalogConnectorService catalogConnectorService;
  protected final ConnectorInstanceService connectorInstanceService;
  protected final CatalogConnectorMapper catalogConnectorMapper;

  protected AbstractConnectorService(
      ConnectorType connectorType,
      ConnectorInstanceConfigurationRepository connectorInstanceConfigurationRepository,
      CatalogConnectorService catalogConnectorService,
      ConnectorInstanceService connectorInstanceService,
      CatalogConnectorMapper catalogConnectorMapper) {
    this.connectorType = connectorType;
    this.connectorInstanceConfigurationRepository = connectorInstanceConfigurationRepository;
    this.catalogConnectorService = catalogConnectorService;
    this.connectorInstanceService = connectorInstanceService;
    this.catalogConnectorMapper = catalogConnectorMapper;
  }

  protected abstract List<T> getAllConnectors();

  protected abstract T getConnectorById(String id);

  protected abstract Output mapToOutput(
      T connector,
      CatalogConnector catalogConnector,
      ConnectorInstance instance,
      boolean existingConnector);

  protected abstract T createNewConnector();

  private String getConnectorIdFromInstance(ConnectorInstance instance) {
    return instance.getConfigurations().stream()
        .filter(c -> this.connectorType.getIdKeyName().equals(c.getKey()))
        .map(c -> c.getValue().asText())
        .findFirst()
        .orElse(null);
  }

  private Map<String, ConnectorInstance> mapInstancesByConnectorId(
      List<ConnectorInstance> instances) {
    Map<String, ConnectorInstance> map = new HashMap<>();
    instances.forEach(
        instance -> {
          String connectorId = getConnectorIdFromInstance(instance);
          if (connectorId != null) {
            map.put(connectorId, instance);
          }
        });
    return map;
  }

  private Output toExistingConnectorOutput(
      T connector, Map<String, ConnectorInstance> instanceMap) {
    ConnectorInstance instance = instanceMap.get(connector.getId());
    boolean isVerified = instance != null;
    CatalogConnector catalogConnector =
        isVerified && instance instanceof ConnectorInstancePersisted
            ? ((ConnectorInstancePersisted) instance).getCatalogConnector()
            : catalogConnectorService.findBySlug(connector.getType()).orElse(null);
    return mapToOutput(connector, catalogConnector, instance, true);
  }

  private T createExternalConnector(String collectorId, ConnectorInstancePersisted instance) {
    T newConnector = createNewConnector();
    newConnector.setId(collectorId);
    newConnector.setName(instance.getCatalogConnector().getTitle());
    newConnector.setExternal(true);
    newConnector.setType(instance.getCatalogConnector().getSlug());
    return newConnector;
  }

  /**
   * Retrieves all connectors including those pending deployment. Pending collectors are identified
   * through their connector instances that exist but haven't yet been registered in the
   * collector/injector/executor registry. This typically occurs during the deployment process with
   * XTMComposer we first create a connector_instance but the connector hasn't completed
   * initialization.
   *
   * @param includeNext Include or not pending connector
   * @return list of connectors
   */
  public Iterable<Output> getConnectorsOutput(boolean includeNext) {
    List<T> connectors = getAllConnectors();
    List<ConnectorInstancePersisted> instancesPersisted =
        this.connectorInstanceService.getAllConnectorInstancesPersistedByConnectorType(
            connectorType);
    List<ConnectorInstanceInMemory> instancesInMemory =
        this.connectorInstanceService.getConnectorInstancesInMemoryByConnectorType(connectorType);

    Map<String, ConnectorInstance> instancesByConnectorIdMap =
        mapInstancesByConnectorId(
            Stream.concat(instancesPersisted.stream(), instancesInMemory.stream())
                .collect(Collectors.toList()));

    List<Output> result = new ArrayList<>();

    // Add existing collectors
    connectors.forEach(
        connector -> result.add(toExistingConnectorOutput(connector, instancesByConnectorIdMap)));

    if (includeNext) {
      // Add new connectors from instances, these collectors are waiting to be deployed
      Set<String> existingConnectorsIds =
          connectors.stream().map(BaseConnectorEntity::getId).collect(Collectors.toSet());
      instancesByConnectorIdMap.entrySet().stream()
          .filter(
              entry -> entry.getKey() != null && !existingConnectorsIds.contains(entry.getKey()))
          .filter(entry -> entry.getValue() instanceof ConnectorInstancePersisted)
          .map(entry -> Map.entry(entry.getKey(), (ConnectorInstancePersisted) entry.getValue()))
          .forEach(
              entry -> {
                T newConnector = createExternalConnector(entry.getKey(), entry.getValue());
                result.add(
                    mapToOutput(
                        newConnector,
                        entry.getValue().getCatalogConnector(),
                        entry.getValue(),
                        false));
              });
    }

    return result;
  }

  /**
   * Retrieves IDs of resources associated with a connector.
   *
   * @param connectorId the connector identifier
   * @return connector instance ID and catalog connector ID if available, null values if not found
   */
  public ConnectorIds getConnectorRelationsId(String connectorId) {
    ConnectorInstanceConfigurationRepository.ConnectorIdsFomDatabase relatedIds =
        connectorInstanceConfigurationRepository.findInstanceAndCatalogIdsByKeyValue(
            this.connectorType.getIdKeyName(), connectorId);
    if (relatedIds != null) {
      return catalogConnectorMapper.toConnectorIds(
          relatedIds.getCatalogConnectorId(), relatedIds.getConnectorInstanceId());
    }

    // Connector already deployed without catalog, we will try to search matching catalog comparing
    // connectorType and catalogSlug
    T connector = getConnectorById(connectorId);
    CatalogConnector catalogConnector =
        catalogConnectorService.findBySlug(connector.getType()).orElse(null);
    if (catalogConnector != null) {
      return catalogConnectorMapper.toConnectorIds(catalogConnector.getId(), null);
    }

    // If nothing match this collector is manually deployed
    return catalogConnectorMapper.toConnectorIds(null, null);
  }
}
