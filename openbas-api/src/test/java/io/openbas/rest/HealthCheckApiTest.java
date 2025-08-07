package io.openbas.rest;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import io.openbas.IntegrationTest;
import io.openbas.rest.health_check.HealthCheckApi;
import io.openbas.service.HealthCheckService;
import io.openbas.service.exception.HealthCheckFailureException;
import org.junit.jupiter.api.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
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
    ResponseEntity<?> responseEntity = healthCheckApi.healthCheck(KEY);
    verify(healthCheckService).runHealthCheck();
    assertEquals(new ResponseEntity<>("success", HttpStatus.OK), responseEntity);
  }

  @DisplayName("Test healthCheck with wrong key")
  @Test
  void test_healthCheck_WITH_wrong_key() throws Exception {
    ResponseStatusException exceptionThrown =
        assertThrows(
            ResponseStatusException.class,
            () -> {
              healthCheckApi.healthCheck("wrong key");
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
              healthCheckApi.healthCheck(KEY);
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
          healthCheckApi.healthCheck(KEY);
        });
  }
}
