package io.openaev.api.chaining;

import io.openaev.aop.AccessControl;
import io.openaev.api.chaining.dto.ChainingConfigurationOutput;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.PreviewFeatureService;
import io.openaev.service.chaining.WorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(WorkflowApi.WORKFLOW_URI)
@Tag(name = "Workflow API", description = "Operations related to Chaining")
public class WorkflowApi extends RestBehavior {

  public static final String WORKFLOW_URI = "/api/workflows";

  private final ChainingConfigurationMapper chainingConfigurationMapper;
  private final WorkflowService workflowService;
  private final PreviewFeatureService previewFeatureService;

  // -- Endpoints --

  @Operation(
      summary = "Fetch chaining configuration for a workflow",
      description =
          "Fetch the chaining configuration for a given workflow, including time-out, rate-limit, safe-mode and scope rules.")
  @ApiResponse(responseCode = "200", description = "Chaining configuration retrieved successfully")
  @ApiResponse(
      responseCode = "404",
      description =
          "Chaining configuration not found for the specified workflow, or the INJECT_CHAINING feature is disabled")
  @ApiResponse(responseCode = "500", description = "Unexpected server error")
  @GetMapping("/{workflowId}/chaining-configuration")
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.SIMULATION)
  public ChainingConfigurationOutput getChainingConfiguration(
      @PathVariable @NotBlank final String workflowId) {
    checkChainingFeatureEnabled();
    return chainingConfigurationMapper.toOutput(
        workflowService.getChainingConfiguration(workflowId));
  }

  @Operation(
      summary = "Update chaining configuration for a workflow",
      description = "Update chaining configuration for a given workflow.")
  @ApiResponse(responseCode = "200", description = "Chaining configuration updated successfully")
  @ApiResponse(
      responseCode = "404",
      description =
          "Workflow or chaining configuration not found, or the INJECT_CHAINING feature is disabled")
  @ApiResponse(responseCode = "500", description = "Unexpected server error")
  @PutMapping("/{workflowId}/chaining-configuration")
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.SIMULATION)
  public ChainingConfigurationOutput updateChainingConfiguration(
      @PathVariable @NotBlank final String workflowId,
      @Valid @RequestBody final ChainingConfigurationInput input) {
    checkChainingFeatureEnabled();
    return chainingConfigurationMapper.toOutput(
        workflowService.updateChainingConfiguration(workflowId, input));
  }

  // -- Helpers --

  /**
   * Throws {@link ElementNotFoundException} (HTTP 404) when the {@code INJECT_CHAINING} feature
   * flag is disabled, preventing access to all chaining endpoints.
   */
  private void checkChainingFeatureEnabled() {
    if (!previewFeatureService.isFeatureEnabled(PreviewFeature.INJECT_CHAINING)) {
      throw new ElementNotFoundException("INJECT_CHAINING feature is not enabled");
    }
  }
}
