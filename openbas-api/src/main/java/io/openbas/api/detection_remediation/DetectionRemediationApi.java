package io.openbas.api.detection_remediation;

import static io.openbas.api.detection_remediation.DetectionRemediationApi.DETECTION_REMEDIATION_URI;

import io.openbas.aop.LogExecutionTime;
import io.openbas.aop.RBAC;
import io.openbas.api.detection_remediation.dto.DetectionRemediationOutput;
import io.openbas.api.detection_remediation.dto.PayloadInput;
import io.openbas.database.model.*;
import io.openbas.executors.crowdstrike.service.CrowdStrikeExecutorService;
import io.openbas.rest.payload.form.DetectionRemediationInput;
import io.openbas.service.detection_remediation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping(DETECTION_REMEDIATION_URI)
@RequiredArgsConstructor
public class DetectionRemediationApi {
  private final DetectionRemediationService detectionRemediationService;

  public static final String DETECTION_REMEDIATION_URI = "api/detection-remediations/ai";

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
  @LogExecutionTime
  @RBAC(skipRBAC = true)
  public ResponseEntity<DetectionRemediationHealthResponse> checkHealth() {
    return ResponseEntity.ok(detectionRemediationService.checkHealthWebservice());
  }

  @Operation(summary = "Get detection and remediation rule by payload using AI")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Return rules generated"),
        @ApiResponse(
            responseCode = "500",
            description = "Illegal value, AI Webservice available only for empty content"),
        @ApiResponse(responseCode = "500", description = "Illegal value collector type unknow"),
        @ApiResponse(responseCode = "500", description = "Enterprise Edition is not available"),
        @ApiResponse(
            responseCode = "501",
            description = "AI Webservice for FileDrop or Executable File not implemented"),
        @ApiResponse(
            responseCode = "503",
            description = "Web service is not deployed on this instance"),
        @ApiResponse(
            responseCode = "501",
            description = "AI Webservice for collector type microsoft defender not implemented"),
        @ApiResponse(
            responseCode = "501",
            description = "AI Webservice for collector type microsoft sentinel not implemented")
      })
  @PostMapping("rules")
  @LogExecutionTime
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.PAYLOAD)
  public ResponseEntity<DetectionRemediationOutput> postRuleDetectionRemediation(
      @Valid @RequestBody PayloadInput input) {
    if (input.getType().equals(FileDrop.FILE_DROP_TYPE)
        || input.getType().equals(Executable.EXECUTABLE_TYPE))
      throw new ResponseStatusException(
          HttpStatus.NOT_IMPLEMENTED,
          "AI Webservice for FileDrop or Executable File not implemented");

    String rules = getRulesDetectionRemediationByCollector(input);

    DetectionRemediationOutput detectionRemediationOutput =
        DetectionRemediationOutput.builder().rules(rules).build();

    return ResponseEntity.ok(detectionRemediationOutput);
  }

  private String getRulesDetectionRemediationByCollector(PayloadInput input) {

    Optional<DetectionRemediationInput> currentDetectionRemediation =
        input.getDetectionRemediations().stream()
            .filter(remediation -> remediation.getCollectorType().equals(input.getCollectorType()))
            .findFirst();

    if (currentDetectionRemediation.isPresent()) {
      // AI cannot replace existing content
      if (!currentDetectionRemediation.get().getValues().isEmpty())
        throw new IllegalStateException("AI Webservice available only for empty content");
    }

    return switch (input.getCollectorType()) {
      case CrowdStrikeExecutorService.CROWDSTRIKE_EXECUTOR_TYPE ->
          detectionRemediationService.getRulesDetectionRemediationCrowdstrike(input);

      case "openbas_microsoft_defender" ->
          throw new ResponseStatusException(
              HttpStatus.NOT_IMPLEMENTED,
              "AI Webservice for collector type microsoft defender not implemented");

      case "openbas_microsoft_sentinel" ->
          throw new ResponseStatusException(
              HttpStatus.NOT_IMPLEMENTED,
              "AI Webservice for collector type microsoft sentinel not implemented");
      default ->
          throw new IllegalStateException(
              "Collector :\"" + input.getCollectorType() + "\" unsupported");
    };
  }
}
