package io.openaev.telemetry.metric_collectors;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.openaev.database.repository.TenantXtmHubRegistrationRepository;
import io.opentelemetry.api.common.Attributes;
import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/**
 * Platform adoption gauges: authentication strategies and ecosystem registration state. These are
 * configuration booleans (0/1), exported so analytics can segment the install base - same intent as
 * the SSO strategy gauges exported by OpenCTI.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformAdoptionMetricCollector {

  private static final String ATTRIBUTE_STRATEGY = "strategy";

  private static final Map<String, String> SSO_STRATEGY_PROPERTIES =
      Map.of(
          "local", "openaev.auth-local-enable",
          "oidc", "openaev.auth-openid-enable",
          "saml2", "openaev.auth-saml2-enable",
          "kerberos", "openaev.auth-kerberos-enable");

  private final MetricRegistry metricRegistry;
  private final Environment environment;
  private final TenantXtmHubRegistrationRepository tenantXtmHubRegistrationRepository;

  @PostConstruct
  public void init() {
    metricRegistry.registerMultiGauge(
        "sso_strategy_enabled",
        "Authentication strategies configured and enabled, by strategy",
        this::collectSsoStrategies,
        "boolean");
    metricRegistry.registerGauge(
        "xtm_hub_registered",
        "At least one tenant is registered on XTM Hub",
        this::isXtmHubRegistered,
        "boolean");
  }

  private Map<Attributes, Long> collectSsoStrategies() {
    Map<Attributes, Long> strategies = new HashMap<>();
    SSO_STRATEGY_PROPERTIES.forEach(
        (strategy, property) -> {
          boolean enabled = Boolean.parseBoolean(environment.getProperty(property, "false"));
          strategies.put(Attributes.of(stringKey(ATTRIBUTE_STRATEGY), strategy), enabled ? 1L : 0L);
        });
    return strategies;
  }

  private long isXtmHubRegistered() {
    try {
      return tenantXtmHubRegistrationRepository.count() > 0 ? 1L : 0L;
    } catch (Exception e) {
      log.error("Telemetry - Failed to read XTM Hub registration state", e);
      return 0L;
    }
  }
}
