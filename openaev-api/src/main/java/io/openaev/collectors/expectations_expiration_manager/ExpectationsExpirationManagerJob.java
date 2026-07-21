package io.openaev.collectors.expectations_expiration_manager;

import io.openaev.collectors.expectations_expiration_manager.config.ExpectationsExpirationManagerConfig;
import io.openaev.collectors.expectations_expiration_manager.service.ExpectationsExpirationManagerService;
import io.openaev.context.TenantContext;
import io.openaev.database.repository.TenantRepository;
import io.openaev.integration.BuiltinTenantRegistrable;
import io.openaev.rest.collector.service.CollectorService;
import java.util.List;
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
  private final TenantRepository tenantRepository;

  @Autowired
  public ExpectationsExpirationManagerJob(
      CollectorService collectorService,
      ExpectationsExpirationManagerConfig config,
      ExpectationsExpirationManagerService fakeDetectorService,
      TenantRepository tenantRepository) {
    this.collectorService = collectorService;
    this.config = config;
    this.fakeDetectorService = fakeDetectorService;
    this.tenantRepository = tenantRepository;
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
    List<String> tenantIds = tenantRepository.findAllIdsByDeletedAtIsNull();
    for (String tenantId : tenantIds) {
      try {
        TenantContext.setCurrentTenant(tenantId);
        // Detection & Prevention
        this.fakeDetectorService.computeExpectations();
        // Heartbeat: surface the run as the collector's last execution so the UI
        // shows a truthful liveliness signal for this built-in collector.
        this.collectorService.updateLastExecution(config.getId(), tenantId);
      } catch (Exception e) {
        log.error("Error running expectations expiration manager for tenant {}", tenantId, e);
      } finally {
        TenantContext.clearCurrentTenant();
      }
    }
  }
}
