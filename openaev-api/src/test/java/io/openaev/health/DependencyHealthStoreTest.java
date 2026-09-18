package io.openaev.health;

import static io.openaev.health.PlatformDependency.ENGINE;
import static io.openaev.health.PlatformDependency.OBJECT_STORAGE;
import static io.openaev.health.PlatformDependency.POSTGRESQL;
import static io.openaev.health.PlatformDependency.RABBITMQ;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Dependency health store")
class DependencyHealthStoreTest {

  private static final Duration ANY_LATENCY = Duration.ofMillis(5);

  private final DependencyHealthStore store = new DependencyHealthStore();

  private static Instant freshnessThreshold() {
    return Instant.now().minusSeconds(30);
  }

  private void recordUp(PlatformDependency dependency) {
    store.record(dependency, DependencyHealth.up(Instant.now(), ANY_LATENCY));
  }

  private void recordAllLivenessDependenciesUp() {
    recordUp(POSTGRESQL);
    recordUp(RABBITMQ);
    recordUp(OBJECT_STORAGE);
  }

  @Nested
  @DisplayName("Liveness failures")
  class LivenessFailures {

    @DisplayName("Given every dependency probed up, should report no failure")
    @Test
    void given_every_dependency_probed_up_should_report_no_failure() {
      // -- PREPARE --
      recordAllLivenessDependenciesUp();

      // -- EXECUTE --
      List<String> failures = store.livenessFailures(freshnessThreshold());

      // -- ASSERT --
      assertThat(failures).isEmpty();
    }

    @DisplayName("Given no probe yet, should report every liveness dependency")
    @Test
    void given_no_probe_yet_should_report_every_liveness_dependency() {
      // -- EXECUTE --
      List<String> failures = store.livenessFailures(freshnessThreshold());

      // -- ASSERT --
      assertThat(failures)
          .containsExactly(
              "postgresql: not probed yet", "rabbitmq: not probed yet", "s3: not probed yet");
    }

    @DisplayName("Given a dependency probed down, should report its failure message")
    @Test
    void given_a_dependency_probed_down_should_report_its_failure_message() {
      // -- PREPARE --
      recordAllLivenessDependenciesUp();
      store.record(
          RABBITMQ, DependencyHealth.down(Instant.now(), ANY_LATENCY, "connection refused"));

      // -- EXECUTE --
      List<String> failures = store.livenessFailures(freshnessThreshold());

      // -- ASSERT --
      assertThat(failures).containsExactly("rabbitmq: connection refused");
    }

    @DisplayName("Given a probe older than the threshold, should report it as stale")
    @Test
    void given_a_probe_older_than_the_threshold_should_report_it_as_stale() {
      // -- PREPARE --
      recordAllLivenessDependenciesUp();
      store.record(POSTGRESQL, DependencyHealth.up(Instant.now().minusSeconds(120), ANY_LATENCY));

      // -- EXECUTE --
      List<String> failures = store.livenessFailures(freshnessThreshold());

      // -- ASSERT --
      assertThat(failures).singleElement().asString().startsWith("postgresql: last probe is stale");
    }

    @DisplayName("Given the engine down, should not report a liveness failure")
    @Test
    void given_the_engine_down_should_not_report_a_liveness_failure() {
      // -- PREPARE --
      recordAllLivenessDependenciesUp();
      store.record(ENGINE, DependencyHealth.down(Instant.now(), ANY_LATENCY, "engine is down"));

      // -- EXECUTE --
      List<String> failures = store.livenessFailures(freshnessThreshold());

      // -- ASSERT --
      assertThat(failures).isEmpty();
    }
  }

  @Nested
  @DisplayName("Storage usage")
  class StorageUsageState {

    @DisplayName("Given no probe yet, should return an unknown usage")
    @Test
    void given_no_probe_yet_should_return_an_unknown_usage() {
      assertThat(store.getStorageUsage()).isEqualTo(StorageUsage.unknown());
    }

    @DisplayName("Given a recorded usage, should return it")
    @Test
    void given_a_recorded_usage_should_return_it() {
      // -- PREPARE --
      StorageUsage usage = new StorageUsage(10L, 20L, 30L);
      store.recordStorageUsage(usage);

      // -- ASSERT --
      assertThat(store.getStorageUsage()).isEqualTo(usage);
    }
  }
}
