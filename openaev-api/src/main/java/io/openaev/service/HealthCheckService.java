package io.openaev.service;

import com.cronutils.utils.VisibleForTesting;
import io.minio.MinioClient;
import io.openaev.database.repository.HealthCheckRepository;
import io.openaev.driver.MinioDriver;
import io.openaev.engine.EngineService;
import io.openaev.service.exception.HealthCheckFailureException;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Service containing the logic related to service health checks */
@RequiredArgsConstructor
@Service
@Slf4j
public class HealthCheckService {

  private final HealthCheckRepository healthCheckRepository;
  private final MinioDriver minioDriver;
  private final MinioService minioService;
  private final RabbitmqService rabbitmqService;
  private final EngineService engineService;

  /**
   * How long a computed {@link StorageUsage} stays valid. The health check endpoint is polled very
   * frequently (load balancer probes) while computing the usage walks the whole object storage
   * listing and queries the engine cluster, so the result is cached and only recomputed once this
   * duration has elapsed.
   */
  @Value("${openaev.healthcheck.storage-usage-cache-duration:PT4H}")
  private Duration storageUsageCacheDuration = Duration.ofHours(4);

  private final AtomicReference<CachedStorageUsage> cachedStorageUsage = new AtomicReference<>();
  private final ReentrantLock storageUsageRefreshLock = new ReentrantLock();

  /**
   * Run health checks by testing connection to the service dependencies (database/rabbitMq/file
   * storage)
   *
   * <p>Note: the analytics engine (Elasticsearch/OpenSearch) connectivity is deliberately NOT
   * checked here, and there is no Redis dependency on this platform. The engine is only contacted
   * for the (cached) storage metrics of {@link #getStorageUsage()}, so an engine outage never turns
   * this probe into a 503.
   *
   * @throws HealthCheckFailureException if any dependency check fails
   */
  public void runHealthCheck() throws HealthCheckFailureException {
    runDatabaseCheck();
    runRabbitMQCheck();
    runFileStorageCheck();
  }

  /**
   * Storage used by the platform dependencies, served from a cache refreshed at most once per
   * {@code openaev.healthcheck.storage-usage-cache-duration}.
   *
   * <p>Metrics are best effort: a dependency failing to report its size yields a {@code null} value
   * instead of failing the health check, which only reflects connectivity.
   *
   * @return the used size of PostgreSQL, of the engine indexes and of the object storage
   */
  public StorageUsage getStorageUsage() {
    CachedStorageUsage cached = cachedStorageUsage.get();
    if (cached != null && !cached.isExpired(storageUsageCacheDuration)) {
      return cached.usage();
    }
    // Single flight: refreshing is expensive, so concurrent probes must not pile up on it. Only one
    // caller recomputes; the others keep serving the previous (stale) value and only block when
    // there is nothing to serve yet.
    if (!storageUsageRefreshLock.tryLock()) {
      if (cached != null) {
        return cached.usage();
      }
      storageUsageRefreshLock.lock();
    }
    try {
      CachedStorageUsage current = cachedStorageUsage.get();
      if (current != null && !current.isExpired(storageUsageCacheDuration)) {
        return current.usage();
      }
      StorageUsage usage = computeStorageUsage();
      cachedStorageUsage.set(new CachedStorageUsage(usage, Instant.now()));
      return usage;
    } finally {
      storageUsageRefreshLock.unlock();
    }
  }

  @VisibleForTesting
  protected void runDatabaseCheck() {
    healthCheckRepository.healthCheck();
  }

  @VisibleForTesting
  protected void runRabbitMQCheck() throws HealthCheckFailureException {
    try {
      rabbitmqService.checkHealth();
    } catch (IOException | TimeoutException e) {
      throw new HealthCheckFailureException("RabbitMQ check failure", e);
    }
  }

  @VisibleForTesting
  protected void runFileStorageCheck() throws HealthCheckFailureException {

    // we get a new client instance to avoid to update the client injected by Spring
    MinioClient minioClient = minioDriver.getMinioClient();
    minioClient.setTimeout(2000L, 2000L, 2000L);
    try {
      minioService.isTenantPathExists(minioClient);
    } catch (Exception e) {
      throw new HealthCheckFailureException("FileStorage check failure", e);
    }
  }

  @VisibleForTesting
  protected StorageUsage computeStorageUsage() {
    return new StorageUsage(
        computeQuietly("PostgreSQL", healthCheckRepository::databaseUsedSize),
        computeQuietly("engine indexes", engineService::getIndexesUsedSize),
        computeQuietly("file storage", minioService::computeUsedSize));
  }

  private Long computeQuietly(String target, Supplier<Long> sizeSupplier) {
    try {
      return sizeSupplier.get();
    } catch (Exception e) {
      // Sizes are informative only: an unavailable metric must not turn a healthy platform into a
      // 503, it is simply reported as null.
      log.warn("Unable to compute the {} used size", target, e);
      return null;
    }
  }

  /**
   * Used size, in bytes, of each storage dependency. A {@code null} value means the metric could
   * not be retrieved.
   *
   * @param pgUsedSize size of the PostgreSQL database
   * @param esUsedSize size of the engine (Elasticsearch/OpenSearch) indexes, replicas excluded
   * @param s3UsedSize size of the objects stored in the bucket
   */
  public record StorageUsage(Long pgUsedSize, Long esUsedSize, Long s3UsedSize) {}

  private record CachedStorageUsage(StorageUsage usage, Instant computedAt) {
    private boolean isExpired(Duration validity) {
      return Instant.now().isAfter(computedAt.plus(validity));
    }
  }
}
