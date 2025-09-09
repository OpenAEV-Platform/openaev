package io.openbas.api.detection_remediation;

import static io.openbas.api.detection_remediation.DetectionRemediationApi.DETECTION_REMEDIATION_URI;

import io.openbas.aop.RBAC;
import io.openbas.database.model.Action;
import io.openbas.database.model.ResourceType;
import io.openbas.service.detection_remediation.DetectionRemediationHealthResponse;
import io.openbas.service.detection_remediation.DetectionRemediationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(DETECTION_REMEDIATION_URI)
@RequiredArgsConstructor
public class DetectionRemediationApi {
  private final DetectionRemediationService detectionRemediationService;

  public static final String DETECTION_REMEDIATION_URI = "api/detection-remediation";

  @Operation(summary = "Get the status of the remediation-detection web service")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Web service status successfully retrieved"),
        @ApiResponse(
            responseCode = "503",
            description = "Web service is not deployed on this instance")
      })
  @GetMapping("/health")
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.PAYLOAD)
  public ResponseEntity<DetectionRemediationHealthResponse> checkHealth() {
    return ResponseEntity.ok(detectionRemediationService.checkHealthWebservice());
  }
}
