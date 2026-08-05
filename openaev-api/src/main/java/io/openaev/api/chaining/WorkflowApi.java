package io.openaev.api.chaining;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.api.chaining.dto.ScopeAssetOutput;
import io.openaev.api.chaining.dto.ScopeTeamOutput;
import io.openaev.api.chaining.dto.WorkflowConfigurationInput;
import io.openaev.api.chaining.dto.WorkflowConfigurationOutput;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.PreviewFeatureService;
import io.openaev.service.chaining.ScopeService;
import io.openaev.service.chaining.WorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(WorkflowApi.TENANT_WORKFLOW_URI)
@Tag(name = "Workflow API", description = "Operations related to Workflow")
public class WorkflowApi extends RestBehavior {

  public static final String TENANT_WORKFLOW_URI = TENANT_PREFIX + "/workflows";

  private final WorkflowService workflowService;
  private final ScopeService scopeService;
  private final PreviewFeatureService previewFeatureService;

  // -- READ --

  @Operation(
      summary = "Fetch workflow configuration for a workflow",
      description =
          "Fetch the workflow configuration for a given workflow, including time-out, rate-limit, safe-mode and scope rules.")
  @Transactional
  @ApiResponse(responseCode = "200", description = "Workflow configuration retrieved successfully")
  @ApiResponse(
      responseCode = "404",
      description =
          "Workflow configuration not found for the specified workflow, or the INJECT_CHAINING feature is disabled")
  @ApiResponse(responseCode = "500", description = "Unexpected server error")
  @GetMapping("/{workflowId}/configuration")
  @AccessControl(
      resourceId = "#workflowId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.WORKFLOW,
      isEnterpriseEdition = true)
  @LogExecutionTime
  public WorkflowConfigurationOutput getWorkflowConfiguration(
      @PathVariable @NotBlank final String workflowId) {
    checkWorkflowFeatureEnabled();
    return WorkflowConfigurationMapper.toOutput(
        workflowService.getWorkflowConfiguration(workflowId));
  }

  @Operation(
      summary = "Get the computed list of valid (allowed) assets for a workflow",
      description =
          "Returns assets that are in scope after applying allowlist/denylist rules. "
              + "Assets from allowlisted groups are included, then any individually or group-denylisted assets are removed.")
  @Transactional
  @ApiResponse(responseCode = "200", description = "Valid assets retrieved successfully")
  @ApiResponse(
      responseCode = "404",
      description = "Workflow not found or the INJECT_CHAINING feature is disabled")
  @GetMapping("/{workflowId}/valid-assets")
  @AccessControl(
      actionPerformed = Action.READ,
      resourceType = ResourceType.WORKFLOW,
      isEnterpriseEdition = true)
  @LogExecutionTime
  public List<ScopeAssetOutput> getValidAssets(@PathVariable @NotBlank final String workflowId) {
    checkWorkflowFeatureEnabled();
    return scopeService.getValidAssets(workflowId).stream()
        .map(ScopeAssetMapper::toOutput)
        .toList();
  }

  @Operation(
      summary = "Get the computed list of valid (allowed) teams for a workflow",
      description =
          "Returns teams that are in scope after applying allowlist/denylist rules on TEAM scope"
              + " rules. Mirrors valid-assets for the audience (team) axis.")
  @Transactional
  @ApiResponse(responseCode = "200", description = "Valid teams retrieved successfully")
  @ApiResponse(
      responseCode = "404",
      description = "Workflow not found or the INJECT_CHAINING feature is disabled")
  @GetMapping("/{workflowId}/valid-teams")
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.WORKFLOW)
  @LogExecutionTime
  public List<ScopeTeamOutput> getValidTeams(@PathVariable @NotBlank final String workflowId) {
    checkWorkflowFeatureEnabled();
    return scopeService.getValidTeams(workflowId).stream().map(ScopeTeamMapper::toOutput).toList();
  }

  // -- UPDATE --
  @Operation(
      summary = "Update workflow configuration for a workflow",
      description = "Update workflow configuration for a given workflow.")
  @ApiResponse(responseCode = "200", description = "Workflow configuration updated successfully")
  @ApiResponse(
      responseCode = "404",
      description =
          "Workflow or workflow configuration not found, or the INJECT_CHAINING feature is disabled")
  @ApiResponse(responseCode = "500", description = "Unexpected server error")
  @PutMapping("/{workflowId}/configuration")
  @Transactional
  @AccessControl(
      resourceId = "#workflowId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.WORKFLOW,
      isEnterpriseEdition = true)
  public WorkflowConfigurationOutput updateWorkflowConfiguration(
      @PathVariable @NotBlank final String workflowId,
      @Valid @RequestBody final WorkflowConfigurationInput input) {
    checkWorkflowFeatureEnabled();
    return WorkflowConfigurationMapper.toOutput(
        workflowService.updateWorkflowConfiguration(workflowId, input));
  }

  // -- Helpers --

  /**
   * Throws {@link ElementNotFoundException} (HTTP 404) when the {@code INJECT_CHAINING} feature
   * flag is disabled, preventing access to all workflow endpoints.
   */
  private void checkWorkflowFeatureEnabled() {
    if (!previewFeatureService.isFeatureEnabled(PreviewFeature.INJECT_CHAINING)) {
      throw new ElementNotFoundException("INJECT_CHAINING feature is not enabled");
    }
  }
}
