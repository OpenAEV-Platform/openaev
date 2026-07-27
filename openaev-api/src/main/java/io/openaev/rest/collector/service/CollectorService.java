package io.openaev.rest.collector.service;

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
import io.openaev.service.exception.ConnectorStatusException;
import io.openaev.utils.mapper.CatalogConnectorMapper;
import io.openaev.utils.mapper.CollectorMapper;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.InputStream;
import java.time.Instant;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    return collectorRepository.findByCollectorId(collectorId).orElse(null);
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

  /**
   * Full composite key lookup — use when tenantId is explicitly available (from TxCtx). Avoids
   * ambiguity on the composite PK (collector_id, tenant_id).
   */
  public Collector collector(String id, String tenantId) {
    return collectorRepository
        .findById(ConnectorCompositeId.of(id, tenantId))
        .orElseThrow(() -> new ElementNotFoundException("Collector not found with id: " + id));
  }

  /**
   * Inspector-scoped lookup — use from background services or paths where tenantId is not
   * explicitly available but the transaction is guaranteed single-tenant (e.g. forEachTenant).
   */
  public Collector collector(String id) {
    return collectorRepository
        .findByCollectorId(id)
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
   * Retrieves IDs of resources associated with a collector, using the full composite key.
   *
   * @param collectorId collector identifier
   * @param tenantId tenant for the composite PK lookup
   * @return connector instance ID and catalog connector ID if available, null values if not found
   */
  public ConnectorIds getCollectorRelationsId(String collectorId, String tenantId) {
    ConnectorInstanceConfigurationRepository.ConnectorIdsFromDatabase relatedIds =
        connectorInstanceConfigurationRepository.findInstanceAndCatalogIdsByKeyValue(
            ConnectorType.COLLECTOR.getIdKeyName(), collectorId);
    if (relatedIds != null) {
      boolean registered =
          collectorRepository.findById(ConnectorCompositeId.of(collectorId, tenantId)).isPresent();
      return catalogConnectorMapper.toConnectorIds(
          relatedIds.getCatalogConnectorId(), relatedIds.getConnectorInstanceId(), registered);
    }

    Collector collector =
        collectorRepository
            .findById(ConnectorCompositeId.of(collectorId, tenantId))
            .orElseThrow(
                () -> new ElementNotFoundException("Collector not found with id: " + collectorId));
    CatalogConnector catalogConnector =
        catalogConnectorService.findBySlug(collector.getType()).orElse(null);
    if (catalogConnector != null) {
      return catalogConnectorMapper.toConnectorIds(catalogConnector.getId(), null, true);
    }

    return catalogConnectorMapper.toConnectorIds(null, null, true);
  }

  public List<Collector> securityPlatformCollectors(@NotNull String tenantId) {
    return collectorRepository.findAllByTenantIdAndSecurityPlatformIsNotNull(tenantId);
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
              // Tenant is auto-assigned by TenantBaseListener @PrePersist
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
   * @param author optional source-declared author override for the collector's payloads and
   *     contracts; when null or blank the collector name is used as author
   * @return the persisted collector
   */
  @Transactional
  public Collector register(
      @NotNull final String tenantId,
      @NotNull String id,
      @NotNull String type,
      @NotNull String name,
      boolean external,
      int period,
      String securityPlatformId,
      InputStream iconStream,
      String author)
      throws Exception {

    if (iconStream != null) {
      fileService.uploadStream(COLLECTORS_IMAGES_BASE_PATH, type + ".png", iconStream);
    }

    CollectorType collectorType = ensureCollectorTypeExists(type);

    // Full composite key lookup: identity is (collector_id, tenant_id).
    Collector collector =
        collectorRepository.findById(ConnectorCompositeId.of(id, tenantId)).orElse(null);

    SecurityPlatform securityPlatform =
        securityPlatformId != null
            ? securityPlatformRepository
                .findByIdAndTenantId(securityPlatformId, tenantId)
                .orElse(null)
            : null;

    if (securityPlatformId != null && securityPlatform == null) {
      log.warn(
          "SecurityPlatform {} not found for tenant {} during collector registration (collector: {})",
          securityPlatformId,
          tenantId,
          id);
    }

    if (collector == null) {
      collector = new Collector();
      collector.setId(id);
      collector.setTenantId(tenantId);
      collector.setPeriod(period); // immutable after creation
    }

    collector.setName(name);
    collector.setType(type);
    collector.setCollectorType(collectorType);
    collector.setExternal(external);
    collector.setAuthor(author != null && !author.isBlank() ? author : null);
    if (external) {
      collector.setUpdatedAt(Instant.now());
    }
    if (securityPlatform != null) {
      collector.setSecurityPlatform(securityPlatform);
    }
    return collectorRepository.save(collector);
  }

  /**
   * Stamps the last execution of a collector. Used as a heartbeat by built-in collectors (which run
   * inside the platform and never go through the external registration ping) so the UI can surface
   * a truthful liveliness signal.
   *
   * @param collectorId collector identifier
   * @param tenantId tenant the collector belongs to
   */
  @Transactional
  public void updateLastExecution(@NotNull final String collectorId, @NotNull String tenantId) {
    collectorRepository
        .findByIdAndTenantId(collectorId, tenantId)
        .ifPresent(
            collector -> {
              collector.setLastExecution(Instant.now());
              collectorRepository.save(collector);
            });
  }

  /**
   * Deletes a collector and, when it was deployed through the Integration Manager, the connector
   * instance that owns it - otherwise the deployment keeps running against a collector that no
   * longer exists and recreates it on its next registration heartbeat (see {@link
   * io.openaev.service.connectors.AbstractConnectorService#deleteOwningConnectorInstance}).
   *
   * @param collectorId collector identifier
   */
  @Transactional(rollbackFor = Exception.class)
  public void deleteCollector(@NotBlank final String collectorId) throws ConnectorStatusException {
    if (!deleteOwningConnectorInstance(collectorId)) {
      collectorRepository.deleteByCollectorId(collectorId);
    }
  }
}
