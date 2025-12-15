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
import io.openaev.rest.connector_instance.dto.ConnectorInstanceHealthInput;
import io.openaev.rest.connector_instance.dto.ConnectorInstanceOutput;
import io.openaev.rest.connector_instance.dto.CreateConnectorInstanceInput;
import io.openaev.utils.mapper.ConnectorInstanceMapper;
import jakarta.persistence.EntityNotFoundException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConnectorInstanceService {

  private final ObjectMapper objectMapper;
  private final ConnectorInstanceMapper connectorInstanceMapper;

  private final ConnectorInstanceRepository connectorInstanceRepository;
  private final ConnectorInstanceConfigurationRepository connectorInstanceConfigurationRepository;
  private final TokenRepository tokenRepository;

  private final EncryptionFactory encryptionFactory;

  /**
   * Retrieves all connector instances managed by XtmComposer with their configurations.
   *
   * @return the list of connector instances managed by XtmComposer
   */
  public List<ConnectorInstance> connectorInstancesManagedByXtmComposer() {
    return connectorInstanceRepository.findAllManagedByXtmComposerAndConfiguration();
  }

  /**
   * Retrieves all connector instances of type INJECTOR.
   *
   * @return the list of injector connector instances
   */
  public List<ConnectorInstance> injectorConnectorInstances() {
    return connectorInstanceRepository.findAllByCatalogConnectorContainerType(
        CatalogConnector.CONNECTOR_TYPE.INJECTOR);
  }

  /**
   * Retrieves all connector instances of type COLLECTOR.
   *
   * @return the list of collector connector instances
   */
  public List<ConnectorInstance> collectorConnectorInstances() {
    return connectorInstanceRepository.findAllByCatalogConnectorContainerType(
        CatalogConnector.CONNECTOR_TYPE.COLLECTOR);
  }

  /**
   * Retrieves all connector instances of type EXECUTOR.
   *
   * @return the list of executor connector instances
   */
  public List<ConnectorInstance> executorConnectorInstances() {
    return connectorInstanceRepository.findAllByCatalogConnectorContainerType(
        CatalogConnector.CONNECTOR_TYPE.EXECUTOR);
  }

  /**
   * Retrieves all connector instances.
   *
   * @return the list of connector instances
   */
  public List<ConnectorInstance> connectorInstances() {
    return fromIterable(connectorInstanceRepository.findAll());
  }

  /**
   * Finds a connector instance by its ID.
   *
   * @param id the connector instance id to search for
   * @return the connector instance matching the ID
   * @throws EntityNotFoundException if no connector instance is found with the given ID
   */
  public ConnectorInstance connectorInstanceById(String id) throws EntityNotFoundException {
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
  public ConnectorInstance updateCurrentStatus(
      String connectorInstanceId, ConnectorInstance.CURRENT_STATUS_TYPE newCurrentStatus) {
    ConnectorInstance instance = this.connectorInstanceById(connectorInstanceId);
    instance.setCurrentStatus(newCurrentStatus);
    return this.save(instance);
  }

  /**
   * Update the requested status for a specific connector instance
   *
   * @param instance the connector instance to update
   * @param newRequestedStatus the new requested status to set
   * @return the connector instance updated
   */
  public ConnectorInstance updateRequestedStatus(
      ConnectorInstance instance, ConnectorInstance.REQUESTED_STATUS_TYPE newRequestedStatus) {
    instance.setRequestedStatus(newRequestedStatus);
    return this.save(instance);
  }

  /**
   * Saves a connector instance.
   *
   * @param connectorInstance the connector instance to save
   * @return the saved connector instance
   */
  public ConnectorInstance save(ConnectorInstance connectorInstance) {
    return connectorInstanceRepository.save(connectorInstance);
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
  public List<ConnectorInstance> findAllByCatalogConnector(CatalogConnector connector) {
    return connectorInstanceRepository.findAllByCatalogConnectorId(connector.getId());
  }

  /**
   * Saves a set of connector instances.
   *
   * @param instances the connector instances to save
   */
  public void saveAll(Set<ConnectorInstance> instances) {
    connectorInstanceRepository.saveAll(instances);
  }

  /**
   * Finds all connector instances by catalog connector ID.
   *
   * @param catalogId the catalog connector ID to search instances for
   * @return the list of connector instances for the given catalog connector ID
   */
  public List<ConnectorInstance> findAllByCatalogConnectorId(String catalogId) {
    return connectorInstanceRepository.findAllByCatalogConnectorId(catalogId);
  }

  private ConnectorInstance buildNewConnectorInstanceFromCatalog(
      CatalogConnector catalogConnector) {
    ConnectorInstance newInstance = new ConnectorInstance();
    newInstance.setCatalogConnector(catalogConnector);
    newInstance.setRequestedStatus(ConnectorInstance.REQUESTED_STATUS_TYPE.stopping);
    newInstance.setCurrentStatus(ConnectorInstance.CURRENT_STATUS_TYPE.stopped);
    newInstance.setSource(ConnectorInstance.SOURCE.CATALOG_DEPLOYMENT);
    return newInstance;
  }

  private ConnectorInstanceConfiguration createConfiguration(
      String key, JsonNode value, boolean isEncrypted, ConnectorInstance instance) {
    ConnectorInstanceConfiguration conf = new ConnectorInstanceConfiguration();
    conf.setKey(key);
    conf.setValue(value);
    conf.setEncrypted(isEncrypted);
    conf.setConnectorInstance(instance);
    return conf;
  }

  private ConnectorInstanceConfiguration createConfigurationFromInput(
      CreateConnectorInstanceInput.ConfigurationInput input,
      CatalogConnectorConfiguration definition,
      ConnectorInstance instance,
      EncryptionService encryptionService) {

    boolean isEncrypted =
        CatalogConnectorConfiguration.CONNECTOR_CONFIGURATION_FORMAT.PASSWORD.equals(
            definition.getConnectorConfigurationFormat());
    JsonNode value = input.getValue();

    if (isEncrypted) {
      try {
        value =
            objectMapper
                .getNodeFactory()
                .textNode(encryptionService.encrypt(input.getValue().asText()));
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    return createConfiguration(input.getKey(), value, isEncrypted, instance);
  }

  private List<ConnectorInstanceConfiguration> getConnectorInstanceConfigurationsFromInput(
      ConnectorInstance instance,
      CatalogConnector catalogConnector,
      CreateConnectorInstanceInput input) {
    List<ConnectorInstanceConfiguration> configurations = new ArrayList<>();
    Map<String, CatalogConnectorConfiguration> definitionsMap =
        catalogConnector.getCatalogConnectorConfigurations().stream()
            .collect(
                Collectors.toMap(
                    CatalogConnectorConfiguration::getConnectorConfigurationKey,
                    Function.identity()));
    EncryptionService encryptionService = encryptionFactory.getEncryptionService(instance);

    for (CreateConnectorInstanceInput.ConfigurationInput confInput : input.getConfigurations()) {
      CatalogConnectorConfiguration definition = definitionsMap.get(confInput.getKey());
      if (definition == null) {
        throw new IllegalArgumentException(
            String.format(
                "Configuration key '%s' not found in CatalogConnector configurations",
                confInput.getKey()));
      }
      ConnectorInstanceConfiguration config =
          createConfigurationFromInput(confInput, definition, instance, encryptionService);
      configurations.add(config);
    }

    return configurations;
  }

  private ConnectorInstanceConfiguration createTokenConfiguration(ConnectorInstance instance) {
    Token token =
        tokenRepository.findAll(fromUser(currentUser().getId())).stream()
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No token found for current user"));
    return createConfiguration(
        "OPENAEV_TOKEN", objectMapper.getNodeFactory().textNode(token.getValue()), false, instance);
  }

  private ConnectorInstanceConfiguration createContainerIdConfiguration(
      ConnectorInstance instance, CatalogConnector.CONNECTOR_TYPE type) {
    return createConfiguration(
        type.toString() + "_ID",
        objectMapper.getNodeFactory().textNode(UUID.randomUUID().toString()),
        false,
        instance);
  }

  /**
   * Creates a connector instance from a catalog connector.
   *
   * @param catalogConnector the catalog connector to create an instance from
   * @param input the input data for creating the connector instance
   * @return the created connector instance
   */
  public ConnectorInstance createConnectorInstance(
      CatalogConnector catalogConnector, CreateConnectorInstanceInput input) {
    ConnectorInstance newInstance = buildNewConnectorInstanceFromCatalog(catalogConnector);
    List<ConnectorInstanceConfiguration> configurations =
        getConnectorInstanceConfigurationsFromInput(newInstance, catalogConnector, input);

    // Add OpenAEV token
    configurations.add(createTokenConfiguration(newInstance));
    // Add container ID
    configurations.add(
        createContainerIdConfiguration(newInstance, catalogConnector.getContainerType()));

    newInstance.setConfigurations(Set.copyOf(configurations));
    return this.save(newInstance);
  }

  private List<ConnectorInstanceConfiguration> mergeConfigurations(
      ConnectorInstance instance,
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
   * @param catalogConnector the catalog connector linked to the instance
   * @param input the input data for updating the connector instance configurations
   * @return the list of connector instance configurations updated
   */
  public List<ConnectorInstanceConfiguration> updateConnectorInstanceConfigurations(
      String connectorInstanceId,
      CatalogConnector catalogConnector,
      CreateConnectorInstanceInput input) {
    ConnectorInstance instance = connectorInstanceById(connectorInstanceId);
    Map<String, ConnectorInstanceConfiguration> existingConfigurationMap =
        instance.getConfigurations().stream()
            .collect(Collectors.toMap(ConnectorInstanceConfiguration::getKey, Function.identity()));

    List<ConnectorInstanceConfiguration> newConfigurations =
        getConnectorInstanceConfigurationsFromInput(instance, catalogConnector, input);
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
  public ConnectorInstance patchConnectorInstanceHealthCheck(
      String connectorInstanceId, ConnectorInstanceHealthInput input) {
    ConnectorInstance instance = this.connectorInstanceById(connectorInstanceId);

    instance.setInRebootLoop(input.isInRebootLoop());
    instance.setStartedAt(input.getStartedAt());
    instance.setRestartCount(input.getRestartCount());

    return this.save(instance);
  }
}
