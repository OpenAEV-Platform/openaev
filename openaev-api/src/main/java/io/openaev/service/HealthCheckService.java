package io.openaev.service;

import io.openaev.health.DependencyHealthStore;
import io.openaev.health.StorageUsage;
import io.openaev.service.exception.HealthCheckFailureException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Serves the platform health from the state published by the background probes, without ever
 * contacting a dependency: the endpoint is polled by load balancers far more often than the
 * dependencies change state, and synchronous probes let a degraded dependency pin request threads.
 */
@RequiredArgsConstructor
@Service
@Slf4j
public class HealthCheckService {

  /**
   * A probe result older than this many intervals is treated as a failure: a scheduler that stopped
   * probing can no longer vouch for the platform, so the endpoint must not keep serving a success.
   */
  private static final int STALE_PROBE_INTERVALS = 3;

  private final DependencyHealthStore dependencyHealthStore;

  @Value("${openaev.healthcheck.connectivity-probe-interval:PT10S}")
  private Duration connectivityProbeInterval = Duration.ofSeconds(10);

  /**
   * Checks that every liveness-gating dependency (database, RabbitMQ, file storage) was seen up by
   * a recent probe.
   *
   * <p>The analytics engine is deliberately excluded: it is probed and exported as a metric, but an
   * engine outage degrades analytics only and must never turn this probe into a 503.
   *
   * @throws HealthCheckFailureException if a dependency is down or has not been probed recently
   */
  public void runHealthCheck() throws HealthCheckFailureException {
    List<String> failures = dependencyHealthStore.livenessFailures(stalenessThreshold());
    if (!failures.isEmpty()) {
      throw new HealthCheckFailureException(String.join(", ", failures));
    }
  }

  /**
   * Storage used by the platform dependencies, as of the last storage probe.
   *
   * <p>Sizes are best effort: a dependency failing to report its size yields a {@code null} value
   * instead of failing the health check, which only reflects connectivity.
   *
   * @return the used size of PostgreSQL, of the engine indexes and of the object storage
   */
  public StorageUsage getStorageUsage() {
    return dependencyHealthStore.getStorageUsage();
  }

  private Instant stalenessThreshold() {
    return Instant.now().minus(connectivityProbeInterval.multipliedBy(STALE_PROBE_INTERVALS));
  }
}
