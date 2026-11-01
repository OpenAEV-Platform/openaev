package io.openaev.telemetry.metric_collectors;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.openaev.service.credential.CredentialService;
import io.opentelemetry.api.common.Attributes;
import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Telemetry on credentials: how many there are, which kinds (identity, AWS, Azure, GCP) and auth
 * methods are used, whether their connectivity check passes, and how many injects rely on them.
 * Counts only, no credential name or secret is ever collected.
 *
 * <p>The counts are platform-wide: {@link CredentialService} opens the {@code TxCtx.allTenants()}
 * scope, without which the v2-active {@code secret_references} table reads as empty.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CredentialMetricCollector {

  private static final String ATTRIBUTE_CREDENTIAL_TYPE = "credential_type";
  private static final String ATTRIBUTE_AUTH_METHOD = "auth_method";
  private static final String ATTRIBUTE_STATUS = "status";

  private final MetricRegistry metricRegistry;
  private final CredentialService credentialService;

  @PostConstruct
  public void init() {
    metricRegistry.registerGauge(
        "total_credentials_count", "Number of credentials", credentialService::globalCount);
    metricRegistry.registerMultiGauge(
        "credentials_total",
        "Credentials broken down by type, auth method and connectivity status",
        this::collectCredentials);
    metricRegistry.registerGauge(
        "injects_with_credentials_total",
        "Number of injects using at least one credential",
        this::countInjectsWithCredentials);
  }

  Map<Attributes, Long> collectCredentials() {
    Map<Attributes, Long> result = new HashMap<>();
    try {
      List<Object[]> rows = credentialService.globalCountByTypeAuthMethodAndStatus();
      for (Object[] row : rows) {
        Attributes attributes =
            Attributes.of(
                stringKey(ATTRIBUTE_CREDENTIAL_TYPE), normalizeEnumLabel(row[0]),
                stringKey(ATTRIBUTE_AUTH_METHOD), normalizeEnumLabel(row[1]),
                stringKey(ATTRIBUTE_STATUS), normalizeEnumLabel(row[2]));
        result.merge(attributes, ((Number) row[3]).longValue(), Long::sum);
      }
    } catch (Exception e) {
      log.error("Telemetry - Failed to collect credential metrics", e);
    }
    return result;
  }

  long countInjectsWithCredentials() {
    try {
      return credentialService.globalCountInjectsWithCredentials();
    } catch (Exception e) {
      log.error("Telemetry - Failed to count injects with credentials", e);
      return 0L;
    }
  }

  /** Enum-backed label value: NULL columns become {@code unknown} instead of the literal "null". */
  private static String normalizeEnumLabel(Object value) {
    return MetricRegistry.normalizeLabel(value == null ? null : value.toString());
  }
}
