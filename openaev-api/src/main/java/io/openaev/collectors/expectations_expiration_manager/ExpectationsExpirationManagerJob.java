package io.openaev.collectors.expectations_expiration_manager;

import io.openaev.collectors.expectations_expiration_manager.config.ExpectationsExpirationManagerConfig;
import io.openaev.collectors.expectations_expiration_manager.service.ExpectationsExpirationManagerService;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.integration.BuiltinTenantRegistrable;
import io.openaev.rest.collector.service.CollectorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ExpectationsExpirationManagerJob implements Runnable, BuiltinTenantRegistrable {
  private static final String FAKE_DETECTOR_COLLECTOR_TYPE = "openaev_fake_detector";
  private static final String FAKE_DETECTOR_COLLECTOR_NAME = "Expectations Expiration Manager";
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
        FAKE_DETECTOR_COLLECTOR_TYPE,
        FAKE_DETECTOR_COLLECTOR_NAME,
        false,
        0,
        null,
        getClass().getResourceAsStream("/img/icon-fake-detector.png"));
  }

  @Override
  public void run() {
    tenantTx.forEachTenant(
        scope -> {
          // Bridge: set TenantContext so that the v1 Hibernate @Filter (enabled by
          // HibernateFilterTransactionAspect) keeps working for tables not yet on v2.
          // Once all tables touched by this job are activated on v2, remove this line.
          TenantContext.setCurrentTenant(((TxCtx.Restricted) scope).tenantIds().getFirst());
          try {
            // Detection & Prevention expectation expiration
            this.fakeDetectorService.computeExpectations();
          } finally {
            TenantContext.clearCurrentTenant();
          }
        });
  }
}
