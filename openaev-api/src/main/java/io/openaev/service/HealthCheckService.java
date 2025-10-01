package io.openaev.service;

import com.cronutils.utils.VisibleForTesting;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import io.minio.errors.*;
import io.openaev.config.MinioConfig;
import io.openaev.config.RabbitmqConfig;
import io.openaev.database.repository.HealthCheckRepository;
import io.openaev.driver.MinioDriver;
import io.openaev.service.exception.HealthCheckFailureException;
import jakarta.annotation.Resource;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Service containing the logic related to service health checks */
@RequiredArgsConstructor
@Service
@Slf4j
public class HealthCheckService {

  @Autowired private HealthCheckRepository healthCheckRepository;

  @Autowired private MinioConfig minioConfig;

  @Autowired private MinioDriver minioDriver;

  @Resource private RabbitmqConfig rabbitmqConfig;

  /**
   * Run health checks by testing connection to the service dependencies (database/rabbitMq/file
   * storage)
   *
   * @throws HealthCheckFailureException
   */
  public void runHealthCheck() throws HealthCheckFailureException {
    runDatabaseCheck();
    runRabbitMQCheck(createRabbitMQConnectionFactory());
    runFileStorageCheck();
  }

  @VisibleForTesting
  protected void runDatabaseCheck() {
    healthCheckRepository.healthCheck();
  }

  @VisibleForTesting
  protected ConnectionFactory createRabbitMQConnectionFactory() {
    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost(rabbitmqConfig.getHostname());
    factory.setPort(rabbitmqConfig.getPort());
    factory.setUsername(rabbitmqConfig.getUser());
    factory.setPassword(rabbitmqConfig.getPass());
    factory.setVirtualHost(rabbitmqConfig.getVhost());
    factory.setConnectionTimeout(2000);
    return factory;
  }

  @VisibleForTesting
  protected void runRabbitMQCheck(ConnectionFactory connectionFactory)
      throws HealthCheckFailureException {
    // Declare queueing
    Connection connection = null;
    try {
      connection = connectionFactory.newConnection();
      connection.createChannel();
    } catch (IOException | TimeoutException e) {
      throw new HealthCheckFailureException("RabbitMQ check failure", e);
    } finally {
      if (connection != null) {
        try {
          connection.close();
        } catch (IOException e) {
          log.error(
              "Unable to close RabbitMQ connection. You should worry as this could impact performance",
              e);
        }
      }
    }
  }

  @VisibleForTesting
  protected void runFileStorageCheck() throws HealthCheckFailureException {

    // we get a new client instance to avoid to update the client injected by Spring
    MinioClient minioClient = minioDriver.getMinioClient();
    minioClient.setTimeout(2000L, 2000L, 2000L);
    try {
      minioClient.bucketExists(BucketExistsArgs.builder().bucket(minioConfig.getBucket()).build());
    } catch (ErrorResponseException
        | InvalidResponseException
        | InsufficientDataException
        | InternalException
        | InvalidKeyException
        | IOException
        | NoSuchAlgorithmException
        | ServerException
        | XmlParserException e) {
      throw new HealthCheckFailureException("FileStorage check failure", e);
    }
  }
}
