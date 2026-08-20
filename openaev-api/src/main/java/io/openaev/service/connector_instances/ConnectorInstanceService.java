package io.openaev.service.connector_instances;

import static io.openaev.config.SessionHelper.currentUser;
import static io.openaev.database.specification.TokenSpecification.fromUser;
import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.service.catalog_connectors.CatalogConnectorIngestionService.OPENAEV_KEY_TENANT_ID;
import static io.openaev.service.catalog_connectors.CatalogConnectorIngestionService.OPENAEV_KEY_TOKEN;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.audit.AuditLoggedService;
import io.openaev.database.model.*;
import io.openaev.database.repository.*;
import io.openaev.integration.Manager;
import io.openaev.integration.ManagerFactory;
import io.openaev.rest.connector_instance.dto.ConnectorInstanceHealthInput;
import io.openaev.rest.connector_instance.dto.ConnectorInstanceOutput;
import io.openaev.rest.connector_instance.dto.CreateConnectorInstanceInput;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.service.EndpointService;
import io.openaev.service.connectors.ConnectorOrchestrationService;
import io.openaev.service.exception.ConnectorStatusException;
import io.openaev.utils.mapper.ConnectorInstanceMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class ConnectorInstanceService implements AuditLoggedService {

  private final ObjectMapper objectMapper;
  private final ConnectorInstanceMapper connectorInstanceMapper;

  private final ConnectorInstanceRepository connectorInstanceRepository;
  private final ConnectorInstanceConfigurationRepository connectorInstanceConfigurationRepository;
  private final TokenRepository tokenRepository;

  private final CollectorRepository collectorRepository;
  private final ExecutorRepository executorRepository;
  private final InjectorRepository injectorRepository;

  private final EncryptionFactory encryptionFactory;
  private final ManagerFactory managerFactory;
  private final EntityManager entityManager;
  private final EndpointService endpointService;
  private final TenantScopedTransaction tenantTx;

  public ConnectorInstanceService(
      ObjectMapper objectMapper,
      ConnectorInstanceMapper connectorInstanceMapper,
      ConnectorInstanceRepository connectorInstanceRepository,
      ConnectorInstanceConfigurationRepository connectorInstanceConfigurationRepository,
      TokenRepository tokenRepository,
      EncryptionFactory encryptionFactory,
      CollectorRepository collectorRepository,
      ExecutorRepository executorRepository,
      InjectorRepository injectorRepository,
      EntityManager entityManager,
      EndpointService endpointService,
      TenantScopedTransaction tenantTx,
      // Use lazy injection to break a circular dependency
      @Lazy ManagerFactory managerFactory) {
    this.objectMapper = objectMapper;
    this.connectorInstanceMapper = connectorInstanceMapper;
    this.connectorInstanceRepository = connectorInstanceRepository;
    this.connectorInstanceConfigurationRepository = connectorInstanceConfigurationRepository;
    this.tokenRepository = tokenRepository;
    this.encryptionFactory = encryptionFactory;
    this.collectorRepository = collectorRepository;
    this.executorRepository = executorRepository;
    this.injectorRepository = injectorRepository;
    this.entityManager = entityManager;
    this.endpointService = endpointService;
    this.tenantTx = tenantTx;
    this.managerFactory = managerFactory;
  }

  /**
   * Retrieves all connector instances managed by XtmComposer with their configurations.
   *
   * <p>Deliberately cross-tenant: one XTM Composer instance manages connector instances across
   * every tenant, so this joins the caller's already-open transaction (XtmComposerApi's
   * {@code @Transactional}, which carries no {@code TxCtx} of its own) and widens it to every
   * tenant rather than opening a nested transaction — there is no narrower scope to isolate from
   * here.
   *
   * @return the list of connector instances managed by XtmComposer
   */
  public List<ConnectorInstancePersisted> connectorInstancesManagedByXtmComposer() {
    tenantTx.setScopeOnCurrentTransaction(TxCtx.allTenants());
    return connectorInstanceRepository.findAllManagedByXtmComposerAndConfiguration();
  }

  /**
   * Retrieves all connector instances persisted in database for a specific connector type.
   *
   * @param connectorType the type of connector to filter by
   * @return the list of persisted connector instances
   */
  public List<ConnectorInstancePersisted> getAllConnectorInstancesPersistedByConnectorType(
      ConnectorType connectorType) {
    return connectorInstanceRepository.findAllByCatalogConnectorContainerType(connectorType);
  }

  /**
   * Retrieves all connector instances in memory for a specific connector type.
   *
   * @param connectorType the type of connector to filter by
   * @return the list of connector instances in memory
   */
  public List<ConnectorInstanceInMemory> getConnectorInstancesInMemoryByConnectorType(
      ConnectorType connectorType, String tenantId) {
    List<ConnectorInstanceInMemory> instancesInMemory = new ArrayList<>();
    try {
      Manager manager = this.managerFactory.getManager(tenantId);
      instancesInMemory =
          manager.getSpawnedIntegrations().keySet().stream()
              .filter(ConnectorInstanceInMemory.class::isInstance)
              .filter(
                  instance ->
                      instance.getConfigurations().stream()
                          .anyMatch(conf -> connectorType.getIdKeyName().equals(conf.getKey())))
              .map(ConnectorInstanceInMemory.class::cast)
              .collect(Collectors.toList());

    } catch (Exception e) {
      log.error("Failed to get executor connector instances in memory", e);
    }
    return instancesInMemory;
  }

  /**
   * Retrieves all connector instances in memory for a specific connector type, using the current
   * tenant from the request context.
   *
   * <p>TODO: migrate all callers to {@link
   * #getConnectorInstancesInMemoryByConnectorType(ConnectorType, String)} once tenant propagation
   * is fully in place.
   *
   * @param connectorType the type of connector to filter by
   * @return the list of connector instances in memory for the current tenant
   */
  public List<ConnectorInstanceInMemory> getConnectorInstancesInMemoryByConnectorType(
      ConnectorType connectorType) {
    return getConnectorInstancesInMemoryByConnectorType(
        connectorType, TenantContext.getCurrentTenant());
  }

  /**
   * Checks whether a started connector instance exists for the given injector.
   *
   * <p>Only applies to connectors persisted in the database. If no record is found, meaning the
   * injector was either deployed manually with no attached instance, or it is an injector that
   * starts automatically and cannot be stopped. {@code true} is returned to avoid blocking
   * executions. The same applies if any exception occurs.
   *
   * @param injectorId the injector ID to look up
   * @return {@code false} only if a connector instance is explicitly found with a non-started
   *     status; {@code true} otherwise
   */
  @Transactional(readOnly = true)
  public boolean hasStartedConnectorInstanceForInjector(final String injectorId) {
    try {
      return this.connectorInstanceConfigurationRepository
          .findStatusByKeyValue(ConnectorType.INJECTOR.getIdKeyName(), injectorId)
          // If we found a status, check if it's 'started'
          // If no record exists, return true
          .map(
              status ->
                  ConnectorInstance.CURRENT_STATUS_TYPE.started.name().equalsIgnoreCase(status))
          .orElse(true);
    } catch (Exception e) {
      log.error(
          "Failed to check started connector instance for injector with id {}", injectorId, e);
      // In case of any exception, return true to avoid blocking executions
      return true;
    }
  }

  /**
   * Resolves the persisted {@link ConnectorInstancePersisted} that owns the given connector
   * (collector / injector / executor), scoped to the connector's tenant.
   *
   * <p>{@code connector_instances}/{@code connector_instance_configurations} are now on v2
   * isolation (activated #6408). The lookup below still carries {@code tenantId} explicitly, not as
   * a leftover v1 workaround: it is a native query (bypasses the Hibernate {@code @Filter} either
   * way), and the same connector ID can exist in several tenants (collectors use a composite PK),
   * so the caller-resolved tenant must be carried explicitly to select the one matching row.
   *
   * @param connectorType the connector type whose ID key is matched in the instance configuration
   * @param connectorId the connector entity ID
   * @param tenantId the tenant owning the connector
   * @return the owning connector instance, or empty if none is found for this tenant
   */
  public Optional<ConnectorInstancePersisted> findPersistedByConnectorId(
      ConnectorType connectorType, String connectorId, String tenantId) {
    ConnectorInstanceConfigurationRepository.ConnectorIdsFromDatabase persistedId =
        this.connectorInstanceConfigurationRepository
            .findInstanceAndCatalogIdsByKeyValueAndTenantId(
                connectorType.getIdKeyName(), connectorId, tenantId);
    if (persistedId == null || persistedId.getConnectorInstanceId() == null) {
      return Optional.empty();
    }
    return this.connectorInstanceRepository.findById(persistedId.getConnectorInstanceId());
  }

  /**
   * Resolves the {@link ConnectorInstancePersisted} that owns the given executor entity, scoped to
   * the executor's tenant.
   *
   * @param executorId the executor entity ID
   * @param tenantId the tenant owning the executor
   * @return the owning connector instance
   * @throws EntityNotFoundException if no connector instance is found for the executor ID
   */
  @Transactional(readOnly = true)
  public ConnectorInstancePersisted findByExecutorId(String executorId, String tenantId) {
    return findPersistedByConnectorId(ConnectorType.EXECUTOR, executorId, tenantId)
        .orElseThrow(
            () ->
                new EntityNotFoundException(
                    "No connector instance found for executor ID: " + executorId));
  }

  /**
   * Retrieves all connector instances.
   *
   * @return the list of connector instances
   */
  public List<ConnectorInstancePersisted> connectorInstances() {
    return fromIterable(connectorInstanceRepository.findAll());
  }

  /**
   * Retrieves connector instances for a specific tenant and factory class name.
   *
   * @param tenantId the tenant ID to filter by
   * @param className the connector factory class name
   * @return the list of matching connector instances for the given tenant
   */
  public List<ConnectorInstancePersisted> connectorInstancesByTenantIdAndClassName(
      String tenantId, String className) {
    return connectorInstanceRepository.findAllByTenantIdAndCatalogConnectorClassName(
        tenantId, className);
  }

  /**
   * @param id the connector instance id to search for
   * @return the connector instance matching the ID
   * @throws EntityNotFoundException if no connector instance is found with the given ID
   */
  public ConnectorInstancePersisted connectorInstanceById(String id)
      throws EntityNotFoundException {
    return connectorInstanceRepository
        .findWithGraphById(id)
        .orElseThrow(
            () -> new EntityNotFoundException("ConnectorInstance with id " + id + " not found"));
  }

  /**
   * Finds a connector instance by ID, bypassing tenant isolation.
   *
   * <p>Use only for platform-level operations (e.g. XtmComposer callbacks) where the request is not
   * scoped to a specific tenant and the instance must be found regardless of the current tenant
   * context. Widens the caller's already-open transaction to every tenant via {@code
   * TenantScopedTransaction#setScopeOnCurrentTransaction}, which covers both the v1 {@code @Filter}
   * (disabled below, for as long as it exists) and the v2 statement inspector (once {@code
   * connector_instances} is added to {@code openaev.tenant.active-tables}) — so this stays correct
   * across the go-live cutover instead of silently starting to see zero rows.
   *
   * @param id the connector instance ID to search for
   * @return the connector instance matching the ID
   * @throws EntityNotFoundException if no connector instance is found with the given ID
   */
  public ConnectorInstancePersisted connectorInstanceByIdIgnoringTenantFilter(String id)
      throws EntityNotFoundException {
    tenantTx.setScopeOnCurrentTransaction(TxCtx.allTenants());
    entityManager.unwrap(Session.class).disableFilter("tenantFilter");
    return connectorInstanceRepository
        .findWithGraphById(id)
        .orElseThrow(
            () -> new EntityNotFoundException("ConnectorInstance with id " + id + " not found"));
  }

  /**
   * Finds a connector instance by its ID as ConnectorInstanceOutput format
   *
   * @param id the connector instance id to search for
   * @return the connector instance matching the ID
   */
  public ConnectorInstanceOutput connectorInstanceOutputById(String id) {
    return connectorInstanceMapper.toConnectorInstanceOutput(connectorInstanceById(id));
  }

  /**
   * Retrieve all connector instance configurations for a specific instance
   *
   * @param instanceId the connector instance ID to search for the configurations
   * @return a set of connector instance configurations
   */
  public Set<ConnectorInstanceConfiguration> getConnectorInstanceConfigurations(String instanceId) {
    return connectorInstanceById(instanceId).getConfigurations();
  }

  /**
   * Retrieve all connector instance configurations for a specific instance, except encrypted fields
   *
   * @param instanceId the connector instance ID to search for the configurations
   * @return a set of connector instance configurations
   */
  public Set<ConnectorInstanceConfiguration> getConnectorInstanceConfigurationsNoSecrets(
      String instanceId) {
    return getConnectorInstanceConfigurations(instanceId).stream()
        .filter(conf -> !conf.isEncrypted())
        .collect(Collectors.toSet());
  }

  /**
   * Retrieve the value of a specific connector instance configuration by instance ID and key.
   *
   * @param instanceId the connector instance ID to search for the configuration
   * @param key the configuration key to retrieve
   * @return the configuration value as a String
   */
  public String getConnectorInstanceConfigurationsByIdAndKey(String instanceId, String key) {
    return this.getConnectorInstanceConfigurations(instanceId).stream()
        .filter(c -> key.equals(c.getKey()))
        .findFirst()
        .map(c -> c.getValue().asText())
        .orElse(null);
  }

  /**
   * Update the current status for a specific connector instance
   *
   * @param connectorInstanceId the connector instance ID to update
   * @param newCurrentStatus the new current status to set
   * @return the connector instance updated
   */
  public ConnectorInstancePersisted updateCurrentStatus(
      String connectorInstanceId, ConnectorInstance.CURRENT_STATUS_TYPE newCurrentStatus) {
    ConnectorInstancePersisted instance =
        this.connectorInstanceByIdIgnoringTenantFilter(connectorInstanceId);
    instance.setCurrentStatus(newCurrentStatus);
    return (ConnectorInstancePersisted) this.save(instance);
  }

  /**
   * Update the requested status for a specific connector instance
   *
   * @param instance the connector instance to update
   * @param newRequestedStatus the new requested status to set
   * @return the connector instance updated
   */
  public ConnectorInstancePersisted updateRequestedStatus(
      ConnectorInstance instance, ConnectorInstance.REQUESTED_STATUS_TYPE newRequestedStatus) {
    instance.setRequestedStatus(newRequestedStatus);
    ConnectorInstancePersisted saved = (ConnectorInstancePersisted) this.save(instance);
    // ConnectorInstanceApi#updateRequestedStatus returns this entity directly, and its "logs"
    // collection is not @JsonIgnore. It is not part of findWithGraphById's entity graph, so it
    // stays a lazy proxy here. Jackson serializes the response after this @Transactional method
    // returns, i.e. outside the scope set_config('app.current_tenants', ...) applies to (that GUC
    // is transaction-local). Once connector_instances is v2-active, a lazy load at that point
    // would fail closed and silently serialize an empty array. Force it now, inside the scope.
    Hibernate.initialize(saved.getLogs());
    return saved;
  }

  /**
   * Saves a connector instance.
   *
   * @param connectorInstance the connector instance to save
   * @return the saved connector instance
   */
  public ConnectorInstance save(ConnectorInstance connectorInstance) {
    if (connectorInstance instanceof ConnectorInstancePersisted) {
      return connectorInstanceRepository.save((ConnectorInstancePersisted) connectorInstance);
    }
    return connectorInstance;
  }

  /**
   * Rejects the deletion of a connector instance that is still running (OpenCTI parity: a started
   * connector can never be deleted). Deletion is only allowed once a stop has been requested
   * ({@code requestedStatus == stopping}) or is effective ({@code currentStatus == stopped}).
   *
   * @param id the connector instance ID about to be deleted
   */
  public void throwIfInstanceRunning(String id) throws BadRequestException {
    connectorInstanceRepository
        .findById(id)
        .ifPresent(ConnectorInstanceService::throwIfInstanceRunning);
  }

  public static void throwIfInstanceRunning(ConnectorInstance instance) throws BadRequestException {
    boolean stopRequested =
        ConnectorInstance.REQUESTED_STATUS_TYPE.stopping.equals(instance.getRequestedStatus());
    boolean stopped =
        ConnectorInstance.CURRENT_STATUS_TYPE.stopped.equals(instance.getCurrentStatus());
    if (!stopRequested && !stopped) {
      throw new BadRequestException(
          "The connector instance is started: stop it before deleting it");
    }
  }

  /**
   * Deletes a connector instance by its ID. A started instance is rejected: it must be stopped (or
   * at least have a stop requested) first.
   *
   * @param id the connector instance ID to delete
   */
  @Transactional(rollbackFor = Exception.class)
  public void deleteById(String id) throws ConnectorStatusException {
    ConnectorInstancePersisted connectorInstance =
        connectorInstanceRepository
            .findById(id)
            .orElseThrow(
                () ->
                    new EntityNotFoundException("ConnectorInstance with id " + id + " not found"));

    throwIfInstanceRunning(connectorInstance);

    if (managerFactory
            .getManager(connectorInstance.getTenant().getId())
            .getSpawnedIntegrations()
            .get(connectorInstance)
        != null) {
      // Setting the status to stopping and immediately calling initialize to effectively stop the
      // integration
      try {
        connectorInstance.setRequestedStatus(ConnectorInstance.REQUESTED_STATUS_TYPE.stopping);
        this.save(connectorInstance);
        managerFactory
            .getManager(connectorInstance.getTenant().getId())
            .getSpawnedIntegrations()
            .get(connectorInstance)
            .initialise();
      } catch (Exception e) {
        log.error("Could not stop the connector id {} before delete", id, e);
        throw new ConnectorStatusException(
            String.format("Could not stop the connector id %s before delete", id));
      }
    }

    String connectorId =
        connectorInstance.getConfigurations().stream()
            .filter(
                c ->
                    connectorInstance
                        .getCatalogConnector()
                        .getContainerType()
                        .getIdKeyName()
                        .equals(c.getKey()))
            .map(c -> c.getValue().asText())
            .findFirst()
            .orElse(null);

    if (connectorId != null) {
      String tenantId = connectorInstance.getTenant().getId();
      switch (connectorInstance.getCatalogConnector().getContainerType()) {
        case EXECUTOR -> {
          // agent_executor_id_fk (composite, ON DELETE CASCADE) already removes the agent rows
          // in the DB; no in-transaction read of Agent follows this delete, so there is no
          // Hibernate persistence-context staleness to guard against here (contrast with
          // ExecutorService#remove, which is exercised by a test that loads an Agent in the same
          // transaction).
          endpointService.removeSourceTagsForExecutor(connectorId, tenantId);
          executorRepository.deleteByExecutorId(connectorId);
        }
        case INJECTOR -> injectorRepository.deleteByInjectorId(connectorId);
        case COLLECTOR -> collectorRepository.deleteByCollectorId(connectorId);
      }
    }

    connectorInstanceRepository.deleteById(id);
  }

  /**
   * Finds all connector instances associated with a catalog connector.
   *
   * @param connector the catalog connector to search instances for
   * @return the list of connector instances for the given catalog connector
   */
  public List<ConnectorInstancePersisted> findAllByCatalogConnector(CatalogConnector connector) {
    return connectorInstanceRepository.findAllByCatalogConnectorId(connector.getId());
  }

  /**
   * Saves a set of connector instances.
   *
   * @param instances the connector instances to save
   */
  public void saveAll(Set<ConnectorInstancePersisted> instances) {
    connectorInstanceRepository.saveAll(instances);
  }

  /**
   * Finds all connector instances by catalog connector ID.
   *
   * @param catalogId the catalog connector ID to search instances for
   * @return the list of connector instances for the given catalog connector ID
   */
  public List<ConnectorInstancePersisted> findAllByCatalogConnectorId(String catalogId) {
    return connectorInstanceRepository.findAllByCatalogConnectorId(catalogId);
  }

  private ConnectorInstancePersisted buildNewConnectorInstanceFromCatalog(
      CatalogConnector catalogConnector) {
    ConnectorInstancePersisted newInstance = new ConnectorInstancePersisted();
    newInstance.setCatalogConnector(catalogConnector);
    newInstance.setRequestedStatus(ConnectorInstance.REQUESTED_STATUS_TYPE.stopping);
    newInstance.setCurrentStatus(ConnectorInstance.CURRENT_STATUS_TYPE.stopped);
    newInstance.setSource(ConnectorInstance.SOURCE.CATALOG_DEPLOYMENT);
    return newInstance;
  }

  private ConnectorInstanceConfiguration createConfiguration(
      String key, JsonNode value, boolean isEncrypted, ConnectorInstancePersisted instance) {
    ConnectorInstanceConfiguration conf = new ConnectorInstanceConfiguration();
    conf.setKey(key);
    conf.setValue(value);
    conf.setEncrypted(isEncrypted);
    conf.setConnectorInstance(instance);
    return conf;
  }

  // --- /!\ ---  SECURITY: Do not log value until this function is DONE
  private JsonNode encryptIfSensitive(
      JsonNode value,
      CatalogConnectorConfiguration definition,
      EncryptionService encryptionService) {
    boolean isEncrypted =
        CatalogConnectorConfiguration.CONNECTOR_CONFIGURATION_FORMAT.PASSWORD.equals(
            definition.getConnectorConfigurationFormat());

    if (!isEncrypted) {
      return value;
    }

    try {
      return objectMapper.getNodeFactory().textNode(encryptionService.encrypt(value.asText()));
    } catch (Exception e) {
      throw new RuntimeException("Failed to encrypt configuration value", e);
    }
  }

  /**
   * Encrypts sensitive configuration values in the input
   *
   * @param catalogConnectorWithConfigMap the catalog connector with its configurations map
   * @param input the connector instance input containing configurations to sanitize
   * @return the input with sensitive values encrypted
   * @throws IllegalArgumentException if a configuration key is not found in the catalog connector
   */
  public CreateConnectorInstanceInput sanitizeConnectorInstanceInput(
      ConnectorOrchestrationService.CatalogConnectorWithConfigMap catalogConnectorWithConfigMap,
      CreateConnectorInstanceInput input)
      throws IllegalArgumentException {

    // --- /!\ ---  SECURITY: Do not log configuration values until this function is DONE
    EncryptionService encryptionService =
        encryptionFactory.getEncryptionService(catalogConnectorWithConfigMap.catalogConnector());

    List<CreateConnectorInstanceInput.ConfigurationInput> safeConfigurations =
        input.getConfigurations().stream()
            .map(
                conf -> {
                  CatalogConnectorConfiguration definition =
                      catalogConnectorWithConfigMap.configurationsMap().get(conf.getKey());
                  if (definition == null) {
                    throw new IllegalArgumentException(
                        String.format(
                            "Configuration key '%s' not a valid key for this integration",
                            conf.getKey()));
                  }
                  conf.setValue(encryptIfSensitive(conf.getValue(), definition, encryptionService));
                  return conf;
                })
            .toList();
    // --- /!\ --- SECURITY END

    input.setConfigurations(safeConfigurations);
    return input;
  }

  private List<ConnectorInstanceConfiguration> getConnectorInstanceConfigurationsFromInput(
      Map<String, CatalogConnectorConfiguration> configurationDefinitionsMap,
      ConnectorInstancePersisted instance,
      CreateConnectorInstanceInput input) {
    List<ConnectorInstanceConfiguration> configurations = new ArrayList<>();

    for (CreateConnectorInstanceInput.ConfigurationInput confInput : input.getConfigurations()) {
      CatalogConnectorConfiguration definition =
          configurationDefinitionsMap.get(confInput.getKey());
      boolean isEncrypted =
          CatalogConnectorConfiguration.CONNECTOR_CONFIGURATION_FORMAT.PASSWORD.equals(
              definition.getConnectorConfigurationFormat());
      ConnectorInstanceConfiguration config =
          createConfiguration(confInput.getKey(), confInput.getValue(), isEncrypted, instance);
      configurations.add(config);
    }

    return configurations;
  }

  private ConnectorInstanceConfiguration createTokenConfiguration(
      ConnectorInstancePersisted instance) {
    Token token =
        tokenRepository.findAll(fromUser(currentUser().getId())).stream()
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No token found for current user"));
    return createConfiguration(
        OPENAEV_KEY_TOKEN,
        objectMapper.getNodeFactory().textNode(token.getValue()),
        false,
        instance);
  }

  private ConnectorInstanceConfiguration createContainerIdConfiguration(
      ConnectorInstancePersisted instance, ConnectorType type) {
    return createConfiguration(
        type.getIdKeyName(),
        objectMapper.getNodeFactory().textNode(UUID.randomUUID().toString()),
        false,
        instance);
  }

  private ConnectorInstanceConfiguration createTenantIdConfiguration(
      ConnectorInstancePersisted instance, String tenantId) {
    return createConfiguration(
        OPENAEV_KEY_TENANT_ID, objectMapper.getNodeFactory().textNode(tenantId), false, instance);
  }

  /**
   * Creates a connector instance from a catalog connector.
   *
   * @param catalogConnectorWithConfigMap the catalog connector with its configurations map
   * @param input the input data for creating the connector instance
   * @return the created connector instance
   */
  public ConnectorInstancePersisted createConnectorInstance(
      ConnectorOrchestrationService.CatalogConnectorWithConfigMap catalogConnectorWithConfigMap,
      CreateConnectorInstanceInput input,
      String tenantId) {
    ConnectorInstancePersisted newInstance =
        buildNewConnectorInstanceFromCatalog(catalogConnectorWithConfigMap.catalogConnector());
    // Explicit write attribution: today TenantBaseListener's @PrePersist stamps tenant_id from
    // TenantContext as a fallback, but that listener is removed once connector_instances goes
    // fully v2 (it is a v1 pattern with no place in the v2 model). Stamp it here so creation stays
    // correct after that removal; tenantId was already resolved and validated by the caller via
    // TenantWriteScopeResolver#tenantForWrite.
    newInstance.setTenant(new Tenant(tenantId));
    List<ConnectorInstanceConfiguration> configurations =
        getConnectorInstanceConfigurationsFromInput(
            catalogConnectorWithConfigMap.configurationsMap(), newInstance, input);

    // Add OpenAEV token
    configurations.add(createTokenConfiguration(newInstance));
    configurations.add(createTenantIdConfiguration(newInstance, tenantId));
    // Add container ID if not already present (in case of a migration)
    if (input.getConfigurations().stream()
        .noneMatch(
            configurationInput ->
                configurationInput
                    .getKey()
                    .equals(
                        catalogConnectorWithConfigMap.catalogConnector().getContainerType()
                            + "_ID"))) {
      configurations.add(
          createContainerIdConfiguration(
              newInstance, catalogConnectorWithConfigMap.catalogConnector().getContainerType()));
    }

    newInstance.setConfigurations(new HashSet<>(configurations));
    return (ConnectorInstancePersisted) this.save(newInstance);
  }

  private List<ConnectorInstanceConfiguration> mergeConfigurations(
      ConnectorInstancePersisted instance,
      Map<String, ConnectorInstanceConfiguration> existingConfigurationMap,
      List<ConnectorInstanceConfiguration> newConfigurations) {

    return newConfigurations.stream()
        .map(
            newConfig -> {
              ConnectorInstanceConfiguration existingConfig =
                  existingConfigurationMap.get(newConfig.getKey());

              if (existingConfig != null) {
                existingConfig.setValue(newConfig.getValue());
                existingConfig.setEncrypted(newConfig.isEncrypted());
                return existingConfig;
              } else {
                return createConfiguration(
                    newConfig.getKey(), newConfig.getValue(), newConfig.isEncrypted(), instance);
              }
            })
        .collect(Collectors.toList());
  }

  /**
   * Update connector instance configurations
   *
   * @param connectorInstanceId the connector instance id to update from
   * @param configurationDefinitionsMap the catalog connector configurations map
   * @param input the input data for updating the connector instance configurations
   * @return the list of connector instance configurations updated
   */
  public List<ConnectorInstanceConfiguration> updateConnectorInstanceConfigurations(
      String connectorInstanceId,
      Map<String, CatalogConnectorConfiguration> configurationDefinitionsMap,
      CreateConnectorInstanceInput input) {
    ConnectorInstancePersisted instance = connectorInstanceById(connectorInstanceId);
    Map<String, ConnectorInstanceConfiguration> existingConfigurationMap =
        instance.getConfigurations().stream()
            .collect(Collectors.toMap(ConnectorInstanceConfiguration::getKey, Function.identity()));

    List<ConnectorInstanceConfiguration> newConfigurations =
        getConnectorInstanceConfigurationsFromInput(configurationDefinitionsMap, instance, input);
    List<ConnectorInstanceConfiguration> configurationsToSave =
        mergeConfigurations(instance, existingConfigurationMap, newConfigurations);

    return fromIterable(
        this.connectorInstanceConfigurationRepository.saveAll(configurationsToSave));
  }

  /**
   * Patch connector instance health check
   *
   * @param connectorInstanceId the connector instance id to update health check from
   * @param input the health check input to set
   * @return the connector instance updated
   */
  public ConnectorInstancePersisted patchConnectorInstanceHealthCheck(
      String connectorInstanceId, ConnectorInstanceHealthInput input) {
    ConnectorInstancePersisted instance =
        this.connectorInstanceByIdIgnoringTenantFilter(connectorInstanceId);

    // Capture significant state before mutation
    Map<String, Object> before = instance.significantState(objectMapper);

    instance.setInRebootLoop(input.isInRebootLoop());
    instance.setStartedAt(input.getStartedAt());
    instance.setRestartCount(input.getRestartCount());

    // Suppress audit logging for heartbeat-only updates (no significant change)
    suppressAuditIfUnchanged(before, instance.significantState(objectMapper));

    return (ConnectorInstancePersisted) this.save(instance);
  }

  public ConnectorInstance refresh(ConnectorInstance instance) {
    if (instance instanceof ConnectorInstancePersisted) {
      return connectorInstanceRepository.findWithGraphById(instance.getId()).orElse(null);
    }
    return instance;
  }

  public ConnectorInstance createAutostartInstance(
      String connectorId, String className, ConnectorType type) {
    ConnectorInstanceInMemory instance = new ConnectorInstanceInMemory();
    instance.setId(connectorId);
    instance.setClassName(className);
    instance.setRequestedStatus(ConnectorInstancePersisted.REQUESTED_STATUS_TYPE.starting);
    instance.setCurrentStatus(ConnectorInstancePersisted.CURRENT_STATUS_TYPE.stopped);
    ConnectorInstanceConfiguration conf =
        createConfiguration(
            type.getIdKeyName(), objectMapper.getNodeFactory().textNode(connectorId), false, null);
    instance.setConfigurations(Set.of(conf));
    return instance;
  }
}
