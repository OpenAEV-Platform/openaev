package io.openaev.rest.collector.service;

import static io.openaev.database.specification.CollectorSpecification.hasSecurityPlatform;
import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.service.FileService.COLLECTORS_IMAGES_BASE_PATH;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.*;
import io.openaev.database.repository.CollectorRepository;
import io.openaev.database.repository.CollectorTypeRepository;
import io.openaev.database.repository.ConnectorInstanceConfigurationRepository;
import io.openaev.database.repository.SecurityPlatformRepository;
import io.openaev.rest.catalog_connector.dto.ConnectorIds;
import io.openaev.rest.collector.form.CollectorOutput;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.service.FileService;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import io.openaev.service.connectors.AbstractConnectorService;
import io.openaev.utils.mapper.CatalogConnectorMapper;
import io.openaev.utils.mapper.CollectorMapper;
import jakarta.annotation.Resource;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import java.io.InputStream;
import java.time.Instant;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class CollectorService extends AbstractConnectorService<Collector, CollectorOutput> {

  @Resource protected ObjectMapper mapper;

  private final CollectorRepository collectorRepository;

  private final CollectorTypeRepository collectorTypeRepository;

  private final SecurityPlatformRepository securityPlatformRepository;

  private final FileService fileService;

  private final CollectorMapper collectorMapper;

  @Autowired
  public CollectorService(
      CollectorRepository collectorRepository,
      CollectorTypeRepository collectorTypeRepository,
      ConnectorInstanceConfigurationRepository connectorInstanceConfigurationRepository,
      SecurityPlatformRepository securityPlatformRepository,
      FileService fileService,
      ConnectorInstanceService connectorInstanceService,
      CatalogConnectorService catalogConnectorService,
      CollectorMapper collectorMapper,
      CatalogConnectorMapper catalogConnectorMapper) {
    super(
        ConnectorType.COLLECTOR,
        connectorInstanceConfigurationRepository,
        catalogConnectorService,
        connectorInstanceService,
        catalogConnectorMapper);
    this.collectorRepository = collectorRepository;
    this.collectorTypeRepository = collectorTypeRepository;
    this.fileService = fileService;
    this.collectorMapper = collectorMapper;
    this.securityPlatformRepository = securityPlatformRepository;
  }

  @Override
  protected List<Collector> getAllConnectors() {
    return fromIterable(this.collectors());
  }

  @Override
  protected Collector getConnectorById(String collectorId) {
    return collector(collectorId);
  }

  @Override
  protected CollectorOutput mapToOutput(
      Collector collector,
      CatalogConnector catalogConnector,
      ConnectorInstance connectorInstance,
      boolean existingCollector) {
    return collectorMapper.toCollectorOutput(
        collector, catalogConnector, connectorInstance, existingCollector);
  }

  @Override
  protected Collector createNewConnector() {
    return new Collector();
  }

  // -- CRUD --

  public Collector collector(String id) {
    return collectorRepository
        .findById(id)
        .orElseThrow(() -> new ElementNotFoundException("Collector not found with id: " + id));
  }

  /**
   * Retrieve all collectors
   *
   * @return List of collectors
   */
  public Iterable<Collector> collectors() {
    return collectorRepository.findAll();
  }

  /**
   * Retrieve all collectors.
   *
   * @param isIncludeNext Include pending collectors.
   * @return List of collector output
   */
  public Iterable<CollectorOutput> collectorsOutput(boolean isIncludeNext) {
    return getConnectorsOutput(isIncludeNext);
  }

  /**
   * Retrieves IDs of resources associated with a collector.
   *
   * @param collectorId collector identifier.
   * @return connector instance ID and catalog connector ID if available, null values if not found
   */
  public ConnectorIds getCollectorRelationsId(String collectorId) {
    return getConnectorRelationsId(collectorId);
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
    collectorToUpdate.setState(state);
    return collectorRepository.save(collectorToUpdate);
  }

  /**
   * Ensures a {@link CollectorType} row exists for the given type name. Creates one if it does not
   * already exist (upsert semantics scoped to the current tenant).
   *
   * @param type the collector type name (e.g. "openaev_crowdstrike")
   * @return the existing or newly created {@link CollectorType}
   */
  public CollectorType ensureCollectorTypeExists(String type) {
    return collectorTypeRepository
        .findByName(type)
        .orElseGet(
            () -> {
              CollectorType ct = new CollectorType(type);
              return collectorTypeRepository.save(ct);
            });
  }

  // -- ACTION --

  /**
   * Registers (or updates) a collector with upsert semantics. Handles both built-in and external
   * collectors.
   *
   * @param id collector identifier
   * @param type collector type (e.g. "openaev_crowdstrike")
   * @param name human-readable name
   * @param external whether the collector is external (true) or built-in (false)
   * @param period polling period in seconds (only relevant for external collectors)
   * @param securityPlatformId optional security platform reference
   * @param iconStream optional PNG icon data — uploaded to the file store when present
   * @return the persisted collector
   */
  @Transactional
  public Collector register(
      @NotNull String id,
      @NotNull String type,
      @NotNull String name,
      boolean external,
      int period,
      String securityPlatformId,
      InputStream iconStream)
      throws Exception {

    if (iconStream != null) {
      fileService.uploadStream(COLLECTORS_IMAGES_BASE_PATH, type + ".png", iconStream);
    }

    ensureCollectorTypeExists(type);

    Collector collector = collectorRepository.findById(id).orElse(null);

    SecurityPlatform securityPlatform =
        securityPlatformId != null
            ? securityPlatformRepository.findById(securityPlatformId).orElseThrow()
            : null;

    CollectorType collectorType = collectorTypeRepository.findByName(type).orElseThrow();

    if (collector != null) {
      collector.setName(name);
      collector.setType(type);
      collector.setCollectorType(collectorType);
      collector.setExternal(external);
      if (external) {
        collector.setUpdatedAt(Instant.now());
      }
      if (securityPlatform != null) {
        collector.setSecurityPlatform(securityPlatform);
      }
      return collectorRepository.save(collector);
    }

    Collector newCollector = new Collector();
    newCollector.setId(id);
    newCollector.setName(name);
    newCollector.setType(type);
    newCollector.setCollectorType(collectorType);
    newCollector.setExternal(external);
    newCollector.setPeriod(period);
    if (securityPlatform != null) {
      newCollector.setSecurityPlatform(securityPlatform);
    }
    return collectorRepository.save(newCollector);
  }

  public List<Collector> collectorsForPayload(String payloadId) {
    return collectorRepository.findByPayloadId(payloadId);
  }

  public List<Collector> collectorsForAtomicTesting(String injectId) {
    return collectorRepository.findByInjectId(injectId);
  }
}
