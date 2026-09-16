package io.openaev.telemetry.metric_collectors;

import io.openaev.service.credential.CredentialService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CredentialMetricCollector {
  private final MetricRegistry metricRegistry;
  private final CredentialService credentialService;

  @PostConstruct
  public void init() {
    metricRegistry.registerGauge(
        "total_credentials_count", "Number of credentials", credentialService::globalCount);
  }
}
