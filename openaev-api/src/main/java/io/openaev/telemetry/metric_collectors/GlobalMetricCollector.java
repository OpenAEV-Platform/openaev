package io.openaev.telemetry.metric_collectors;

import io.openaev.ee.EnterpriseEditionService;
import io.openaev.service.UserService;
import io.openaev.service.tenants.TenantService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GlobalMetricCollector {
  private final MetricRegistry metricRegistry;
  private final UserService userService;
  private final EnterpriseEditionService enterpriseEditionService;
  private final TenantService tenantService;

  @PostConstruct
  public void init() {
    metricRegistry.registerGauge("total_users_count", "Number of users", userService::globalCount);
    metricRegistry.registerGauge(
        "is_enterprise_edition",
        "enterprise Edition is activated",
        this::isEnterpriseEdition,
        "boolean");
    metricRegistry.registerGauge(
        "active_tenants_count",
        "Number of active instance tenants",
        tenantService::countActiveTenants);
  }

  private long isEnterpriseEdition() {
    return enterpriseEditionService.getEnterpriseEditionInfo().isLicenseValidated() ? 1 : 0;
  }
}
