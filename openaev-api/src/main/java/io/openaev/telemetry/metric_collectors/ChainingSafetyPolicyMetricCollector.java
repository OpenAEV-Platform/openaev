package io.openaev.telemetry.metric_collectors;

import static io.opentelemetry.api.common.AttributeKey.booleanKey;
import static io.opentelemetry.api.common.AttributeKey.longKey;

import io.opentelemetry.api.common.Attributes;
import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Collects metrics related to safety policy configuration changes (timeout and rate limit).
 *
 * <p>Each metric is a multi-dimensional gauge keyed by the configured values and an {@code
 * is_default} flag. Metrics are delta-based: they report the number of changes per collection
 * interval, not cumulative totals.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChainingSafetyPolicyMetricCollector {
  private final MetricRegistry metricRegistry;

  private final Map<Attributes, AtomicLong> timeoutStats = new ConcurrentHashMap<>();
  private final Map<Attributes, AtomicLong> rateLimitStats = new ConcurrentHashMap<>();

  @PostConstruct
  public void init() {
    metricRegistry.registerMultiGauge(
        "safety_timeout_configured",
        "Number of times the workflow timeout safety policy was configured during the collection interval",
        () -> collectAndReset(timeoutStats));
    metricRegistry.registerMultiGauge(
        "safety_ratelimit_configured",
        "Number of times the workflow rate limit safety policy was configured during the collection interval",
        () -> collectAndReset(rateLimitStats));
  }

  /**
   * Records a timeout configuration change with the configured values as dimensions.
   *
   * @param valueHours the configured timeout value in whole hours
   * @param valueMinutes the configured timeout value in whole minutes
   * @param isDefault whether the configured value matches the platform default
   */
  public void recordTimeoutConfigured(long valueHours, long valueMinutes, boolean isDefault) {
    Attributes attrs =
        Attributes.of(
            longKey("value_hours"), valueHours,
            longKey("value_minutes"), valueMinutes,
            booleanKey("is_default"), isDefault);
    timeoutStats.computeIfAbsent(attrs, k -> new AtomicLong(0)).incrementAndGet();
    log.info("Increment Safety Timeout Configured Counter");
  }

  /**
   * Records a rate-limit configuration change with the configured values as dimensions.
   *
   * @param maxAttempts the configured maximum number of attempts
   * @param seconds the configured temporal rate in seconds
   * @param isDefault whether rate limiting is disabled (i.e. the platform default)
   */
  public void recordRateLimitConfigured(long maxAttempts, long seconds, boolean isDefault) {
    Attributes attrs =
        Attributes.of(
            longKey("max_attempts"), maxAttempts,
            longKey("seconds"), seconds,
            booleanKey("is_default"), isDefault);
    rateLimitStats.computeIfAbsent(attrs, k -> new AtomicLong(0)).incrementAndGet();
    log.info("Increment Safety Rate Limit Configured Counter");
  }

  private Map<Attributes, Long> collectAndReset(Map<Attributes, AtomicLong> stats) {
    Map<Attributes, Long> snap = new HashMap<>();
    stats.forEach(
        (k, v) -> {
          long val = v.getAndSet(0);
          if (val > 0) {
            snap.put(k, val);
          }
        });
    return snap;
  }
}
