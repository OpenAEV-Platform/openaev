package io.openaev.telemetry.metric_collectors;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.openaev.rest.exception.CredentialResolutionException;
import io.opentelemetry.api.common.Attributes;
import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Telemetry on credential resolutions: how many return the secret, and why the others are refused,
 * by error code. The success rate is computed downstream from both counts. In-memory counters only,
 * reset at each export.
 *
 * <p>Kept apart from {@link CredentialMetricCollector} on purpose: it depends on nothing but the
 * {@link MetricRegistry}, so the resolution code can feed it without pulling the credential
 * services into a bean cycle.
 */
@Service
@RequiredArgsConstructor
public class CredentialResolutionMetricCollector {

  private static final String ATTRIBUTE_ERROR_CODE = "error_code";

  private final MetricRegistry metricRegistry;

  private final AtomicLong resolutionSuccesses = new AtomicLong();
  private final Map<Attributes, AtomicLong> resolutionFailuresByErrorCode =
      new ConcurrentHashMap<>();

  @PostConstruct
  public void init() {
    metricRegistry.registerGauge(
        "credential_resolution_successes_count",
        "Credential resolutions that returned the secret since the last export",
        this::collectResolutionSuccesses);
    metricRegistry.registerMultiGauge(
        "credential_resolution_failures_count",
        "Credential resolutions refused since the last export, broken down by error code",
        this::collectResolutionFailures);
  }

  /** Counts a credential resolution that returned the secret. */
  public void addResolutionSuccess() {
    resolutionSuccesses.incrementAndGet();
  }

  /**
   * Returns the successful resolutions counted since the last call, and resets them.
   *
   * @return the successful resolution count
   */
  public long collectResolutionSuccesses() {
    return resolutionSuccesses.getAndSet(0);
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
}
