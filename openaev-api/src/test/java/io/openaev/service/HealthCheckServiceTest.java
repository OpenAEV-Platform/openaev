package io.openaev.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import io.minio.errors.*;
import io.openaev.config.MinioConfig;
import io.openaev.config.RabbitmqConfig;
import io.openaev.database.repository.*;
import io.openaev.driver.MinioDriver;
import io.openaev.service.exception.HealthCheckFailureException;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HealthCheckServiceTest {

  private static final String BUCKET = "bucket";

  @Mock private HealthCheckRepository healthCheckRepository;
  @Mock private MinioConfig minioConfig;
  @Mock private MinioDriver minioDriver;
  @Mock private RabbitmqConfig rabbitmqConfig;
  @Mock private MinioClient minioClient;
  @Mock private ConnectionFactory connectionFactory;
  @Mock private Connection connection;

  @InjectMocks private HealthCheckService healthCheckService;

  @DisplayName("Test runDatabaseCheck")
  @Test
  void test_runDatabaseCheck() {
    healthCheckService.runDatabaseCheck();
    verify(healthCheckRepository).healthCheck();
  }

  @DisplayName("Test runFileStorageCheck")
  @Test
  void test_runFileStorageCheck() throws Exception {
    when(minioDriver.getMinioClient()).thenReturn(minioClient);
    when(minioConfig.getBucket()).thenReturn(BUCKET);
    healthCheckService.runFileStorageCheck();
    verify(minioClient).bucketExists(BucketExistsArgs.builder().bucket(BUCKET).build());
  }

  @DisplayName("Test runFileStorageCheck when check fails ")
  @Test
  void test_runFileStorageCheck_WHEN_client_throws_exception()
      throws ServerException,
          InsufficientDataException,
          ErrorResponseException,
          IOException,
          NoSuchAlgorithmException,
          InvalidKeyException,
          InvalidResponseException,
          XmlParserException,
          InternalException {
    when(minioDriver.getMinioClient()).thenReturn(minioClient);
    when(minioConfig.getBucket()).thenReturn(BUCKET);
    when(minioClient.bucketExists(BucketExistsArgs.builder().bucket(BUCKET).build()))
        .thenThrow(new IOException("test"));
    assertThrows(
        HealthCheckFailureException.class,
        () -> {
          healthCheckService.runFileStorageCheck();
        });
  }

  @DisplayName("Test runRabbitMQCheck")
  @Test
  void test_runRabbitMQCheck() throws HealthCheckFailureException, IOException, TimeoutException {
    when(connectionFactory.newConnection()).thenReturn(connection);
    healthCheckService.runRabbitMQCheck(connectionFactory);
  }

  @DisplayName("Test runRabbitMQCheck when check fails")
  @Test
  void test_runRabbitMQCheck_WHEN_connection_throws_exception()
      throws IOException, TimeoutException {
    when(connectionFactory.newConnection()).thenThrow(new TimeoutException());
    assertThrows(
        HealthCheckFailureException.class,
        () -> {
          healthCheckService.runRabbitMQCheck(connectionFactory);
        });
  }
}
