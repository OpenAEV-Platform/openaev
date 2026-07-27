package io.openaev.collectors.expectations_expiration_manager;

import io.openaev.collectors.expectations_expiration_manager.config.ExpectationsExpirationManagerConfig;
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
  private final ExpectationsExpirationManagerJob job;

  @PostConstruct
  public void init() {
    if (this.config.isEnable()) {
      this.taskScheduler.scheduleAtFixedRate(
          job,
          Instant.now().plus(30, ChronoUnit.SECONDS),
          Duration.ofSeconds(this.config.getInterval()));
    }
  }
}
