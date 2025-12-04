package io.openaev.rest.collector.service;

import static io.openaev.database.specification.CollectorSpecification.hasSecurityPlatform;
import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.service.FileService.COLLECTORS_IMAGES_BASE_PATH;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.Collector;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.repository.CollectorRepository;
import io.openaev.database.repository.ConnectorInstanceConfigurationRepository;
import io.openaev.rest.collector.form.CollectorOutput;
import io.openaev.rest.catalog_connector.dto.ConnectorIds;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.service.ConnectorInstanceService;
import io.openaev.service.FileService;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import io.openaev.utils.mapper.CatalogConnectorMapper;
import io.openaev.utils.mapper.CollectorMapper;
import jakarta.annotation.Resource;
import jakarta.transaction.Transactional;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CollectorService {

  @Resource protected ObjectMapper mapper;

  private final CollectorRepository collectorRepository;
  private final ConnectorInstanceConfigurationRepository connectorInstanceConfigurationRepository;

  private final FileService fileService;
  private final ConnectorInstanceService connectorInstanceService;
  private final CatalogConnectorService catalogConnectorService;

  private final CollectorMapper collectorMapper;
  @Autowired  private CatalogConnectorMapper catalogConnectorMapper;

  // -- CRUD --

  public Collector collector(String id) {
    return collectorRepository
        .findById(id)
        .orElseThrow(() -> new ElementNotFoundException("Collector not found with id: " + id));
  }

  public Iterable<Collector> collectors() {
    return collectorRepository.findAll();
  }

  private String getCollectorIdFromInstance(ConnectorInstance instance) {
    return instance.getConfigurations().stream()
        .filter(c -> "COLLECTOR_ID".equals(c.getKey()))
        .map(c -> c.getValue().asText())
        .findFirst()
        .orElse(null);
  }

  private Map<String, ConnectorInstance> mapInstancesByCollectorId(
      List<ConnectorInstance> instances) {
    Map<String, ConnectorInstance> map = new HashMap<>();
    instances.forEach(
        instance -> {
          String collectorId = getCollectorIdFromInstance(instance);
          if (collectorId != null) {
            map.put(collectorId, instance);
          }
        });
    return map;
  }

  private Collector createExternalCollector(String collectorId, ConnectorInstance instance) {
    Collector newCollector = new Collector();
    newCollector.setId(collectorId);
    newCollector.setName(instance.getCatalogConnector().getTitle());
    newCollector.setExternal(true);
    newCollector.setType(instance.getCatalogConnector().getSlug());
    return newCollector;
  }

  private CollectorOutput toCollectorOutput(
      Collector collector, Map<String, ConnectorInstance> instanceMap) {
    ConnectorInstance instance = instanceMap.get(collector.getId());
    boolean isVerified = instance != null;
    CatalogConnector catalogConnector = isVerified ? instance.getCatalogConnector():
            catalogConnectorService.findBySlug(collector.getType().replace("openaev_", "")).orElse(null);
    return collectorMapper.toCollectorOutput(collector, catalogConnector, isVerified);
  }

  public Iterable<CollectorOutput> collectorsOutput() {
    List<ConnectorInstance> collectorInstances = connectorInstanceService.collectorConnectorInstances();
    Map<String, ConnectorInstance> instanceByCollectorIdMap = mapInstancesByCollectorId(collectorInstances);
    List<Collector> collectors = fromIterable(collectorRepository.findAll());

    return collectors.stream()
        .map(collector -> toCollectorOutput(collector, instanceByCollectorIdMap))
        .toList();
  }

  public Iterable<CollectorOutput> getCollectorsOutputWithNextCollectors() {
    List<Collector> collectors = fromIterable(this.collectors());
    Set<String> existingCollectorIds = collectors.stream().map(Collector::getId).collect(Collectors.toSet());

    List<ConnectorInstance> collectorInstances = connectorInstanceService.collectorConnectorInstances();
    Map<String, ConnectorInstance> instanceMap = mapInstancesByCollectorId(collectorInstances);

    List<CollectorOutput> result = new ArrayList<>();

    // Add existing collectors
    collectors.forEach(
        collector -> result.add(toCollectorOutput(collector, instanceMap)));

    // Add new collectors from instances, these collectors are waiting to be deployed
    instanceMap.entrySet().stream()
        .filter(entry -> entry.getKey() != null && !existingCollectorIds.contains(entry.getKey()))
        .forEach(
            entry -> {
              Collector newCollector = createExternalCollector(entry.getKey(), entry.getValue());
              result.add(
                  collectorMapper.toCollectorOutput(
                      newCollector, entry.getValue().getCatalogConnector(), true));
            });

    return result;
  }

  public ConnectorIds getCollectorRelationsId(String collectorId){
    ConnectorInstanceConfigurationRepository.ConnectorIdsFomDatabase relatedIds = connectorInstanceConfigurationRepository.findInstanceAndCatalogIdsByKeyValue("COLLECTOR_ID", collectorId);
    if (relatedIds != null) {
      return catalogConnectorMapper.toConnectorIds(relatedIds.getCatalogConnectorId(), relatedIds.getConnectorInstanceId());
    }

    // Collector already deployed without catalog, we will try to search matching catalog comparing collectorType and catalogSlug
    Collector collector = collector(collectorId);
    CatalogConnector catalogConnector = catalogConnectorService.findBySlug(collector.getType().replace("openaev_", "")).orElse(null);
    if (catalogConnector != null) {
      return catalogConnectorMapper.toConnectorIds(catalogConnector.getId(), null);
    }

    // If nothing match this collector is manually deployed
    return catalogConnectorMapper.toConnectorIds(null, null);
  }

  public Collector collectorByType(String type) {
    return collectorRepository
        .findByType(type)
        .orElseThrow(() -> new ElementNotFoundException("Collector not found with type: " + type));
  }

  public List<Collector> securityPlatformCollectors() {
    return fromIterable(collectorRepository.findAll(hasSecurityPlatform()));
  }

  public Collector updateCollectorState(Collector collectorToUpdate, ObjectNode newState) {
    ObjectNode state =
        Optional.ofNullable(collectorToUpdate.getState()).orElse(mapper.createObjectNode());
    newState
        .fieldNames()
        .forEachRemaining(fieldName -> state.set(fieldName, newState.get(fieldName)));
    return collectorRepository.save(collectorToUpdate);
  }

  // -- ACTION --

  @Transactional
  public void register(String id, String type, String name, InputStream iconData) throws Exception {
    if (iconData != null) {
      fileService.uploadStream(COLLECTORS_IMAGES_BASE_PATH, type + ".png", iconData);
    }
    Collector collector = collectorRepository.findById(id).orElse(null);
    if (collector == null) {
      Collector collectorChecking = collectorRepository.findByType(type).orElse(null);
      if (collectorChecking != null) {
        throw new Exception(
            "The collector "
                + type
                + " already exists with a different ID, please delete it or contact your administrator.");
      }
    }
    if (collector != null) {
      collector.setName(name);
      collector.setExternal(false);
      collector.setType(type);
      collectorRepository.save(collector);
    } else {
      // save the collector
      Collector newCollector = new Collector();
      newCollector.setId(id);
      newCollector.setName(name);
      newCollector.setType(type);
      collectorRepository.save(newCollector);
    }
  }

  public List<Collector> collectorsForPayload(String payloadId) {
    return collectorRepository.findByPayloadId(payloadId);
  }

  @Query(
      "SELECT c FROM Collector c WHERE c.detectionRemediations.payload.injector.contracts.injects.injectId = :injectId")
  public List<Collector> collectorsForAtomicTesting(String injectId) {
    return collectorRepository.findByInjectId(injectId);
  }
}
