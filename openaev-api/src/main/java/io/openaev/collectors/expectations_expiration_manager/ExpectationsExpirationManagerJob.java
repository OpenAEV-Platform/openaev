package io.openaev.collectors.expectations_expiration_manager;

import io.openaev.collectors.expectations_expiration_manager.config.ExpectationsExpirationManagerConfig;
import io.openaev.collectors.expectations_expiration_manager.service.ExpectationsExpirationManagerService;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.integration.BuiltinTenantRegistrable;
import io.openaev.rest.collector.service.CollectorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ExpectationsExpirationManagerJob implements Runnable, BuiltinTenantRegistrable {
  public static final String EXPECTATIONS_EXPIRATION_MANAGER_TYPE = "openaev_expiration_manager";
  private static final String EXPECTATIONS_EXPIRATION_MANAGER_NAME =
      "Expectations Expiration Manager";
  private final ExpectationsExpirationManagerService fakeDetectorService;
  private final CollectorService collectorService;
  private final ExpectationsExpirationManagerConfig config;
  private final TenantScopedTransaction tenantTx;

  @Autowired
  public ExpectationsExpirationManagerJob(
      CollectorService collectorService,
      ExpectationsExpirationManagerConfig config,
      ExpectationsExpirationManagerService fakeDetectorService,
      TenantScopedTransaction tenantTx) {
    this.collectorService = collectorService;
    this.config = config;
    this.fakeDetectorService = fakeDetectorService;
    this.tenantTx = tenantTx;
  }

  @Override
  public void registerForTenant(String tenantId) throws Exception {
    collectorService.register(
        tenantId,
        config.getId(),
        EXPECTATIONS_EXPIRATION_MANAGER_TYPE,
        EXPECTATIONS_EXPIRATION_MANAGER_NAME,
        false,
        0,
        null,
        getClass().getResourceAsStream("/img/icon-fake-detector.png"),
        null);
  }

  @Override
  public void run() {
    log.debug("ExpectationsExpirationManagerJob starting (interval={}s)", config.getInterval());
    tenantTx.forEachTenant(
        tenantId -> {
          // Bridge: set TenantContext so that the v1 Hibernate @Filter (enabled by
          // HibernateFilterTransactionAspect) keeps working for tables not yet on v2.
          // Once all tables touched by this job are activated on v2, remove this line.
          TenantContext.setCurrentTenant(tenantId);
          try {
            log.debug("Processing expectations expiration for tenant {}", tenantId);
            // Detection & Prevention expectation expiration
            this.fakeDetectorService.computeExpectations(tenantId);
            // Heartbeat: surface the run as the collector's last execution so the UI
            // shows a truthful liveliness signal for this built-in collector.
            this.collectorService.updateLastExecution(config.getId(), tenantId);
            log.debug("Finished expectations expiration for tenant {}", tenantId);
          } finally {
            TenantContext.clearCurrentTenant();
          }
        });
    log.debug("ExpectationsExpirationManagerJob completed");
  }
}
