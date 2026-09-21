package io.openaev.rest.health_check;

import io.openaev.aop.AccessControl;
import io.openaev.api.health_check.dto.HealthCheckDetailsOutput;
import io.openaev.context.TxCtx;
import io.openaev.health.StorageUsage;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.service.HealthCheckService;
import io.openaev.service.exception.HealthCheckFailureException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Slf4j
public class HealthCheckApi extends RestBehavior {

  public static final String HEALTH_CHECK_URI = "/api/health";

  private static final String SUCCESS_STATUS = "success";

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
  @AccessControl(skipRBAC = true)
  // No RBAC check for health check endpoint
  @Operation(
      summary = "Run an healthcheck ",
      description =
          "Reports the connectivity to the dependencies (DB/RabbitMQ/file storage) as observed by"
              + " the background probes. With details=true, also returns the storage used by each"
              + " dependency. Nothing is computed on call: both come from the periodically"
              + " refreshed state that also feeds /actuator/prometheus")
  // NOT_SUPPORTED: this endpoint only reads in-memory probe results. Opening a transaction would
  // pin a Hikari connection for every load balancer probe, for nothing.
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Service is healthy"),
        @ApiResponse(responseCode = "503", description = "Service is not running properly")
      })
  public ResponseEntity<HealthCheckDetailsOutput> healthCheck(
      TxCtx ctx,
      @RequestParam("health_access_key") String requestHealthAccessKey,
      @RequestParam(value = "details", required = false, defaultValue = "false") boolean details) {
    if (StringUtils.isBlank(requestHealthAccessKey)
        || StringUtils.isBlank(healthCheckKey)
        || !healthCheckKey.equals(requestHealthAccessKey)) {
      throw new ResponseStatusException(HttpStatusCode.valueOf(HttpStatus.UNAUTHORIZED.value()));
    }
    try {
      healthCheckService.runHealthCheck();
    } catch (HealthCheckFailureException e) {
      String message = String.format("Health check failure : %s", e.getMessage());
      // WARN without the stack: load balancers poll this endpoint constantly, so an outage would
      // otherwise flood the logs. The probe that observed the failure already logged it with its
      // cause in DependencyProbeService.
      log.warn(message);
      throw new ResponseStatusException(
          HttpStatusCode.valueOf(HttpStatus.SERVICE_UNAVAILABLE.value()), message);
    }
    if (!details) {
      return new ResponseEntity<>(
          new HealthCheckDetailsOutput(SUCCESS_STATUS, null, null, null), HttpStatus.OK);
    }
    StorageUsage storageUsage = healthCheckService.getStorageUsage();
    return new ResponseEntity<>(
        new HealthCheckDetailsOutput(
            SUCCESS_STATUS,
            storageUsage.pgUsedSize(),
            storageUsage.esUsedSize(),
            storageUsage.s3UsedSize()),
        HttpStatus.OK);
  }
}
