package io.openaev.service;

import static io.openaev.health.PlatformDependency.ENGINE;
import static io.openaev.health.PlatformDependency.OBJECT_STORAGE;
import static io.openaev.health.PlatformDependency.POSTGRESQL;
import static io.openaev.health.PlatformDependency.RABBITMQ;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openaev.health.DependencyHealth;
import io.openaev.health.DependencyHealthStore;
import io.openaev.health.PlatformDependency;
import io.openaev.health.StorageUsage;
import io.openaev.service.exception.HealthCheckFailureException;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Health check service")
class HealthCheckServiceTest {

  private static final Duration ANY_LATENCY = Duration.ofMillis(5);

  private final DependencyHealthStore store = new DependencyHealthStore();
  private final HealthCheckService healthCheckService = new HealthCheckService(store);

  private void recordUp(PlatformDependency dependency) {
    store.record(dependency, DependencyHealth.up(Instant.now(), ANY_LATENCY));
  }

  private void recordAllLivenessDependenciesUp() {
    recordUp(POSTGRESQL);
    recordUp(RABBITMQ);
    recordUp(OBJECT_STORAGE);
  }

  @Nested
  @DisplayName("Run health check")
  class RunHealthCheck {

    @DisplayName("Given every dependency probed up, should not fail")
    @Test
    void given_every_dependency_probed_up_should_not_fail() {
      // -- PREPARE --
      recordAllLivenessDependenciesUp();

      // -- ASSERT --
      assertThatCode(healthCheckService::runHealthCheck).doesNotThrowAnyException();
    }

    @DisplayName("Given no probe has run yet, should fail rather than report a healthy platform")
    @Test
    void given_no_probe_has_run_yet_should_fail() {
      assertThatThrownBy(healthCheckService::runHealthCheck)
          .isInstanceOf(HealthCheckFailureException.class)
          .hasMessageContaining("not probed yet");
    }

    @DisplayName("Given a dependency probed down, should fail with its failure message")
    @Test
    void given_a_dependency_probed_down_should_fail() {
      // -- PREPARE --
      recordAllLivenessDependenciesUp();
      store.record(
          OBJECT_STORAGE, DependencyHealth.down(Instant.now(), ANY_LATENCY, "bucket unreachable"));

      // -- ASSERT --
      assertThatThrownBy(healthCheckService::runHealthCheck)
          .isInstanceOf(HealthCheckFailureException.class)
          .hasMessage("s3: bucket unreachable");
    }

    @DisplayName("Given probes older than 3 intervals, should fail on staleness")
    @Test
    void given_probes_older_than_three_intervals_should_fail() {
      // -- PREPARE --
      // Default interval is 10s, so anything older than 30s is stale.
      store.record(POSTGRESQL, DependencyHealth.up(Instant.now().minusSeconds(31), ANY_LATENCY));
      recordUp(RABBITMQ);
      recordUp(OBJECT_STORAGE);

      // -- ASSERT --
      assertThatThrownBy(healthCheckService::runHealthCheck)
          .isInstanceOf(HealthCheckFailureException.class)
          .hasMessageContaining("postgresql: last probe is stale");
    }

    @DisplayName("Given the engine probed down, should stay healthy")
    @Test
    void given_the_engine_probed_down_should_stay_healthy() {
      // -- PREPARE --
      recordAllLivenessDependenciesUp();
      store.record(ENGINE, DependencyHealth.down(Instant.now(), ANY_LATENCY, "engine is down"));

      // -- ASSERT --
      assertThatCode(healthCheckService::runHealthCheck).doesNotThrowAnyException();
    }
  }

  @Nested
  @DisplayName("Storage usage")
  class StorageUsageTest {

    @DisplayName("Given no storage probe yet, should return an unknown usage")
    @Test
    void given_no_storage_probe_yet_should_return_an_unknown_usage() {
      assertThat(healthCheckService.getStorageUsage()).isEqualTo(StorageUsage.unknown());
    }

    @DisplayName("Given a probed usage, should return it without recomputing anything")
    @Test
    void given_a_probed_usage_should_return_it() {
      // -- PREPARE --
      store.recordStorageUsage(new StorageUsage(10L, 20L, 30L));

      // -- ASSERT --
      assertThat(healthCheckService.getStorageUsage()).isEqualTo(new StorageUsage(10L, 20L, 30L));
    }
  }
}
