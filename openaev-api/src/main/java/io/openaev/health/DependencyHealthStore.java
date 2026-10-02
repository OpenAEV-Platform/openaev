package io.openaev.health;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/**
 * Last known state of the platform dependencies: written by {@link DependencyProbeService} from the
 * scheduler, read by the Prometheus gauges and by {@code /api/health}. Readers never perform I/O,
 * which is the whole point of the store — a load balancer probe must not be able to pin a thread on
 * a degraded dependency.
 */
@Component
public class DependencyHealthStore {

  private final Map<PlatformDependency, DependencyHealth> healths = new ConcurrentHashMap<>();
  private final AtomicReference<StorageUsage> storageUsage =
      new AtomicReference<>(StorageUsage.unknown());

  public void record(PlatformDependency dependency, DependencyHealth health) {
    healths.put(dependency, health);
  }

  public void recordStorageUsage(StorageUsage usage) {
    storageUsage.set(usage);
  }

  public Optional<DependencyHealth> get(PlatformDependency dependency) {
    return Optional.ofNullable(healths.get(dependency));
  }

  public StorageUsage getStorageUsage() {
    return storageUsage.get();
  }

  /**
   * Describes every liveness-gating dependency that is not currently known to be healthy: never
   * probed, probed before {@code freshnessThreshold}, or probed down. A stale result counts as a
   * failure because a scheduler that stopped probing is itself an outage.
   *
   * @return one human-readable reason per failing dependency, empty when the platform is healthy
   */
  public List<String> livenessFailures(Instant freshnessThreshold) {
    return Arrays.stream(PlatformDependency.values())
        .filter(PlatformDependency::isRequiredForLiveness)
        .map(dependency -> livenessFailure(dependency, freshnessThreshold))
        .flatMap(Optional::stream)
        .toList();
  }

  private Optional<String> livenessFailure(
      PlatformDependency dependency, Instant freshnessThreshold) {
    DependencyHealth health = healths.get(dependency);
    if (health == null) {
      return Optional.of(dependency.getLabel() + ": not probed yet");
    }
    if (health.isStaleAt(freshnessThreshold)) {
      return Optional.of(
          dependency.getLabel() + ": last probe is stale (" + health.probedAt() + ")");
    }
    if (!health.up()) {
      return Optional.of(dependency.getLabel() + ": " + health.failure());
    }
    return Optional.empty();
  }
}
