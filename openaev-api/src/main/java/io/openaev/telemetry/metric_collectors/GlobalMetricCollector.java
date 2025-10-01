package io.openaev.telemetry.metric_collectors;

import io.openaev.ee.Ee;
import io.openaev.service.UserService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GlobalMetricCollector {
  private final MetricRegistry metricRegistry;
  private final UserService userService;
  private final Ee eeService;

  @PostConstruct
  public void init() {
    metricRegistry.registerGauge("total_users_count", "Number of users", userService::globalCount);
    metricRegistry.registerGauge(
        "is_enterprise_edition",
        "enterprise Edition is activated",
        this::isEnterpriseEdition,
        "boolean");
  }

  private long isEnterpriseEdition() {
    return eeService.getEnterpriseEditionInfo().isLicenseValidated() ? 1 : 0;
  }
}
