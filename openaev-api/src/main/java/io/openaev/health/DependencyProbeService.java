package io.openaev.health;

import static io.openaev.health.PlatformDependency.ENGINE;
import static io.openaev.health.PlatformDependency.OBJECT_STORAGE;
import static io.openaev.health.PlatformDependency.POSTGRESQL;
import static io.openaev.health.PlatformDependency.RABBITMQ;

import io.minio.MinioClient;
import io.openaev.database.repository.HealthCheckRepository;
import io.openaev.engine.EngineService;
import io.openaev.service.MinioService;
import io.openaev.service.RabbitmqService;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Probes the platform dependencies in the background and publishes the outcome to {@link
 * DependencyHealthStore}. This is the only component performing dependency I/O for health and
 * metrics purposes; both {@code /api/health} and the Prometheus gauges are pure readers.
 *
 * <p>Runs on the shared {@code threadPoolTaskScheduler} (20 threads), so a dependency timing out
 * does not starve the other scheduled tasks.
 *
 * <p>Every dependency gets its own scheduled task, all on the same interval — the one {@link
 * io.openaev.service.HealthCheckService} derives its staleness threshold from. Probing them in a
 * single pass would couple them: {@code fixedDelay} never overlaps a task with itself, so one slow
 * probe delays the dependencies queued behind it past that threshold. The engine makes this
 * concrete, as it is the one probe without a short-timeout client — it reuses the shared engine
 * client and its 60s socket timeout, which outlives the staleness budget. A hung engine would
 * therefore stale out the liveness dependencies and 503 the endpoint, the exact outcome {@link
 * PlatformDependency#ENGINE} is declared non-liveness-gating to prevent.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DependencyProbeService {

  private final DependencyHealthStore store;
  private final HealthCheckRepository healthCheckRepository;
  private final MinioService minioService;

  @Qualifier("healthCheckMinioClient")
  private final MinioClient healthCheckMinioClient;

  private final RabbitmqService rabbitmqService;
  private final EngineService engineService;

  /** Database connectivity, on a connection borrowed from the shared pool. */
  @Scheduled(fixedDelayString = "${openaev.healthcheck.connectivity-probe-interval:PT10S}")
  public void probePostgresql() {
    probe(POSTGRESQL, healthCheckRepository::healthCheck);
  }

  /** Broker connectivity, on a dedicated factory with a short connection timeout. */
  @Scheduled(fixedDelayString = "${openaev.healthcheck.connectivity-probe-interval:PT10S}")
  public void probeRabbitmq() {
    probe(RABBITMQ, rabbitmqService::checkHealth);
  }

  /** Object storage connectivity, on a dedicated client with short timeouts. */
  @Scheduled(fixedDelayString = "${openaev.healthcheck.connectivity-probe-interval:PT10S}")
  public void probeObjectStorage() {
    probe(OBJECT_STORAGE, () -> minioService.checkStorageAccessible(healthCheckMinioClient));
  }

  /**
   * Engine connectivity. Reuses the shared engine client, so this probe can hang for a full socket
   * timeout; it is observability only and gates nothing, hence its own task.
   */
  @Scheduled(fixedDelayString = "${openaev.healthcheck.connectivity-probe-interval:PT10S}")
  public void probeEngine() {
    probe(ENGINE, engineService::ping);
  }

  /**
   * Storage sizes. Walking the whole object storage listing costs one round trip per 1 000 objects,
   * hence a schedule an order of magnitude slower than the connectivity one.
   */
  @Scheduled(fixedDelayString = "${openaev.healthcheck.storage-probe-interval:PT4H}")
  public void probeStorageUsage() {
    store.recordStorageUsage(
        new StorageUsage(
            sizeQuietly("PostgreSQL", healthCheckRepository::databaseUsedSize),
            sizeQuietly("engine indexes", engineService::getIndexesUsedSize),
            sizeQuietly("file storage", minioService::computeUsedSize)));
  }

  private void probe(PlatformDependency dependency, DependencyProbe dependencyProbe) {
    Instant startedAt = Instant.now();
    try {
      dependencyProbe.run();
      store.record(dependency, DependencyHealth.up(startedAt, elapsedSince(startedAt)));
    } catch (Exception e) {
      log.warn("Health probe of {} failed", dependency.getLabel(), e);
      store.record(
          dependency,
          DependencyHealth.down(startedAt, elapsedSince(startedAt), describeFailure(e)));
    }
  }

  private Long sizeQuietly(String target, Supplier<Long> sizeSupplier) {
    try {
      return sizeSupplier.get();
    } catch (Exception e) {
      // Sizes are informative only: an unavailable metric is reported as absent, never as an
      // outage.
      log.warn("Unable to compute the {} used size", target, e);
      return null;
    }
  }

  private Duration elapsedSince(Instant startedAt) {
    return Duration.between(startedAt, Instant.now());
  }

  /** Keeps the exception type when the message is empty, so the 503 body is never blank. */
  private String describeFailure(Exception e) {
    String message = e.getMessage();
    return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
  }

  @FunctionalInterface
  private interface DependencyProbe {
    void run() throws Exception;
  }
}
