package io.openaev.health;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.TimeGauge;
import jakarta.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Exports the dependency state collected by {@link DependencyProbeService} to Micrometer, hence to
 * {@code /actuator/prometheus}.
 *
 * <p>Gauges observe {@link DependencyHealthStore}, a singleton kept alive by the application
 * context, so the weak reference Micrometer holds on the observed object is never collected.
 * Missing values are reported as {@code NaN} rather than {@code 0}: "not probed yet" and "down"
 * must not look alike on a dashboard.
 */
@Component
@RequiredArgsConstructor
public class DependencyHealthMetrics {

  private static final String DEPENDENCY_TAG = "dependency";
  private static final String STORAGE_TAG = "storage";

  private final MeterRegistry meterRegistry;
  private final DependencyHealthStore store;

  @PostConstruct
  public void registerGauges() {
    for (PlatformDependency dependency : PlatformDependency.values()) {
      Gauge.builder("openaev.dependency.up", store, s -> upValue(s, dependency))
          .tag(DEPENDENCY_TAG, dependency.getLabel())
          .description(
              "1 when the last connectivity probe of the dependency succeeded, 0 when it failed,"
                  + " NaN when it has never been probed")
          .register(meterRegistry);
      TimeGauge.builder(
              "openaev.dependency.probe.duration",
              store,
              TimeUnit.NANOSECONDS,
              s -> latencyValue(s, dependency))
          .tag(DEPENDENCY_TAG, dependency.getLabel())
          .description("Duration of the last connectivity probe of the dependency")
          .register(meterRegistry);
    }
    registerStorageGauge("postgresql", StorageUsage::pgUsedSize);
    registerStorageGauge("engine", StorageUsage::esUsedSize);
    registerStorageGauge("s3", StorageUsage::s3UsedSize);
  }

  private void registerStorageGauge(String storage, Function<StorageUsage, Long> usedSize) {
    Gauge.builder("openaev.storage.used", store, s -> sizeValue(s, usedSize))
        .tag(STORAGE_TAG, storage)
        .baseUnit("bytes")
        .description("Size used by the storage dependency, as of the last storage probe")
        .register(meterRegistry);
  }

  private static double upValue(DependencyHealthStore store, PlatformDependency dependency) {
    return store.get(dependency).map(health -> health.up() ? 1d : 0d).orElse(Double.NaN);
  }

  private static double latencyValue(DependencyHealthStore store, PlatformDependency dependency) {
    return store
        .get(dependency)
        .map(health -> (double) health.latency().toNanos())
        .orElse(Double.NaN);
  }

  private static double sizeValue(
      DependencyHealthStore store, Function<StorageUsage, Long> usedSize) {
    Long size = usedSize.apply(store.getStorageUsage());
    return size == null ? Double.NaN : size;
  }
}
