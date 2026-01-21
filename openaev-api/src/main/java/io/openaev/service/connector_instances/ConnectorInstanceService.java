package io.openaev.service.connector_instances;

import static io.openaev.config.SessionHelper.currentUser;
import static io.openaev.database.specification.TokenSpecification.fromUser;
import static io.openaev.helper.StreamHelper.fromIterable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.*;
import io.openaev.database.repository.ConnectorInstanceConfigurationRepository;
import io.openaev.database.repository.ConnectorInstanceRepository;
import io.openaev.database.repository.TokenRepository;
import io.openaev.integration.Manager;
import io.openaev.integration.ManagerFactory;
import io.openaev.rest.connector_instance.dto.ConnectorInstanceHealthInput;
import io.openaev.rest.connector_instance.dto.ConnectorInstanceOutput;
import io.openaev.rest.connector_instance.dto.CreateConnectorInstanceInput;
import io.openaev.service.connectors.ConnectorOrchestrationService;
import io.openaev.utils.mapper.ConnectorInstanceMapper;
import jakarta.persistence.EntityNotFoundException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ConnectorInstanceService {

  private final ObjectMapper objectMapper;
  private final ConnectorInstanceMapper connectorInstanceMapper;

  private final ConnectorInstanceRepository connectorInstanceRepository;
  private final ConnectorInstanceConfigurationRepository connectorInstanceConfigurationRepository;
  private final TokenRepository tokenRepository;

  private final EncryptionFactory encryptionFactory;
  private final ManagerFactory managerFactory;

  public ConnectorInstanceService(
      ObjectMapper objectMapper,
      ConnectorInstanceMapper connectorInstanceMapper,
      ConnectorInstanceRepository connectorInstanceRepository,
      ConnectorInstanceConfigurationRepository connectorInstanceConfigurationRepository,
      TokenRepository tokenRepository,
      EncryptionFactory encryptionFactory,
      // Use lazy injection to break a circular dependency
      @Lazy ManagerFactory managerFactory) {
    this.objectMapper = objectMapper;
    this.connectorInstanceMapper = connectorInstanceMapper;
    this.connectorInstanceRepository = connectorInstanceRepository;
    this.connectorInstanceConfigurationRepository = connectorInstanceConfigurationRepository;
    this.tokenRepository = tokenRepository;
    this.encryptionFactory = encryptionFactory;
    this.managerFactory = managerFactory;
  }

  /**
   * Retrieves all connector instances managed by XtmComposer with their configurations.
   *
   * @return the list of connector instances managed by XtmComposer
   */
  public List<ConnectorInstancePersisted> connectorInstancesManagedByXtmComposer() {
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
      ConnectorType connectorType) {
    List<ConnectorInstanceInMemory> instancesInMemory = new ArrayList<>();
    try {
      Manager manager = this.managerFactory.getManager();
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
   * Retrieves all connector instances.
   *
   * @return the list of connector instances
   */
  public List<ConnectorInstancePersisted> connectorInstances() {
    return fromIterable(connectorInstanceRepository.findAll());
  }

  /**
   * Finds a connector instance by its ID.
   *
   * @param id the connector instance id to search for
   * @return the connector instance matching the ID
   * @throws EntityNotFoundException if no connector instance is found with the given ID
   */
  public ConnectorInstancePersisted connectorInstanceById(String id)
      throws EntityNotFoundException {
    return connectorInstanceRepository
        .findById(id)
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
    ConnectorInstance connectorInstance = connectorInstanceById(instanceId);
    return connectorInstance.getConfigurations();
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
    ConnectorInstancePersisted instance = this.connectorInstanceById(connectorInstanceId);
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
    return (ConnectorInstancePersisted) this.save(instance);
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
   * Deletes a connector instance by its ID.
   *
   * @param id the connector instance ID to delete
   */
  public void deleteById(String id) {
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
                        String.format("Configuration key '%s' not found", conf.getKey()));
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
        "OPENAEV_TOKEN", objectMapper.getNodeFactory().textNode(token.getValue()), false, instance);
  }

  private ConnectorInstanceConfiguration createContainerIdConfiguration(
      ConnectorInstancePersisted instance, ConnectorType type) {
    return createConfiguration(
        type.getIdKeyName(),
        objectMapper.getNodeFactory().textNode(UUID.randomUUID().toString()),
        false,
        instance);
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
      CreateConnectorInstanceInput input) {
    ConnectorInstancePersisted newInstance =
        buildNewConnectorInstanceFromCatalog(catalogConnectorWithConfigMap.catalogConnector());
    List<ConnectorInstanceConfiguration> configurations =
        getConnectorInstanceConfigurationsFromInput(
            catalogConnectorWithConfigMap.configurationsMap(), newInstance, input);

    // Add OpenAEV token
    configurations.add(createTokenConfiguration(newInstance));
    // Add container ID
    configurations.add(
        createContainerIdConfiguration(
            newInstance, catalogConnectorWithConfigMap.catalogConnector().getContainerType()));

    newInstance.setConfigurations(Set.copyOf(configurations));
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
    ConnectorInstancePersisted instance = this.connectorInstanceById(connectorInstanceId);

    instance.setInRebootLoop(input.isInRebootLoop());
    instance.setStartedAt(input.getStartedAt());
    instance.setRestartCount(input.getRestartCount());

    return (ConnectorInstancePersisted) this.save(instance);
  }

  public ConnectorInstance refresh(ConnectorInstance instance) {
    if (instance instanceof ConnectorInstancePersisted) {
      return connectorInstanceRepository.findById(instance.getId()).orElse(null);
    }
    return instance;
  }

  public ConnectorInstance createAutostartInstance(String connectorId, ConnectorType type) {
    ConnectorInstanceInMemory instance = new ConnectorInstanceInMemory();
    instance.setId(connectorId);
    instance.setRequestedStatus(ConnectorInstancePersisted.REQUESTED_STATUS_TYPE.starting);
    instance.setCurrentStatus(ConnectorInstancePersisted.CURRENT_STATUS_TYPE.stopped);
    ConnectorInstanceConfiguration conf =
        createConfiguration(
            type.getIdKeyName(), objectMapper.getNodeFactory().textNode(connectorId), false, null);
    instance.setConfigurations(Set.of(conf));
    return instance;
  }
}
