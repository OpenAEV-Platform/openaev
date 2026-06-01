package io.openaev.collectors.expectations_expiration_manager;

import io.openaev.collectors.expectations_expiration_manager.config.ExpectationsExpirationManagerConfig;
import io.openaev.collectors.expectations_expiration_manager.service.ExpectationsExpirationManagerService;
import io.openaev.context.TenantContext;
import io.openaev.integration.BuiltinTenantRegistrable;
import io.openaev.rest.collector.service.CollectorService;
import io.openaev.service.tenants.TenantService;
import io.openaev.utils.DeterministicIdUtils;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ExpectationsExpirationManagerJob implements Runnable, BuiltinTenantRegistrable {
  public static final String FAKE_DETECTOR_COLLECTOR_TYPE = "openaev_fake_detector";
  private static final String FAKE_DETECTOR_COLLECTOR_NAME = "Expectations Expiration Manager";
  private final ExpectationsExpirationManagerService fakeDetectorService;
  private final CollectorService collectorService;
  private final ExpectationsExpirationManagerConfig config;
  private final TenantService tenantService;

  @Autowired
  public ExpectationsExpirationManagerJob(
      CollectorService collectorService,
      ExpectationsExpirationManagerConfig config,
      ExpectationsExpirationManagerService fakeDetectorService,
      @Lazy TenantService tenantService) {
    this.collectorService = collectorService;
    this.config = config;
    this.fakeDetectorService = fakeDetectorService;
    this.tenantService = tenantService;
  }

  @Override
  public void registerForTenant(String tenantId) throws Exception {
    collectorService.register(
        DeterministicIdUtils.resolveConnectorId(config.getId(), tenantId),
        FAKE_DETECTOR_COLLECTOR_TYPE,
        FAKE_DETECTOR_COLLECTOR_NAME,
        false,
        0,
        null,
        getClass().getResourceAsStream("/img/icon-fake-detector.png"),
        tenantId);
  }

  @Override
  public void unregisterForTenant(String tenantId) {
    collectorService.deleteCollector(
        DeterministicIdUtils.resolveConnectorId(config.getId(), tenantId), tenantId);
  }

  @Override
  public void run() {
    List<String> tenantIds = tenantService.findActiveTenantIds();
    for (String tenantId : tenantIds) {
      try {
        TenantContext.setCurrentTenant(tenantId);
        this.fakeDetectorService.computeExpectations(tenantId);
      } catch (Exception e) {
        log.error("Error running expectations expiration manager for tenant {}", tenantId, e);
      } finally {
        TenantContext.clearCurrentTenant();
      }
    }
  }
}
