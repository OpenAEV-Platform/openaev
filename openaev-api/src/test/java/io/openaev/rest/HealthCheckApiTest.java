package io.openaev.rest;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.openaev.IntegrationTest;
import io.openaev.rest.health_check.HealthCheckApi;
import io.openaev.rest.health_check.dto.HealthCheckDetailsOutput;
import io.openaev.service.HealthCheckService;
import io.openaev.service.HealthCheckService.StorageUsage;
import io.openaev.service.exception.HealthCheckFailureException;
import io.openaev.utilstest.RabbitMQTestListener;
import org.junit.jupiter.api.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@TestInstance(PER_CLASS)
public class HealthCheckApiTest extends IntegrationTest {

  private static final String KEY = "KEY";

  @Mock private HealthCheckService healthCheckService;

  @InjectMocks private HealthCheckApi healthCheckApi;

  @BeforeEach
  public void setup() {
    healthCheckApi.setHealthCheckKey(KEY);
  }

  @DisplayName("Test healthCheck")
  @Test
  void test_healthCheck() throws Exception {
    ResponseEntity<?> responseEntity = healthCheckApi.healthCheck(KEY, false);
    verify(healthCheckService).runHealthCheck();
    assertEquals(new ResponseEntity<>("success", HttpStatus.OK), responseEntity);
  }

  @DisplayName("Test healthCheck without details does not compute the storage usage")
  @Test
  void test_healthCheck_WITHOUT_details() throws Exception {
    healthCheckApi.healthCheck(KEY, false);

    verify(healthCheckService).runHealthCheck();
    verifyNoMoreInteractions(healthCheckService);
  }

  @DisplayName("Test healthCheck with details returns the storage usage")
  @Test
  void test_healthCheck_WITH_details() throws Exception {
    when(healthCheckService.getStorageUsage()).thenReturn(new StorageUsage(1L, 2L, 3L));

    ResponseEntity<?> responseEntity = healthCheckApi.healthCheck(KEY, true);

    verify(healthCheckService).runHealthCheck();
    assertEquals(
        new ResponseEntity<>(new HealthCheckDetailsOutput("success", 1L, 2L, 3L), HttpStatus.OK),
        responseEntity);
  }

  @DisplayName("Test healthCheck with wrong key")
  @Test
  void test_healthCheck_WITH_wrong_key() throws Exception {
    ResponseStatusException exceptionThrown =
        assertThrows(
            ResponseStatusException.class,
            () -> {
              healthCheckApi.healthCheck("wrong key", false);
            });
    assertEquals(
        HttpStatusCode.valueOf(HttpStatus.UNAUTHORIZED.value()), exceptionThrown.getStatusCode());
  }

  @DisplayName("Test healthCheck when healthCheckService throws a failure exception")
  @Test
  void test_healthCheck_WHEN_health_check_fails() throws Exception {
    doThrow(new HealthCheckFailureException("test")).when(healthCheckService).runHealthCheck();
    ResponseStatusException exceptionThrown =
        assertThrows(
            ResponseStatusException.class,
            () -> {
              healthCheckApi.healthCheck(KEY, false);
            });
    assertEquals(
        HttpStatusCode.valueOf(HttpStatus.SERVICE_UNAVAILABLE.value()),
        exceptionThrown.getStatusCode());
  }

  @DisplayName("Test healthCheck when healthCheckService throws a runtime exception")
  @Test
  void test_healthCheck_WHEN_health_check_fails_with_a_runtime() throws Exception {
    doThrow(new RuntimeException("test")).when(healthCheckService).runHealthCheck();

    assertThrows(
        RuntimeException.class,
        () -> {
          healthCheckApi.healthCheck(KEY, false);
        });
  }
}
