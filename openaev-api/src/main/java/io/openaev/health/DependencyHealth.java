package io.openaev.health;

import java.time.Duration;
import java.time.Instant;

/**
 * Outcome of the last background probe of a dependency.
 *
 * @param up whether the probe succeeded
 * @param probedAt when the probe ran
 * @param latency how long the probe took
 * @param failure the failure description, {@code null} when {@code up}
 */
public record DependencyHealth(boolean up, Instant probedAt, Duration latency, String failure) {

  public static DependencyHealth up(Instant probedAt, Duration latency) {
    return new DependencyHealth(true, probedAt, latency, null);
  }

  public static DependencyHealth down(Instant probedAt, Duration latency, String failure) {
    return new DependencyHealth(false, probedAt, latency, failure);
  }

  public boolean isStaleAt(Instant freshnessThreshold) {
    return probedAt.isBefore(freshnessThreshold);
  }
}
