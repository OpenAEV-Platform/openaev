package io.openaev.health;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** An infrastructure dependency probed in the background and exported as a Prometheus gauge. */
@Getter
@RequiredArgsConstructor
public enum PlatformDependency {
  POSTGRESQL("postgresql", true),
  RABBITMQ("rabbitmq", true),
  OBJECT_STORAGE("s3", true),
  /**
   * Observability only: an engine outage degrades analytics but leaves the instance able to serve
   * traffic, so it must never pull the instance out of the load balancer.
   */
  ENGINE("engine", false);

  /** Value of the {@code dependency} metric label. */
  private final String label;

  /** Whether a failed probe of this dependency must turn {@code /api/health} into a 503. */
  private final boolean requiredForLiveness;
}
