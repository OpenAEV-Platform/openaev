package io.openaev.integration.impl.collectors.expiration_manager;

import static io.openaev.collectors.expectations_expiration_manager.config.ExpectationsExpirationManagerConfig.COLLECTOR_ID;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.openaev.authorisation.HttpClientFactory;
import io.openaev.collectors.expectations_expiration_manager.config.ExpectationsExpirationManagerConfig;
import io.openaev.collectors.expectations_expiration_manager.service.ExpectationsExpirationManagerService;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorType;
import io.openaev.integration.BuiltinIntegrationFactory;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.Integration;
import io.openaev.rest.collector.service.CollectorService;
import io.openaev.scheduler.TenantScopedJobRunner;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

@Service
@Profile("!test")
public class ExpirationManagerIntegrationFactory extends BuiltinIntegrationFactory {
  private static final String FAKE_DETECTOR_COLLECTOR_TYPE = "openaev_fake_detector";
  private static final String FAKE_DETECTOR_COLLECTOR_NAME = "Expectations Expiration Manager";
  private final ThreadPoolTaskScheduler taskScheduler;
  private final CollectorService collectorService;
  private final ExpectationsExpirationManagerService fakeDetectorService;
  private final ComponentRequestEngine componentRequestEngine;
  private final ExpectationsExpirationManagerConfig expectationsExpirationManagerConfig;
  private final TenantScopedJobRunner tenantScopedJobRunner;

  public ExpirationManagerIntegrationFactory(
      ConnectorInstanceService connectorInstanceService,
      CatalogConnectorService catalogConnectorService,
      HttpClientFactory httpClientFactory,
      ThreadPoolTaskScheduler taskScheduler,
      ComponentRequestEngine componentRequestEngine,
      CollectorService collectorService,
      ExpectationsExpirationManagerService fakeDetectorService,
      ExpectationsExpirationManagerConfig expectationsExpirationManagerConfig,
      TenantScopedJobRunner tenantScopedJobRunner) {
    super(connectorInstanceService, catalogConnectorService, httpClientFactory);
    this.taskScheduler = taskScheduler;
    this.fakeDetectorService = fakeDetectorService;
    this.collectorService = collectorService;
    this.componentRequestEngine = componentRequestEngine;
    this.expectationsExpirationManagerConfig = expectationsExpirationManagerConfig;
    this.tenantScopedJobRunner = tenantScopedJobRunner;
  }

  @Override
  public void registerConnectorForTenant(String tenantId) throws Exception {
    collectorService.register(
        tenantId,
        expectationsExpirationManagerConfig.getId(),
        FAKE_DETECTOR_COLLECTOR_TYPE,
        FAKE_DETECTOR_COLLECTOR_NAME,
        false,
        0,
        null,
        getClass().getResourceAsStream("/img/icon-fake-detector.png"),
        null,
        null);
  }

  @Override
  protected void runMigrations(String tenantId) throws Exception {
    // noop
  }

  @Override
  protected void insertCatalogEntry() throws Exception {
    // noop
  }

  @Override
  protected String getClassName() {
    return ExpirationManagerIntegrationFactory.class.getCanonicalName();
  }

  @Override
  public List<ConnectorInstance> findRelatedInstances(String tenantId) {
    ConnectorInstance instance =
        connectorInstanceService.createAutostartInstance(
            COLLECTOR_ID, this.getClassName(), ConnectorType.COLLECTOR);

    // disable the integration in case the settings disable the associated functionality
    if (!expectationsExpirationManagerConfig.isEnable()) {
      instance.setCurrentStatus(ConnectorInstance.CURRENT_STATUS_TYPE.stopped);
      instance.setRequestedStatus(ConnectorInstance.REQUESTED_STATUS_TYPE.stopping);
    }

    return List.of(instance);
  }

  @Override
  public Integration spawn(ConnectorInstance instance, String tenantId)
      throws JsonProcessingException,
          InvocationTargetException,
          NoSuchMethodException,
          InstantiationException,
          IllegalAccessException {
    return new ExpirationManagerIntegration(
        componentRequestEngine,
        instance,
        connectorInstanceService,
        tenantId,
        expectationsExpirationManagerConfig,
        fakeDetectorService,
        taskScheduler,
        collectorService,
        tenantScopedJobRunner);
  }
}
