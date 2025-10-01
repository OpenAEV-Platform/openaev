package io.openaev.collectors.expectations_expiration_manager;

import io.openaev.collectors.expectations_expiration_manager.config.ExpectationsExpirationManagerConfig;
import io.openaev.collectors.expectations_expiration_manager.service.ExpectationsExpirationManagerService;
import io.openaev.rest.collector.service.CollectorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ExpectationsExpirationManagerJob implements Runnable {
  private static final String FAKE_DETECTOR_COLLECTOR_TYPE = "openaev_fake_detector";
  private static final String FAKE_DETECTOR_COLLECTOR_NAME = "Expectations Expiration Manager";
  private final ExpectationsExpirationManagerService fakeDetectorService;

  @Autowired
  public ExpectationsExpirationManagerJob(
      CollectorService collectorService,
      ExpectationsExpirationManagerConfig config,
      ExpectationsExpirationManagerService fakeDetectorService) {
    this.fakeDetectorService = fakeDetectorService;
    try {
      collectorService.register(
          config.getId(),
          FAKE_DETECTOR_COLLECTOR_TYPE,
          FAKE_DETECTOR_COLLECTOR_NAME,
          getClass().getResourceAsStream("/img/icon-fake-detector.png"));
    } catch (Exception e) {
      log.error("Error creating expectations expiration manager ", e);
    }
  }

  @Override
  public void run() {
    // Detection & Prevention
    try {
      this.fakeDetectorService.computeExpectations();
    } catch (Exception e) {
      log.error("Error running expectations expiration manager service", e);
    }
  }
}
