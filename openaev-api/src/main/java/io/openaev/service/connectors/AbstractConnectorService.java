package io.openaev.service.connectors;

import io.openaev.database.model.*;
import io.openaev.database.repository.ConnectorInstanceConfigurationRepository;
import io.openaev.rest.catalog_connector.dto.ConnectorIds;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import io.openaev.service.exception.ConnectorStatusException;
import io.openaev.utils.mapper.CatalogConnectorMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class AbstractConnectorService<
    T extends BaseConnectorEntity & TenantIdBase, Output> {

  /**
   * An external connector that pinged within this window is considered running. Mirrors the
   * frontend liveliness threshold (LIVELINESS_THRESHOLD_MS): external connectors re-register every
   * ~40s, so two minutes without a heartbeat means the process is down.
   */
  public static final Duration ACTIVE_HEARTBEAT_WINDOW = Duration.ofMinutes(2);

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

  /**
   * Maps a connector entity to its output DTO.
   *
   * @param connector the connector entity (may be Hibernate-managed: implementations must never
   *     mutate it, see {@link #toConnectorOutput})
   * @param displayName the resolved display name to expose (instance-configured name when the
   *     connector is deployed through the Integration Manager, entity name otherwise)
   * @param catalogConnector the matching catalog entry, if any
   * @param instance the owning connector instance, if any
   * @param existingConnector false for pending connectors not yet registered
   */
  protected abstract Output mapToOutput(
      T connector,
      String displayName,
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

  private Output toConnectorOutput(T connector, ConnectorInstance instance) {
    boolean isVerified = instance != null;
    CatalogConnector catalogConnector =
        isVerified && instance instanceof ConnectorInstancePersisted
            ? ((ConnectorInstancePersisted) instance).getCatalogConnector()
            : catalogConnectorService.findBySlug(connector.getType()).orElse(null);
    // The instance-configured name is a pure display concern: resolve it into the DTO without
    // touching the managed entity. Calling connector.setName() here dirties the entity, and the
    // open-in-view session flushes that UPDATE at response commit (outside the controller's
    // read-only transaction, via the spring-session save) - a connector deleted or re-registered
    // in between then makes the flush fail with StaleStateException and the GET returns a 500
    // (issue #7092, regression of #6469).
    String displayName = connector.getName();
    if (instance instanceof ConnectorInstancePersisted persistedInstance) {
      displayName = getConfiguredConnectorName(persistedInstance).orElse(displayName);
    }
    return mapToOutput(connector, displayName, catalogConnector, instance, true);
  }

  private Output toExistingConnectorOutput(
      T connector, Map<String, ConnectorInstance> instanceMap) {
    return toConnectorOutput(connector, instanceMap.get(connector.getId()));
  }

  private T createExternalConnector(String collectorId, ConnectorInstancePersisted instance) {
    T newConnector = createNewConnector();
    newConnector.setId(collectorId);
    newConnector.setName(resolveConnectorName(instance));
    newConnector.setExternal(true);
    newConnector.setType(instance.getCatalogConnector().getSlug());
    return newConnector;
  }

  /**
   * Resolves the display name for a connector from the instance's configuration. Looks for the
   * {@code COLLECTOR_NAME / INJECTOR_NAME / EXECUTOR_NAME} configuration key. Falls back to the
   * catalog connector title if no name configuration is found.
   */
  private String resolveConnectorName(ConnectorInstancePersisted instance) {
    return getConfiguredConnectorName(instance).orElse(instance.getCatalogConnector().getTitle());
  }

  /**
   * Extracts the custom connector name from the instance configuration, if explicitly set. Looks
   * for the {@code COLLECTOR_NAME / INJECTOR_NAME / EXECUTOR_NAME} configuration key.
   *
   * @return the configured name, or empty if no custom name is set
   */
  private Optional<String> getConfiguredConnectorName(ConnectorInstancePersisted instance) {
    String nameKey = connectorType.getIdKeyName().replace("_ID", "_NAME");
    return instance.getConfigurations().stream()
        .filter(c -> nameKey.equals(c.getKey()))
        .map(c -> c.getValue().asText())
        .filter(name -> name != null && !name.isBlank())
        .findFirst();
  }

  private Map<String, ConnectorInstance> buildInstanceMap() {
    List<ConnectorInstancePersisted> instancesPersisted =
        this.connectorInstanceService.getAllConnectorInstancesPersistedByConnectorType(
            connectorType);
    List<ConnectorInstanceInMemory> instancesInMemory =
        this.connectorInstanceService.getConnectorInstancesInMemoryByConnectorType(connectorType);
    return mapInstancesByConnectorId(
        Stream.concat(instancesPersisted.stream(), instancesInMemory.stream())
            .collect(Collectors.toList()));
  }

  /**
   * Builds the Output DTO for a single connector, including its instance context (in-memory
   * auto-start instance or persisted instance), so single-resource GET endpoints expose the same
   * status information as the list endpoints.
   *
   * @param id the connector entity ID
   * @return the connector output
   * @throws ElementNotFoundException if the connector is not visible in the current tenant scope
   */
  public Output getConnectorOutput(String id) {
    T connector = getConnectorById(id);
    if (connector == null) {
      throw new ElementNotFoundException("Connector not found with id: " + id);
    }
    return toConnectorOutput(connector, findInstanceForConnector(id, connector.getTenantId()));
  }

  private ConnectorInstance findInstanceForConnector(String connectorId, String tenantId) {
    for (ConnectorInstanceInMemory instance :
        connectorInstanceService.getConnectorInstancesInMemoryByConnectorType(connectorType)) {
      if (connectorId.equals(getConnectorIdFromInstance(instance))) {
        return instance;
      }
    }
    // The persisted lookup uses a native query that bypasses the Hibernate tenant filter, so it
    // must be scoped to the connector's tenant explicitly (the same connector ID can exist in
    // several tenants).
    return connectorInstanceService
        .findPersistedByConnectorId(connectorType, connectorId, tenantId)
        .orElse(null);
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
    Map<String, ConnectorInstance> instancesByConnectorIdMap = buildInstanceMap();

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
                        newConnector.getName(),
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
    T connector = getConnectorById(connectorId);

    ConnectorInstanceConfigurationRepository.ConnectorIdsFromDatabase relatedIds =
        connectorInstanceConfigurationRepository.findInstanceAndCatalogIdsByKeyValue(
            this.connectorType.getIdKeyName(), connectorId);
    if (relatedIds != null) {
      boolean registered = connector != null;
      return catalogConnectorMapper.toConnectorIds(
          relatedIds.getCatalogConnectorId(), relatedIds.getConnectorInstanceId(), registered);
    }

    if (connector == null) {
      return catalogConnectorMapper.toConnectorIds(null, null, false);
    }

    // Connector already deployed without catalog, we will try to search matching catalog comparing
    // connectorType and catalogSlug
    CatalogConnector catalogConnector =
        catalogConnectorService.findBySlug(connector.getType()).orElse(null);
    if (catalogConnector != null) {
      return catalogConnectorMapper.toConnectorIds(catalogConnector.getId(), null, true);
    }

    // If nothing match this collector is manually deployed
    return catalogConnectorMapper.toConnectorIds(null, null, true);
  }

  /**
   * Rejects the deletion of a connector that is still running (OpenCTI parity: a started connector
   * can never be deleted, it must be stopped first).
   *
   * <p>Two cases, mirroring the frontend gating:
   *
   * <ul>
   *   <li>deployed through the Integration Manager: the owning instance decides - deletion is only
   *       allowed once a stop has been requested ({@code requestedStatus == stopping}) or is
   *       effective ({@code currentStatus == stopped});
   *   <li>unmanaged external connector: the registration heartbeat decides - a ping within {@link
   *       #ACTIVE_HEARTBEAT_WINDOW} means the container is alive and must be stopped (externally)
   *       before the row can be removed. Deleting an active row is futile anyway: the connector
   *       re-registers on its next heartbeat.
   * </ul>
   *
   * @param connector the connector entity being deleted
   * @param lastHeartbeat the connector's last registration heartbeat ({@code updatedAt})
   */
  protected void throwIfConnectorRunning(T connector, Instant lastHeartbeat)
      throws BadRequestException {
    ConnectorInstanceConfigurationRepository.ConnectorIdsFromDatabase relatedIds =
        connectorInstanceConfigurationRepository.findInstanceAndCatalogIdsByKeyValueAndTenantId(
            this.connectorType.getIdKeyName(), connector.getId(), connector.getTenantId());
    if (relatedIds != null && relatedIds.getConnectorInstanceId() != null) {
      connectorInstanceService.throwIfInstanceRunning(relatedIds.getConnectorInstanceId());
      return;
    }
    if (lastHeartbeat != null
        && lastHeartbeat.isAfter(Instant.now().minus(ACTIVE_HEARTBEAT_WINDOW))) {
      throw new BadRequestException(
          "The "
              + this.connectorType.name().toLowerCase(Locale.ROOT)
              + " "
              + connector.getName()
              + " is still running (last heartbeat "
              + lastHeartbeat
              + "): stop it before deleting it");
    }
  }

  /**
   * Deletes the connector instance that owns this connector, when the connector was deployed
   * through the Integration Manager.
   *
   * <p>The instance list is the desired state the XTM Composer polls, and the composer only tears a
   * deployment down when its id disappears from that list. Deleting the connector entity alone
   * therefore leaves the container running against a connector that no longer exists - and the
   * container recreates the entity on its next registration heartbeat, so the deletion looks like
   * it silently failed. The instance delete removes the connector entity too, hence the return
   * value: callers must not delete the row again.
   *
   * <p>Tenant safety: the owning instance is resolved with an explicit tenant predicate (native
   * queries bypass the Hibernate tenant filter) and only when the connector itself is visible in
   * the current tenant, so a foreign or crafted connector id can never tear down another tenant's
   * deployment.
   *
   * @param connectorId the collector / injector / executor id being deleted
   * @return true when an instance was found and deleted, so the connector row is already gone
   */
  protected boolean deleteOwningConnectorInstance(String connectorId)
      throws ConnectorStatusException {
    T connector = getConnectorById(connectorId);
    if (connector == null) {
      // Not a connector of the current tenant (or not registered): nothing to tear down here,
      // the caller's tenant-scoped row delete decides what happens.
      return false;
    }
    ConnectorInstanceConfigurationRepository.ConnectorIdsFromDatabase relatedIds =
        connectorInstanceConfigurationRepository.findInstanceAndCatalogIdsByKeyValueAndTenantId(
            this.connectorType.getIdKeyName(), connectorId, connector.getTenantId());
    if (relatedIds == null || relatedIds.getConnectorInstanceId() == null) {
      return false;
    }
    connectorInstanceService.deleteById(relatedIds.getConnectorInstanceId());
    return true;
  }
}
