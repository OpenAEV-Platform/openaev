package io.openaev.collectors.expectations_expiration_manager;

import io.openaev.collectors.expectations_expiration_manager.config.ExpectationsExpirationManagerConfig;
import io.openaev.collectors.expectations_expiration_manager.service.ExpectationsExpirationManagerService;
import io.openaev.database.repository.TenantRepository;
import io.openaev.rest.collector.service.CollectorService;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class ExpectationsExpirationManagerCollector {

  private final ExpectationsExpirationManagerConfig config;
  private final ThreadPoolTaskScheduler taskScheduler;
  private final ExpectationsExpirationManagerService expectationsExpirationManagerService;
  private final CollectorService collectorService;
  private final TenantRepository tenantRepository;

  @PostConstruct
  public void init() {
    if (this.config.isEnable()) {
      ExpectationsExpirationManagerJob job =
          new ExpectationsExpirationManagerJob(
              this.collectorService,
              this.config,
              this.expectationsExpirationManagerService,
              this.tenantRepository);
      this.taskScheduler.scheduleAtFixedRate(
          job,
          Instant.now().plus(30, ChronoUnit.SECONDS),
          Duration.ofSeconds(this.config.getInterval()));
    }
  }
}
