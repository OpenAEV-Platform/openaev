package io.openaev.integration.impl.collectors.expiration_manager;

import io.openaev.collectors.expectations_expiration_manager.config.ExpectationsExpirationManagerConfig;
import io.openaev.collectors.expectations_expiration_manager.service.ExpectationsExpirationManagerService;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.Integration;
import io.openaev.rest.collector.service.CollectorService;
import io.openaev.scheduler.TenantScopedJobRunner;
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
  private final TenantScopedJobRunner tenantScopedJobRunner;
  private final List<ScheduledFuture<?>> timers = new ArrayList<>();

  protected ExpirationManagerIntegration(
      ComponentRequestEngine componentRequestEngine,
      ConnectorInstance connectorInstance,
      ConnectorInstanceService connectorInstanceService,
      String tenantId,
      ExpectationsExpirationManagerConfig expectationsExpirationManagerConfig,
      ExpectationsExpirationManagerService expectationsExpirationManagerService,
      ThreadPoolTaskScheduler taskScheduler,
      CollectorService collectorService,
      TenantScopedJobRunner tenantScopedJobRunner) {
    super(componentRequestEngine, connectorInstance, connectorInstanceService, tenantId);
    this.expectationsExpirationManagerConfig = expectationsExpirationManagerConfig;
    this.expectationsExpirationManagerService = expectationsExpirationManagerService;
    this.taskScheduler = taskScheduler;
    this.collectorService = collectorService;
    this.tenantScopedJobRunner = tenantScopedJobRunner;
  }

  @Override
  protected void innerStart() throws Exception {
    this.timers.add(
        taskScheduler.scheduleAtFixedRate(
            () ->
                tenantScopedJobRunner.runInTenant(
                    this.getTenantId(),
                    () ->
                        expectationsExpirationManagerService.computeExpectations(
                            this.getTenantId())),
            Duration.ofSeconds(this.expectationsExpirationManagerConfig.getInterval())));
    this.timers.add(
        taskScheduler.scheduleAtFixedRate(
            () ->
                tenantScopedJobRunner.runInTenant(
                    this.getTenantId(),
                    () ->
                        collectorService.updateLastExecution(
                            expectationsExpirationManagerConfig.getId(), this.getTenantId())),
            Duration.ofSeconds(this.expectationsExpirationManagerConfig.getInterval())));
  }

  @Override
  protected void refresh() throws Exception {
    // Nothing to refresh from DB
  }

  @Override
  protected void innerStop() {
    this.timers.forEach(timer -> timer.cancel(true));
    this.timers.clear();
  }
}
