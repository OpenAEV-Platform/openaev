package io.openaev.telemetry.metric_collectors;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.openaev.rest.exception.CredentialResolutionException;
import io.openaev.service.credential.CredentialService;
import io.opentelemetry.api.common.Attributes;
import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Telemetry on credentials: how many there are, which kinds (identity, AWS, Azure, GCP) and auth
 * methods are used, whether their connectivity check passes, how many injects, users and injectors
 * rely on them, and why their resolutions are refused. Counts only, no credential name, user
 * identity or secret is ever collected.
 *
 * <p>The counts are platform-wide: {@link CredentialService} opens the {@code TxCtx.allTenants()}
 * scope, without which the v2-active {@code secret_references} table reads as empty. Resolution
 * failures are in-memory counters, reset at each export.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CredentialMetricCollector {

  private static final String ATTRIBUTE_CREDENTIAL_TYPE = "credential_type";
  private static final String ATTRIBUTE_AUTH_METHOD = "auth_method";
  private static final String ATTRIBUTE_STATUS = "status";
  private static final String ATTRIBUTE_INJECTOR_TYPE = "injector_type";
  private static final String ATTRIBUTE_ERROR_CODE = "error_code";

  private final MetricRegistry metricRegistry;
  private final CredentialService credentialService;

  private final Map<Attributes, AtomicLong> resolutionFailuresByErrorCode =
      new ConcurrentHashMap<>();

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
    metricRegistry.registerGauge(
        "credentials_attached_to_injects_total",
        "Number of credentials attached to at least one inject",
        this::countCredentialsAttachedToInjects);
    metricRegistry.registerGauge(
        "users_with_injects_using_credentials_total",
        "Number of distinct users owning at least one inject that uses a credential",
        this::countUsersWithInjectsUsingCredentials);
    metricRegistry.registerMultiGauge(
        "credential_usages_total",
        "Inject-credential links broken down by credential type and injector type",
        this::collectCredentialUsages);
    metricRegistry.registerMultiGauge(
        "credential_resolution_failures_count",
        "Credential resolutions refused since the last export, broken down by error code",
        this::collectResolutionFailures);
  }

  /**
   * Counts a credential resolution refused with the given error code.
   *
   * @param errorCode the error code the resolution was refused with
   */
  public void addResolutionFailure(CredentialResolutionException.ErrorCode errorCode) {
    resolutionFailuresByErrorCode
        .computeIfAbsent(
            Attributes.of(stringKey(ATTRIBUTE_ERROR_CODE), errorCode.name()),
            key -> new AtomicLong())
        .incrementAndGet();
  }

  /**
   * Returns the refused resolutions counted since the last call, by error code, and resets them.
   *
   * @return the refused resolution count per error code
   */
  public Map<Attributes, Long> collectResolutionFailures() {
    Map<Attributes, Long> snapshot = new HashMap<>();
    resolutionFailuresByErrorCode.forEach(
        (attributes, value) -> {
          long collected = value.getAndSet(0);
          if (collected > 0) {
            snapshot.put(attributes, collected);
          }
        });
    return snapshot;
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

  Map<Attributes, Long> collectCredentialUsages() {
    Map<Attributes, Long> result = new HashMap<>();
    try {
      List<Object[]> rows = credentialService.globalCountUsagesByCredentialTypeAndInjectorType();
      for (Object[] row : rows) {
        Attributes attributes =
            Attributes.of(
                stringKey(ATTRIBUTE_CREDENTIAL_TYPE), normalizeEnumLabel(row[0]),
                stringKey(ATTRIBUTE_INJECTOR_TYPE), normalizeEnumLabel(row[1]));
        result.merge(attributes, ((Number) row[2]).longValue(), Long::sum);
      }
    } catch (Exception e) {
      log.error("Telemetry - Failed to collect credential usage metrics", e);
    }
    return result;
  }

  long countInjectsWithCredentials() {
    return countOrZero(
        credentialService::globalCountInjectsWithCredentials, "injects with credentials");
  }

  long countCredentialsAttachedToInjects() {
    return countOrZero(
        credentialService::globalCountCredentialsAttachedToInjects,
        "credentials attached to injects");
  }

  long countUsersWithInjectsUsingCredentials() {
    return countOrZero(
        credentialService::globalCountUsersWithInjectsUsingCredentials,
        "users with injects using credentials");
  }

  /** A failing count reports zero instead of breaking the whole telemetry export. */
  private static long countOrZero(LongSupplier count, String countedItems) {
    try {
      return count.getAsLong();
    } catch (Exception e) {
      log.error("Telemetry - Failed to count {}", countedItems, e);
      return 0L;
    }
  }

  /** Enum-backed label value: NULL columns become {@code unknown} instead of the literal "null". */
  private static String normalizeEnumLabel(Object value) {
    return MetricRegistry.normalizeLabel(value == null ? null : value.toString());
  }
}
