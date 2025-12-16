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
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ConnectorOrchestrationService {
  private final String openAEVPrefix = "openaev_";

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

    List<ConnectorInstance> instances =
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

    ConnectorInstance instances =
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
  public ConnectorInstance updateRequestedStatus(
      String connectorInstanceId, ConnectorInstance.REQUESTED_STATUS_TYPE requestedStatus) {
    throwIfEnterpriseLicenseNotActive();

    ConnectorInstance instance =
        connectorInstanceService.connectorInstanceById(connectorInstanceId);
    throwIfXtmComposerDownAndNeeded(instance.getCatalogConnector());

    return connectorInstanceService.updateRequestedStatus(instance, requestedStatus);
  }

  private void throwIfConnectorInstanceAlreadyExist(String catalogId) {
    List<ConnectorInstance> existingInstances =
        connectorInstanceService.findAllByCatalogConnectorId(catalogId);
    if (!existingInstances.isEmpty()) {
      throw new IllegalArgumentException(
          "ConnectorInstance with CatalogConnector id " + catalogId + " already exists");
    }
  }

  private void throwIfConnectorAlreadyExist(
      String catalogConnectorSlug, CatalogConnector.CONNECTOR_TYPE catalogConnectorType)
      throws IllegalArgumentException {
    BaseConnectorEntity connector;
    if (CatalogConnector.CONNECTOR_TYPE.COLLECTOR.equals(catalogConnectorType)) {
      connector =
          collectorService.findCollectorByType(openAEVPrefix + catalogConnectorSlug).orElse(null);
    } else if (CatalogConnector.CONNECTOR_TYPE.INJECTOR.equals(catalogConnectorType)) {
      connector = injectorService.injectorByType(openAEVPrefix + catalogConnectorSlug).orElse(null);
    } else {
      connector = executorService.executorByType(openAEVPrefix + catalogConnectorSlug).orElse(null);
    }
    if (connector != null) {
      throw new IllegalArgumentException(
          "Connector with slug " + catalogConnectorSlug + " already exists");
    }
  }

  private void throwIfInstanceOrConnectorAlreadyExist(
      String catalogConnectorId,
      String catalogConnectorSlug,
      CatalogConnector.CONNECTOR_TYPE catalogConnectorType)
      throws IllegalArgumentException {
    throwIfConnectorInstanceAlreadyExist(catalogConnectorId);
    throwIfConnectorAlreadyExist(catalogConnectorSlug, catalogConnectorType);
  }

  /**
   * Create connector instance. Validates license and XTM Composer connectivity if required.
   *
   * @param input CreateConnectorInstanceInput
   * @return Created ConnectorInstance
   */
  public ConnectorInstance createConnectorInstance(CreateConnectorInstanceInput input) {
    throwIfEnterpriseLicenseNotActive();

    Optional<CatalogConnector> catalogConnector =
        catalogConnectorService.findById(input.getCatalogConnectorId());
    if (catalogConnector.isEmpty()) {
      throw new EntityNotFoundException(
          "CatalogConnector with id " + input.getCatalogConnectorId() + " not found");
    }
    throwIfXtmComposerDownAndNeeded(catalogConnector.get());
    throwIfInstanceOrConnectorAlreadyExist(
        catalogConnector.get().getId(),
        catalogConnector.get().getSlug(),
        catalogConnector.get().getContainerType());

    return connectorInstanceService.createConnectorInstance(catalogConnector.get(), input);
  }

  /**
   * Update connector instance configurations
   *
   * @param connectorInstanceId the identifier of the connector instance to update
   * @param input CreateConnectorInstanceInput
   * @return list of connector instance configuration updated
   */
  public List<ConnectorInstanceConfiguration> updateConnectorInstanceConfiguration(
      String connectorInstanceId, CreateConnectorInstanceInput input) {
    throwIfEnterpriseLicenseNotActive();

    Optional<CatalogConnector> catalogConnector =
        catalogConnectorService.findById(input.getCatalogConnectorId());
    if (catalogConnector.isEmpty()) {
      throw new EntityNotFoundException(
          "CatalogConnector with id " + input.getCatalogConnectorId() + " not found");
    }
    throwIfXtmComposerDownAndNeeded(catalogConnector.get());

    return connectorInstanceService.updateConnectorInstanceConfigurations(
        connectorInstanceId, catalogConnector.get(), input);
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
    ConnectorInstance instance =
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
   * @return the updated ConnectorInstance
   */
  public ConnectorInstance patchConnectorInstanceHealthCheck(
      String xtmComposerId, String connectorInstanceId, ConnectorInstanceHealthInput input) {
    this.xtmComposerService.throwIfInvalidXtmComposerId(xtmComposerId);
    return connectorInstanceService.patchConnectorInstanceHealthCheck(connectorInstanceId, input);
  }
}
