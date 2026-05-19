package io.openaev.service;

import com.cronutils.utils.VisibleForTesting;
import io.minio.MinioClient;
import io.openaev.database.repository.HealthCheckRepository;
import io.openaev.driver.MinioDriver;
import io.openaev.service.exception.HealthCheckFailureException;
import java.io.IOException;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

  /**
   * Run health checks by testing connection to the service dependencies (database/rabbitMq/file
   * storage)
   *
   * @throws HealthCheckFailureException if any dependency check fails
   */
  public void runHealthCheck() throws HealthCheckFailureException {
    runDatabaseCheck();
    runRabbitMQCheck();
    runFileStorageCheck();
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
      minioService.isTenantPathExists();
    } catch (Exception e) {
      throw new HealthCheckFailureException("FileStorage check failure", e);
    }
  }
}
