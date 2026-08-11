package io.openaev.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import io.minio.MinioClient;
import io.openaev.database.repository.*;
import io.openaev.driver.MinioDriver;
import io.openaev.engine.EngineService;
import io.openaev.service.HealthCheckService.StorageUsage;
import io.openaev.service.exception.HealthCheckFailureException;
import java.io.IOException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HealthCheckServiceTest {

  @Mock private HealthCheckRepository healthCheckRepository;
  @Mock private MinioDriver minioDriver;
  @Mock private MinioService minioService;
  @Mock private MinioClient minioClient;
  @Mock private RabbitmqService rabbitmqService;
  @Mock private EngineService engineService;

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
    healthCheckService.runFileStorageCheck();
    verify(minioService).isTenantPathExists(minioClient);
  }

  @DisplayName("Test runFileStorageCheck when check fails ")
  @Test
  void test_runFileStorageCheck_WHEN_client_throws_exception() throws Exception {
    when(minioDriver.getMinioClient()).thenReturn(minioClient);
    doThrow(new IOException("test")).when(minioService).isTenantPathExists(minioClient);
    assertThrows(
        HealthCheckFailureException.class,
        () -> {
          healthCheckService.runFileStorageCheck();
        });
  }

  @DisplayName("Test runRabbitMQCheck")
  @Test
  void test_runRabbitMQCheck() throws HealthCheckFailureException, IOException, TimeoutException {
    healthCheckService.runRabbitMQCheck();
    verify(rabbitmqService).checkHealth();
  }

  @DisplayName("Test runRabbitMQCheck when check fails")
  @Test
  void test_runRabbitMQCheck_WHEN_connection_throws_exception()
      throws IOException, TimeoutException {
    doThrow(new TimeoutException()).when(rabbitmqService).checkHealth();
    assertThrows(
        HealthCheckFailureException.class,
        () -> {
          healthCheckService.runRabbitMQCheck();
        });
  }

  @Nested
  @DisplayName("Storage usage")
  class StorageUsageTest {

    @DisplayName("Given available dependencies, should return the size of each of them")
    @Test
    void given_available_dependencies_should_return_each_size() {
      // -- PREPARE --
      when(healthCheckRepository.databaseUsedSize()).thenReturn(10L);
      when(engineService.getIndexesUsedSize()).thenReturn(20L);
      when(minioService.computeUsedSize()).thenReturn(30L);

      // -- EXECUTE --
      StorageUsage storageUsage = healthCheckService.computeStorageUsage();

      // -- ASSERT --
      assertEquals(new StorageUsage(10L, 20L, 30L), storageUsage);
    }

    @DisplayName("Given a failing dependency, should report a null size without failing")
    @Test
    void given_a_failing_dependency_should_report_a_null_size() {
      // -- PREPARE --
      when(healthCheckRepository.databaseUsedSize()).thenReturn(10L);
      when(engineService.getIndexesUsedSize()).thenThrow(new RuntimeException("engine is down"));
      when(minioService.computeUsedSize()).thenReturn(30L);

      // -- EXECUTE --
      StorageUsage storageUsage = healthCheckService.computeStorageUsage();

      // -- ASSERT --
      assertEquals(10L, storageUsage.pgUsedSize());
      assertNull(storageUsage.esUsedSize());
      assertEquals(30L, storageUsage.s3UsedSize());
    }

    @DisplayName("Given a previous call, should serve the cached value")
    @Test
    void given_a_previous_call_should_serve_the_cached_value() {
      // -- PREPARE --
      when(healthCheckRepository.databaseUsedSize()).thenReturn(10L);
      when(engineService.getIndexesUsedSize()).thenReturn(20L);
      when(minioService.computeUsedSize()).thenReturn(30L);

      // -- EXECUTE --
      StorageUsage first = healthCheckService.getStorageUsage();
      StorageUsage second = healthCheckService.getStorageUsage();

      // -- ASSERT --
      assertEquals(first, second);
      verify(healthCheckRepository, times(1)).databaseUsedSize();
      verify(engineService, times(1)).getIndexesUsedSize();
      verify(minioService, times(1)).computeUsedSize();
    }
  }
}
