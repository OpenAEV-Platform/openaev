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

  public List<ConnectorInstance> connectorInstancesManagedByXtmComposer() {
    return connectorInstanceRepository.findAllManagedByXtmComposerAndConfiguration();
  }

  public List<ConnectorInstance> injectorConnectorInstances() {
    return connectorInstanceRepository.findAllByCatalogConnectorContainerType(
        CatalogConnector.CONNECTOR_TYPE.INJECTOR);
  }

  public List<ConnectorInstance> collectorConnectorInstances() {
    return connectorInstanceRepository.findAllByCatalogConnectorContainerType(
        CatalogConnector.CONNECTOR_TYPE.COLLECTOR);
  }

  public List<ConnectorInstance> executorConnectorInstances() {
    return connectorInstanceRepository.findAllByCatalogConnectorContainerType(
        CatalogConnector.CONNECTOR_TYPE.EXECUTOR);
  }

  public List<ConnectorInstance> connectorInstances() {
    return fromIterable(connectorInstanceRepository.findAll());
  }

  public ConnectorInstance connectorInstanceById(String id) {
    return connectorInstanceRepository
        .findById(id)
        .orElseThrow(
            () -> new EntityNotFoundException("ConnectorInstance with id " + id + " not found"));
  }

  public ConnectorInstanceOutput connectorInstanceOutputById(String id) {
    return connectorInstanceMapper.toConnectorInstanceOutput(connectorInstanceById(id));
  }

  public Set<ConnectorInstanceConfiguration> getConnectorInstanceConfigurations(String id) {
    ConnectorInstance connectorInstance = connectorInstanceById(id);
    return connectorInstance.getConfigurations();
  }

  public ConnectorInstance updateCurrentStatus(
      String connectorInstanceId, ConnectorInstance.CURRENT_STATUS_TYPE newCurrentStatus) {
    ConnectorInstance instance = this.connectorInstanceById(connectorInstanceId);
    instance.setCurrentStatus(newCurrentStatus);
    return this.save(instance);
  }

  public ConnectorInstance updateRequestedStatus(
      ConnectorInstance instance, ConnectorInstance.REQUESTED_STATUS_TYPE newRequestedStatus) {
    instance.setRequestedStatus(newRequestedStatus);
    return this.save(instance);
  }

  public ConnectorInstance save(ConnectorInstance connectorInstance) {
    return connectorInstanceRepository.save(connectorInstance);
  }

  public void deleteById(String id) {
    connectorInstanceRepository.deleteById(id);
  }

  public List<ConnectorInstance> findAllByCatalogConnector(CatalogConnector connector) {
    return connectorInstanceRepository.findAllByCatalogConnectorId(connector.getId());
  }

  public void saveAll(Set<ConnectorInstance> instances) {
    connectorInstanceRepository.saveAll(instances);
  }

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

  public List<ConnectorInstanceConfiguration> updateConnectorInstanceConfiguration(
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

  public ConnectorInstance patchConnectorInstanceHealthCheck(
      String connectorInstanceId, ConnectorInstanceHealthInput input) {
    ConnectorInstance instance = this.connectorInstanceById(connectorInstanceId);

    instance.setInRebootLoop(input.isInRebootLoop());
    instance.setStartedAt(input.getStartedAt());
    instance.setRestartCount(input.getRestartCount());

    return this.save(instance);
  }
}
