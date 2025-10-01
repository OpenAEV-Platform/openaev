package io.openaev.collectors.expectations_expiration_manager;

import io.openaev.collectors.expectations_expiration_manager.config.ExpectationsExpirationManagerConfig;
import io.openaev.collectors.expectations_expiration_manager.service.ExpectationsExpirationManagerService;
import io.openaev.rest.collector.service.CollectorService;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class ExpectationsExpirationManagerCollector {

  private final ExpectationsExpirationManagerConfig config;
  private final ThreadPoolTaskScheduler taskScheduler;
  private final ExpectationsExpirationManagerService fakeDetectorService;
  private final CollectorService collectorService;

  @PostConstruct
  public void init() {
    if (this.config.isEnable()) {
      ExpectationsExpirationManagerJob job =
          new ExpectationsExpirationManagerJob(
              this.collectorService, this.config, this.fakeDetectorService);
      this.taskScheduler.scheduleAtFixedRate(job, Duration.ofSeconds(this.config.getInterval()));
    }
  }
}
