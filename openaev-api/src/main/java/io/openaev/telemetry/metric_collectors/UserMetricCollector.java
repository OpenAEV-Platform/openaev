package io.openaev.telemetry.metric_collectors;

import io.openaev.service.user_events.UserEventService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserMetricCollector {

  private final MetricRegistry metricRegistry;
  private final UserEventService userEventService;

  @PostConstruct
  public void init() {
    metricRegistry.registerGauge(
        "avg_logins_per_day",
        "Average daily logins for OAEV users",
        () -> this.userEventService.averageDailyLogins(1));
  }
}
