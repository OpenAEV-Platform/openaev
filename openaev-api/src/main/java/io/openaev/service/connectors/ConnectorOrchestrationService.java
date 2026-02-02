package io.openaev.service.connectors;

import io.openaev.api.xtm_composer.dto.XtmComposerInstanceOutput;
import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.*;
import io.openaev.ee.Ee;
import io.openaev.executors.ExecutorService;
import io.openaev.rest.collector.service.CollectorService;
import io.openaev.rest.connector_instance.dto.ConnectorInstanceHealthInput;
import io.openaev.rest.connector_instance.dto.CreateConnectorInstanceInput;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exception.LicenseRestrictionException;
import io.openaev.service.InjectorService;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import io.openaev.service.connector_instances.ConnectorInstanceLogService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ConnectorOrchestrationService {
  private final ConnectorInstanceService connectorInstanceService;
  private final XtmComposerService xtmComposerService;
  private final Ee eeService;
  private final CatalogConnectorService catalogConnectorService;

  private final CollectorService collectorService;
  private final InjectorService injectorService;
  private final ExecutorService executorService;
  private final ConnectorInstanceLogService connectorInstanceLogService;

  private final LicenseCacheManager licenseCacheManager;

  /**
   * Find connector instances managed by xtm Composer
   *
   * @param xtmComposerId XTM Composer id
   * @return List of connector instances
   */
  public List<XtmComposerInstanceOutput> findConnectorInstancesManagedByComposer(
      String xtmComposerId) {
    this.xtmComposerService.throwIfInvalidXtmComposerId(xtmComposerId);

    List<ConnectorInstancePersisted> instances =
        connectorInstanceService.connectorInstancesManagedByXtmComposer();

    return instances.stream().map(xtmComposerService::toXtmComposerInstanceOutput).toList();
  }

  /**
   * Update connector instance status, called by XTM Composer
   *
   * @param xtmComposerId XTM Composer id
   * @param connectorInstanceId Connector Instance id
   * @param newCurrentStatus New current status
   * @return Updated connector instance formatted for XTM Composer
   */
  public XtmComposerInstanceOutput updateConnectorInstanceStatus(
      String xtmComposerId,
      String connectorInstanceId,
      ConnectorInstance.CURRENT_STATUS_TYPE newCurrentStatus) {
    this.xtmComposerService.throwIfInvalidXtmComposerId(xtmComposerId);

    ConnectorInstancePersisted instances =
        connectorInstanceService.updateCurrentStatus(connectorInstanceId, newCurrentStatus);

    return xtmComposerService.toXtmComposerInstanceOutput(instances);
  }

  private void throwIfEnterpriseLicenseNotActive() throws LicenseRestrictionException {
    if (!eeService.isLicenseActive(licenseCacheManager.getEnterpriseEditionInfo())) {
      throw new LicenseRestrictionException("Manage instance is enterprise edition");
    }
  }

  private void throwIfXtmComposerDownAndNeeded(CatalogConnector catalogConnector)
      throws BadRequestException {
    if (catalogConnector.isManagerSupported()) {
      this.xtmComposerService.throwIfXtmComposerNotReachable();
    }
  }

  /**
   * Updates the requested status of a connector instance. Validates license and XTM Composer
   * connectivity if required.
   *
   * @param connectorInstanceId the identifier of the connector instance to update
   * @param requestedStatus the new requested status to set
   * @return the updated connector instance
   */
  public ConnectorInstancePersisted updateRequestedStatus(
      String connectorInstanceId, ConnectorInstance.REQUESTED_STATUS_TYPE requestedStatus) {
    if (requestedStatus.equals(ConnectorInstance.REQUESTED_STATUS_TYPE.starting)) {
      throwIfEnterpriseLicenseNotActive();
    }

    ConnectorInstancePersisted instance =
        connectorInstanceService.connectorInstanceById(connectorInstanceId);
    throwIfXtmComposerDownAndNeeded(instance.getCatalogConnector());

    return connectorInstanceService.updateRequestedStatus(instance, requestedStatus);
  }

  private void throwIfConnectorInstanceAlreadyExist(String catalogId)
      throws DataIntegrityViolationException {
    List<ConnectorInstancePersisted> existingInstances =
        connectorInstanceService.findAllByCatalogConnectorId(catalogId);
    if (!existingInstances.isEmpty()) {
      throw new DataIntegrityViolationException(
          "ConnectorInstance with CatalogConnector id " + catalogId + " already exists");
    }
  }

  private void throwIfConnectorAlreadyExist(
      String catalogConnectorSlug, ConnectorType catalogConnectorType)
      throws DataIntegrityViolationException {
    BaseConnectorEntity connector;
    if (ConnectorType.COLLECTOR.equals(catalogConnectorType)) {
      connector = collectorService.findCollectorByType(catalogConnectorSlug).orElse(null);
    } else if (ConnectorType.INJECTOR.equals(catalogConnectorType)) {
      connector = injectorService.injectorByType(catalogConnectorSlug).orElse(null);
    } else {
      connector = executorService.executorByType(catalogConnectorSlug).orElse(null);
    }
    if (connector != null) {
      throw new DataIntegrityViolationException(
          "Connector with slug " + catalogConnectorSlug + " already exists");
    }
  }

  private void throwIfInstanceOrConnectorAlreadyExist(
      String catalogConnectorId, String catalogConnectorSlug, ConnectorType catalogConnectorType)
      throws DataIntegrityViolationException {
    throwIfConnectorInstanceAlreadyExist(catalogConnectorId);
    throwIfConnectorAlreadyExist(catalogConnectorSlug, catalogConnectorType);
  }

  private void cleanDummyInjectorsIfItExists(
      String catalogConnectorSlug, ConnectorType catalogConnectorType) {
    if (ConnectorType.INJECTOR.equals(catalogConnectorType)) {
      injectorService.deleteDummyInjectorIfItExists(catalogConnectorSlug);
    }
  }

  /**
   * Holds a CatalogConnector and its configurations mapped by key.
   *
   * @param catalogConnector the catalog connector
   * @param configurationsMap the configurations mapped by their key
   */
  public record CatalogConnectorWithConfigMap(
      CatalogConnector catalogConnector,
      Map<String, CatalogConnectorConfiguration> configurationsMap) {}

  /**
   * Retrieves a CatalogConnector with its configurations mapped by key.
   *
   * @param catalogConnectorId the catalog connector ID to search for
   * @return the catalog connector with its configurations map
   * @throws EntityNotFoundException if no catalog connector is found with the given ID
   */
  public CatalogConnectorWithConfigMap getCatalogConnectorWithConfigurationsMap(
      String catalogConnectorId) throws EntityNotFoundException {
    CatalogConnector catalogConnector =
        catalogConnectorService
            .findById(catalogConnectorId)
            .orElseThrow(
                () ->
                    new EntityNotFoundException(
                        "CatalogConnector with id " + catalogConnectorId + " not found"));

    Map<String, CatalogConnectorConfiguration> configurationsMap =
        catalogConnector.getCatalogConnectorConfigurations().stream()
            .collect(
                Collectors.toMap(
                    CatalogConnectorConfiguration::getConnectorConfigurationKey,
                    Function.identity()));

    return new CatalogConnectorWithConfigMap(catalogConnector, configurationsMap);
  }

  /**
   * Create connector instance. Validates license and XTM Composer connectivity if required.
   *
   * @param catalogConnectorWithConfigMap the catalog connector with its configurations map
   * @param input CreateConnectorInstanceInput
   * @return Created ConnectorInstance
   */
  public ConnectorInstancePersisted createConnectorInstance(
      CatalogConnectorWithConfigMap catalogConnectorWithConfigMap,
      CreateConnectorInstanceInput input) {
    throwIfEnterpriseLicenseNotActive();

    throwIfXtmComposerDownAndNeeded(catalogConnectorWithConfigMap.catalogConnector);
    throwIfInstanceOrConnectorAlreadyExist(
        catalogConnectorWithConfigMap.catalogConnector.getId(),
        catalogConnectorWithConfigMap.catalogConnector.getSlug(),
        catalogConnectorWithConfigMap.catalogConnector.getContainerType());

    ConnectorInstancePersisted connectorInstance =
        connectorInstanceService.createConnectorInstance(catalogConnectorWithConfigMap, input);

    cleanDummyInjectorsIfItExists(
        catalogConnectorWithConfigMap.catalogConnector.getSlug(),
        catalogConnectorWithConfigMap.catalogConnector.getContainerType());

    return connectorInstance;
  }

  /**
   * Update connector instance configurations
   *
   * @param catalogConnectorWithConfigMap the catalog connector with its configurations map
   * @param connectorInstanceId the identifier of the connector instance to update
   * @param input CreateConnectorInstanceInput
   * @return list of connector instance configuration updated
   */
  public List<ConnectorInstanceConfiguration> updateConnectorInstanceConfiguration(
      CatalogConnectorWithConfigMap catalogConnectorWithConfigMap,
      String connectorInstanceId,
      CreateConnectorInstanceInput input) {
    throwIfEnterpriseLicenseNotActive();
    throwIfXtmComposerDownAndNeeded(catalogConnectorWithConfigMap.catalogConnector);

    return connectorInstanceService.updateConnectorInstanceConfigurations(
        connectorInstanceId, catalogConnectorWithConfigMap.configurationsMap, input);
  }

  /**
   * Pushes log entries to a specific connector instance after validating the XTM composer.
   *
   * @param xtmComposerId the unique identifier of the XTM composer to validate
   * @param connectorInstanceId the unique identifier of the connector instance to receive the logs
   * @param logs a set of log messages to be pushed to the connector instance
   * @return the updated ConnectorInstanceLog
   */
  public ConnectorInstanceLog pushLogsByConnectorInstance(
      String xtmComposerId, String connectorInstanceId, Set<String> logs) {
    this.xtmComposerService.throwIfInvalidXtmComposerId(xtmComposerId);
    if (logs.isEmpty()) {
      return null;
    }
    ConnectorInstancePersisted instance =
        connectorInstanceService.connectorInstanceById(connectorInstanceId);
    return connectorInstanceLogService.pushLogByConnectorInstance(
        instance, connectorInstanceLogService.transformRawLogsLineToLog(logs));
  }

  /**
   * Updates the health check status of a specific connector instance after validating the XTM
   * composer.
   *
   * @param xtmComposerId the unique identifier of the XTM composer to validate
   * @param connectorInstanceId the unique identifier of the connector instance to update
   * @param input the health check input data containing the new health status and related
   *     information
   * @return the updated ConnectorInstance formatted for XTM Composer
   */
  public XtmComposerInstanceOutput patchConnectorInstanceHealthCheck(
      String xtmComposerId, String connectorInstanceId, ConnectorInstanceHealthInput input) {
    this.xtmComposerService.throwIfInvalidXtmComposerId(xtmComposerId);
    ConnectorInstancePersisted instances =
        connectorInstanceService.patchConnectorInstanceHealthCheck(connectorInstanceId, input);
    return xtmComposerService.toXtmComposerInstanceOutput(instances);
  }
}
