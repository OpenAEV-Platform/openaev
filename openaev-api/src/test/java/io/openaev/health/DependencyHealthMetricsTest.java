package io.openaev.health;

import static io.openaev.health.PlatformDependency.RABBITMQ;
import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Dependency health metrics")
class DependencyHealthMetricsTest {

  private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final DependencyHealthStore store = new DependencyHealthStore();

  @BeforeEach
  void registerGauges() {
    new DependencyHealthMetrics(meterRegistry, store).registerGauges();
  }

  private double dependencyUp(PlatformDependency dependency) {
    return meterRegistry
        .get("openaev.dependency.up")
        .tag("dependency", dependency.getLabel())
        .gauge()
        .value();
  }

  @DisplayName("Given no probe yet, should expose NaN rather than a misleading zero")
  @Test
  void given_no_probe_yet_should_expose_nan() {
    assertThat(dependencyUp(RABBITMQ)).isNaN();
    assertThat(meterRegistry.get("openaev.storage.used").tag("storage", "s3").gauge().value())
        .isNaN();
  }

  @DisplayName("Given a probed dependency, should expose its state and latency")
  @Test
  void given_a_probed_dependency_should_expose_its_state_and_latency() {
    // -- PREPARE --
    store.record(RABBITMQ, DependencyHealth.up(Instant.now(), Duration.ofMillis(42)));

    // -- ASSERT --
    assertThat(dependencyUp(RABBITMQ)).isEqualTo(1d);
    assertThat(
            meterRegistry
                .get("openaev.dependency.probe.duration")
                .tag("dependency", RABBITMQ.getLabel())
                .timeGauge()
                .value(TimeUnit.MILLISECONDS))
        .isEqualTo(42d);
  }

  @DisplayName("Given a dependency down, should expose zero")
  @Test
  void given_a_dependency_down_should_expose_zero() {
    // -- PREPARE --
    store.record(RABBITMQ, DependencyHealth.down(Instant.now(), Duration.ZERO, "boom"));

    // -- ASSERT --
    assertThat(dependencyUp(RABBITMQ)).isZero();
  }

  @DisplayName("Given a storage probe, should expose each used size in bytes")
  @Test
  void given_a_storage_probe_should_expose_each_used_size() {
    // -- PREPARE --
    store.recordStorageUsage(new StorageUsage(10L, 20L, null));

    // -- ASSERT --
    assertThat(
            meterRegistry.get("openaev.storage.used").tag("storage", "postgresql").gauge().value())
        .isEqualTo(10d);
    assertThat(meterRegistry.get("openaev.storage.used").tag("storage", "engine").gauge().value())
        .isEqualTo(20d);
    assertThat(meterRegistry.get("openaev.storage.used").tag("storage", "s3").gauge().value())
        .isNaN();
  }
}
