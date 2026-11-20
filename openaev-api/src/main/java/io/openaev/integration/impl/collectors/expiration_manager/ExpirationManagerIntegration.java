package io.openaev.integration.impl.collectors.expiration_manager;

import io.openaev.collectors.expectations_expiration_manager.config.ExpectationsExpirationManagerConfig;
import io.openaev.collectors.expectations_expiration_manager.service.ExpectationsExpirationManagerService;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.Integration;
import io.openaev.integration.impl.collectors.expiration_manager.scheduler.ExpectationsExpirationManagerServiceRunner;
import io.openaev.rest.collector.service.CollectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

public class ExpirationManagerIntegration extends Integration {
  private final ExpectationsExpirationManagerConfig expectationsExpirationManagerConfig;
  private final ExpectationsExpirationManagerService expectationsExpirationManagerService;

  private final ThreadPoolTaskScheduler taskScheduler;
  private final CollectorService collectorService;
  private final List<ScheduledFuture<?>> timers = new ArrayList<>();

  protected ExpirationManagerIntegration(
      ComponentRequestEngine componentRequestEngine,
      ConnectorInstance connectorInstance,
      ConnectorInstanceService connectorInstanceService,
      String tenantId,
      ExpectationsExpirationManagerConfig expectationsExpirationManagerConfig,
      ExpectationsExpirationManagerService expectationsExpirationManagerService,
      ThreadPoolTaskScheduler taskScheduler,
      CollectorService collectorService) {
    super(componentRequestEngine, connectorInstance, connectorInstanceService, tenantId);
    this.expectationsExpirationManagerConfig = expectationsExpirationManagerConfig;
    this.expectationsExpirationManagerService = expectationsExpirationManagerService;
    this.taskScheduler = taskScheduler;
    this.collectorService = collectorService;
  }

  @Override
  protected void innerStart() throws Exception {
    this.timers.add(
        taskScheduler.scheduleAtFixedRate(
            ExpectationsExpirationManagerServiceRunner.runnerForTenant(
                this.getTenantId(), expectationsExpirationManagerService),
            Duration.ofSeconds(this.expectationsExpirationManagerConfig.getInterval())));
    //        this.timers.add(taskScheduler.scheduleAtFixedRate(
    //                ExpectationsExpirationManagerServiceRunner.runnerForTenant(
    //                        getTenantId(), expectationsExpirationManagerService),
    //                Duration.ofSeconds(this.expectationsExpirationManagerConfig.getInterval())));
  }

  @Override
  protected void refresh() throws Exception {
    // Nothing to refresh from DB
  }

  @Override
  protected void innerStop() {
    // it is not possible to stop this integration
  }
}
