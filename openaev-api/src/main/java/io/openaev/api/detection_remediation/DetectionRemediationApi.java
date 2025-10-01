package io.openaev.api.detection_remediation;

import static io.openaev.api.detection_remediation.DetectionRemediationApi.DETECTION_REMEDIATION_URI;

import io.openaev.aop.LogExecutionTime;
import io.openaev.aop.RBAC;
import io.openaev.api.detection_remediation.dto.DetectionRemediationAIOutput;
import io.openaev.api.detection_remediation.dto.PayloadInput;
import io.openaev.collectors.utils.CollectorsUtils;
import io.openaev.database.model.*;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.payload.form.DetectionRemediationInput;
import io.openaev.rest.payload.form.DetectionRemediationOutput;
import io.openaev.service.detection_remediation.*;
import io.openaev.utils.mapper.PayloadMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
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
  private final InjectService injectService;

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
  @PostMapping("/rules/{collectorType}")
  @LogExecutionTime
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.PAYLOAD)
  public ResponseEntity<DetectionRemediationAIOutput> postRuleDetectionRemediation(
      @PathVariable @NotBlank final String collectorType, @Valid @RequestBody PayloadInput input) {
    if (input.getType().equals(FileDrop.FILE_DROP_TYPE)
        || input.getType().equals(Executable.EXECUTABLE_TYPE))
      throw new ResponseStatusException(
          HttpStatus.NOT_IMPLEMENTED,
          "AI Webservice for FileDrop or Executable File not implemented");

    String rules = getRulesDetectionRemediationByCollector(input, collectorType);

    DetectionRemediationAIOutput detectionRemediationAIOutput =
        DetectionRemediationAIOutput.builder().rules(rules).build();

    return ResponseEntity.ok(detectionRemediationAIOutput);
  }

  private String getRulesDetectionRemediationByCollector(PayloadInput input, String collectorType) {

    Optional<DetectionRemediationInput> currentDetectionRemediation =
        input.getDetectionRemediations().stream()
            .filter(remediation -> remediation.getCollectorType().equals(collectorType))
            .findFirst();

    if (currentDetectionRemediation.isPresent()) {
      // AI cannot replace existing content
      if (!currentDetectionRemediation.get().getValues().isEmpty())
        throw new IllegalStateException("AI Webservice available only for empty content");
    }

    return switch (collectorType) {
      case CollectorsUtils.CROWDSTRIKE ->
          detectionRemediationService.getRulesDetectionRemediationCrowdstrike(input);

      case CollectorsUtils.MICROSOFT_DEFENDER ->
          throw new ResponseStatusException(
              HttpStatus.NOT_IMPLEMENTED,
              "AI Webservice for collector type microsoft defender not implemented");

      case CollectorsUtils.MICROSOFT_SENTINEL ->
          throw new ResponseStatusException(
              HttpStatus.NOT_IMPLEMENTED,
              "AI Webservice for collector type microsoft sentinel not implemented");
      default ->
          throw new IllegalStateException("Collector :\"" + collectorType + "\" unsupported");
    };
  }

  @Operation(summary = "Get detection and remediation rule by inject using AI")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Return rules generated"),
        @ApiResponse(
            responseCode = "500",
            description =
                "Illegal value: Inject has not payload. "
                    + "The feature should not be available for inject without payload. "
                    + "Some inject like email has no payload and no inject contract"),
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
            description = "AI Webservice for collector type microsoft sentinel not implemented"),
        @ApiResponse(
            responseCode = "404",
            description = "Collector not found with type {collectorType}")
      })
  @LogExecutionTime
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.PAYLOAD)
  @PostMapping("rules/inject/{injectId}/collector/{collectorType}")
  public ResponseEntity<DetectionRemediationOutput>
      postRuleDetectionRemediationByInjectIdAndCollectorType(
          @PathVariable @NotBlank String injectId, @PathVariable @NotBlank String collectorType) {

    Inject inject = injectService.inject(injectId);
    Optional<Payload> payloadOptional = inject.getPayload();

    if (payloadOptional.isEmpty())
      throw new IllegalStateException("Illegal value: Inject has not payload");

    Payload payload = payloadOptional.get();
    List<DetectionRemediation> detectionRemediations = payload.getDetectionRemediations();
    DetectionRemediation detectionRemediation =
        detectionRemediationService.getOrCreateDetectionRemediationWithAIRulesByCollector(
            detectionRemediations, payload, collectorType);

    DetectionRemediationOutput detectionRemediationOutput =
        PayloadMapper.toDetectionRemediationOutput(detectionRemediation);

    return ResponseEntity.ok(detectionRemediationOutput);
  }
}
