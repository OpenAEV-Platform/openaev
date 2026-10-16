package io.openaev.collectors.expectations_expiration_manager.service;

import io.openaev.collectors.expectations_expiration_manager.config.ExpectationsExpirationManagerConfig;
import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.Collector;
import io.openaev.rest.collector.service.CollectorService;
import io.openaev.service.InjectExpectationService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
@Slf4j
public class ExpectationsExpirationManagerService {

  private final InjectExpectationService injectExpectationService;
  private final ExpectationsExpirationManagerConfig config;
  private final CollectorService collectorService;

  public static final String EXPIRED = "Expired";

  private static final int BATCH_SIZE = 1000;

  @Transactional(rollbackFor = Exception.class)
  public void computeExpectations(String tenantId) {
    Collector collector = this.collectorService.collector(config.getId());
    List<BaseInjectExpectation> expectations =
        this.injectExpectationService.expectationsNotFillAndExpired(tenantId, BATCH_SIZE);
    log.debug(
        "Found {} pending expired expectations for tenant {} (expirationTime={}s, assetExpirationTime={}s)",
        expectations.size(),
        tenantId,
        config.getExpirationTime(),
        config.getExpirationTimeForAsset());
    int concluded = this.injectExpectationService.expireExpectations(expectations, collector);
    if (!expectations.isEmpty()) {
      log.debug("Concluded {} expectations for tenant {}", concluded, tenantId);
    }
  }
}
