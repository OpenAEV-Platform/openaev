package io.openaev.rest.health_check;

import io.openaev.aop.RBAC;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.service.HealthCheckService;
import io.openaev.service.exception.HealthCheckFailureException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class HealthCheckApi extends RestBehavior {

  public static final String HEALTH_CHECK_URI = "/api/health";

  private HealthCheckService healthCheckService;

  private String healthCheckKey;

  @Autowired
  public void setHealthCheckService(HealthCheckService healthCheckService) {
    this.healthCheckService = healthCheckService;
  }

  @Autowired
  public void setHealthCheckKey(
      @Value("${openbas.healthcheck.key:${openaev.healthcheck.key:#{null}}}")
          String healthCheckKey) {
    this.healthCheckKey = healthCheckKey;
  }

  @GetMapping(HEALTH_CHECK_URI)
  @RBAC(skipRBAC = true) // No RBAC check for health check endpoint
  @Operation(
      summary = "Run an healthcheck ",
      description = "Tries to connect to dependencies (DB/Minio/RabbitMQ)")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Service is healthy"),
        @ApiResponse(responseCode = "503", description = "Service is not running properly")
      })
  public ResponseEntity<?> healthCheck(
      @RequestParam("health_access_key") String requestHealthAccessKey) {
    if (StringUtils.isBlank(requestHealthAccessKey)
        || StringUtils.isBlank(healthCheckKey)
        || !healthCheckKey.equals(requestHealthAccessKey)) {
      throw new ResponseStatusException(HttpStatusCode.valueOf(HttpStatus.UNAUTHORIZED.value()));
    }
    try {
      healthCheckService.runHealthCheck();
    } catch (HealthCheckFailureException e) {
      String message = String.format("Health check failure : %s", e.getMessage());
      throw new ResponseStatusException(
          HttpStatusCode.valueOf(HttpStatus.SERVICE_UNAVAILABLE.value()), message);
    }
    return new ResponseEntity<>("success", HttpStatus.OK);
  }
}
