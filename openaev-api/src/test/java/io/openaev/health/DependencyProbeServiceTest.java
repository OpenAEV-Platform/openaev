package io.openaev.health;

import static io.openaev.health.PlatformDependency.ENGINE;
import static io.openaev.health.PlatformDependency.OBJECT_STORAGE;
import static io.openaev.health.PlatformDependency.POSTGRESQL;
import static io.openaev.health.PlatformDependency.RABBITMQ;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.minio.MinioClient;
import io.openaev.database.repository.HealthCheckRepository;
import io.openaev.engine.EngineService;
import io.openaev.service.MinioService;
import io.openaev.service.RabbitmqService;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Qualifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("Dependency probe service")
class DependencyProbeServiceTest {

  @Mock private HealthCheckRepository healthCheckRepository;
  @Mock private MinioService minioService;
  @Mock private MinioClient healthCheckMinioClient;
  @Mock private RabbitmqService rabbitmqService;
  @Mock private EngineService engineService;

  private final DependencyHealthStore store = new DependencyHealthStore();

  private DependencyProbeService probeService() {
    return new DependencyProbeService(
        store,
        healthCheckRepository,
        minioService,
        healthCheckMinioClient,
        rabbitmqService,
        engineService);
  }

  @DisplayName("The generated constructor must carry the storage client qualifier")
  @Test
  void the_generated_constructor_must_carry_the_storage_client_qualifier() {
    // Guards lombok.copyableAnnotations: without it Spring injects the default MinioClient, and
    // the probe silently stops using the short-timeout one.
    Constructor<?> constructor = DependencyProbeService.class.getDeclaredConstructors()[0];
    int clientIndex = Arrays.asList(constructor.getParameterTypes()).indexOf(MinioClient.class);

    assertThat(constructor.getParameterAnnotations()[clientIndex])
        .filteredOn(Qualifier.class::isInstance)
        .singleElement()
        .extracting(annotation -> ((Qualifier) annotation).value())
        .isEqualTo("healthCheckMinioClient");
  }

  @Nested
  @DisplayName("Connectivity probe")
  class ConnectivityProbe {

    @DisplayName("Given reachable dependencies, should record every one of them as up")
    @Test
    void given_reachable_dependencies_should_record_every_one_as_up() throws Exception {
      // -- EXECUTE --
      DependencyProbeService probeService = probeService();
      probeService.probePostgresql();
      probeService.probeRabbitmq();
      probeService.probeObjectStorage();
      probeService.probeEngine();

      // -- ASSERT --
      verify(healthCheckRepository).healthCheck();
      verify(rabbitmqService).checkHealth();
      verify(minioService).checkStorageAccessible(healthCheckMinioClient);
      verify(engineService).ping();
      for (PlatformDependency dependency : PlatformDependency.values()) {
        assertThat(store.get(dependency)).get().extracting(DependencyHealth::up).isEqualTo(true);
      }
    }

    @DisplayName("Given a failing dependency, should record it as down with its failure message")
    @Test
    void given_a_failing_dependency_should_record_it_as_down() throws Exception {
      // -- PREPARE --
      doThrow(new TimeoutException("broker unreachable")).when(rabbitmqService).checkHealth();

      // -- EXECUTE --
      DependencyProbeService probeService = probeService();
      probeService.probeRabbitmq();
      probeService.probePostgresql();

      // -- ASSERT --
      assertThat(store.get(RABBITMQ))
          .get()
          .satisfies(
              health -> {
                assertThat(health.up()).isFalse();
                assertThat(health.failure()).isEqualTo("broker unreachable");
              });
      assertThat(store.get(POSTGRESQL)).get().extracting(DependencyHealth::up).isEqualTo(true);
    }

    @DisplayName("Given one probe task, should leave every other dependency untouched")
    @Test
    void given_one_probe_task_should_leave_every_other_dependency_untouched() throws Exception {
      // Guards the per-dependency scheduling: probing them in one pass lets a slow dependency
      // delay the others past the staleness threshold, which 503s the endpoint.
      // -- EXECUTE --
      probeService().probeRabbitmq();

      // -- ASSERT --
      verify(rabbitmqService).checkHealth();
      assertThat(store.get(RABBITMQ)).isPresent();
      assertThat(store.get(POSTGRESQL)).isEmpty();
      assertThat(store.get(OBJECT_STORAGE)).isEmpty();
      assertThat(store.get(ENGINE)).isEmpty();
      verifyNoInteractions(healthCheckRepository, minioService, engineService);
    }

    @DisplayName("Given a failure without a message, should record the exception type instead")
    @Test
    void given_a_failure_without_a_message_should_record_the_exception_type() throws Exception {
      // -- PREPARE --
      doThrow(new TimeoutException()).when(rabbitmqService).checkHealth();

      // -- EXECUTE --
      probeService().probeRabbitmq();

      // -- ASSERT --
      assertThat(store.get(RABBITMQ))
          .get()
          .extracting(DependencyHealth::failure)
          .isEqualTo("TimeoutException");
    }
  }

  @Nested
  @DisplayName("Storage probe")
  class StorageProbe {

    @DisplayName("Given available dependencies, should record the size of each of them")
    @Test
    void given_available_dependencies_should_record_each_size() {
      // -- PREPARE --
      when(healthCheckRepository.databaseUsedSize()).thenReturn(10L);
      when(engineService.getIndexesUsedSize()).thenReturn(20L);
      when(minioService.computeUsedSize()).thenReturn(30L);

      // -- EXECUTE --
      probeService().probeStorageUsage();

      // -- ASSERT --
      assertThat(store.getStorageUsage()).isEqualTo(new StorageUsage(10L, 20L, 30L));
    }

    @DisplayName("Given a failing dependency, should record a null size without failing")
    @Test
    void given_a_failing_dependency_should_record_a_null_size() {
      // -- PREPARE --
      when(healthCheckRepository.databaseUsedSize()).thenReturn(10L);
      when(engineService.getIndexesUsedSize()).thenThrow(new RuntimeException("engine is down"));
      when(minioService.computeUsedSize()).thenReturn(30L);

      // -- EXECUTE --
      probeService().probeStorageUsage();

      // -- ASSERT --
      assertThat(store.getStorageUsage()).isEqualTo(new StorageUsage(10L, null, 30L));
    }
  }
}
