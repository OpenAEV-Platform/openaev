package io.openaev.api.chaining;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.api.chaining.dto.ScopeAssetOutput;
import io.openaev.api.chaining.dto.ScopeTeamOutput;
import io.openaev.api.chaining.dto.WorkflowConfigurationInput;
import io.openaev.api.chaining.dto.WorkflowConfigurationOutput;
import io.openaev.context.TxCtx;
import io.openaev.api.chaining.dto.WorkflowInjectorContractOutput;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.asset.endpoint.form.EndpointOutput;
import io.openaev.rest.asset_group.form.AssetGroupOutput;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.injector_contract.InjectorContractService;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.PreviewFeatureService;
import io.openaev.service.chaining.ScopeService;
import io.openaev.service.chaining.WorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
  private final WorkflowConfigurationMapper workflowConfigurationMapper;
  private final InjectorContractService injectorContractService;

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
      TxCtx ctx, @PathVariable @NotBlank final String workflowId) {
    checkWorkflowFeatureEnabled();
    return workflowConfigurationMapper.toOutput(
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
      resourceId = "#workflowId",
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
  @AccessControl(
      resourceId = "#workflowId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.WORKFLOW,
      isEnterpriseEdition = true)
  @LogExecutionTime
  public List<ScopeTeamOutput> getValidTeams(@PathVariable @NotBlank final String workflowId) {
    checkWorkflowFeatureEnabled();
    return scopeService.getValidTeams(workflowId).stream().map(ScopeTeamMapper::toOutput).toList();
  }

  @Operation(
      summary = "Get the endpoints referenced by the scope rules of a workflow",
      description =
          "Returns the endpoints explicitly listed in the workflow scope rules, allowlist and "
              + "denylist alike, so the scope screen can label every rule. Workflow-scoped "
              + "counterpart of GET /api/endpoints: a user granted on the parent simulation or "
              + "scenario can read them without the global asset capabilities.")
  @Transactional(readOnly = true)
  @ApiResponse(responseCode = "200", description = "Scope endpoints retrieved successfully")
  @ApiResponse(
      responseCode = "404",
      description = "Workflow not found or the INJECT_CHAINING feature is disabled")
  @GetMapping("/{workflowId}/scope/endpoints")
  @AccessControl(
      resourceId = "#workflowId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.WORKFLOW,
      isEnterpriseEdition = true)
  @LogExecutionTime
  // ctx is unused directly: the aspect reads it to scope this transaction against the v2-active
  // executors table (each endpoint's agents eager-load their executor).
  public List<EndpointOutput> getScopeEndpoints(
      TxCtx ctx, @PathVariable @NotBlank final String workflowId) {
    checkWorkflowFeatureEnabled();
    return scopeService.getScopeEndpoints(workflowId);
  }

  @Operation(
      summary = "Get the endpoints referenced by the scope rules of a workflow, by IDs",
      description =
          "Same as GET /{workflowId}/scope/endpoints, restricted to the requested IDs. IDs that "
              + "are not referenced by the workflow scope rules are ignored.")
  @Transactional(readOnly = true)
  @ApiResponse(responseCode = "200", description = "Scope endpoints retrieved successfully")
  @ApiResponse(
      responseCode = "404",
      description = "Workflow not found or the INJECT_CHAINING feature is disabled")
  @PostMapping("/{workflowId}/scope/endpoints/find")
  @AccessControl(
      resourceId = "#workflowId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.WORKFLOW,
      isEnterpriseEdition = true)
  @LogExecutionTime
  // ctx is unused directly: the aspect reads it to scope this transaction against the v2-active
  // executors table (each endpoint's agents eager-load their executor).
  public List<EndpointOutput> findScopeEndpoints(
      TxCtx ctx,
      @PathVariable @NotBlank final String workflowId,
      @RequestBody @Valid @NotNull final List<String> endpointIds) {
    checkWorkflowFeatureEnabled();
    return scopeService.getScopeEndpointsByIds(workflowId, endpointIds);
  }

  @Operation(
      summary = "Get the asset groups referenced by the scope rules of a workflow",
      description =
          "Returns the asset groups explicitly listed in the workflow scope rules, allowlist and "
              + "denylist alike. Workflow-scoped counterpart of GET /api/asset_groups.")
  @Transactional(readOnly = true)
  @ApiResponse(responseCode = "200", description = "Scope asset groups retrieved successfully")
  @ApiResponse(
      responseCode = "404",
      description = "Workflow not found or the INJECT_CHAINING feature is disabled")
  @GetMapping("/{workflowId}/scope/asset-groups")
  @AccessControl(
      resourceId = "#workflowId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.WORKFLOW,
      isEnterpriseEdition = true)
  @LogExecutionTime
  public List<AssetGroupOutput> getScopeAssetGroups(
      @PathVariable @NotBlank final String workflowId) {
    checkWorkflowFeatureEnabled();
    return scopeService.getScopeAssetGroups(workflowId);
  }

  @Operation(
      summary = "Get the asset groups referenced by the scope rules of a workflow, by IDs",
      description =
          "Same as GET /{workflowId}/scope/asset-groups, restricted to the requested IDs. IDs that "
              + "are not referenced by the workflow scope rules are ignored.")
  @Transactional(readOnly = true)
  @ApiResponse(responseCode = "200", description = "Scope asset groups retrieved successfully")
  @ApiResponse(
      responseCode = "404",
      description = "Workflow not found or the INJECT_CHAINING feature is disabled")
  @PostMapping("/{workflowId}/scope/asset-groups/find")
  @AccessControl(
      resourceId = "#workflowId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.WORKFLOW,
      isEnterpriseEdition = true)
  @LogExecutionTime
  public List<AssetGroupOutput> findScopeAssetGroups(
      @PathVariable @NotBlank final String workflowId,
      @RequestBody @Valid @NotNull final List<String> assetGroupIds) {
    checkWorkflowFeatureEnabled();
    return scopeService.getScopeAssetGroupsByIds(workflowId, assetGroupIds);
  }

  @Operation(
      summary = "Get an injector contract used by a workflow",
      description =
          "Returns the injector contract only if it is referenced by one of the workflow's steps. "
              + "Chaining steps do not persist their inject before execution, so this is the only "
              + "way for a user granted on the parent simulation or scenario to read the contracts "
              + "displayed in the logic screen.")
  @Transactional(readOnly = true)
  @ApiResponse(responseCode = "200", description = "Injector contract retrieved successfully")
  @ApiResponse(
      responseCode = "404",
      description =
          "Workflow or injector contract not found, the contract is not used by the workflow, or "
              + "the INJECT_CHAINING feature is disabled")
  @GetMapping("/{workflowId}/injector_contracts/{injectorContractId}")
  @AccessControl(
      resourceId = "#workflowId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.WORKFLOW,
      isEnterpriseEdition = true)
  @LogExecutionTime
  public WorkflowInjectorContractOutput getWorkflowInjectorContract(
      @PathVariable @NotBlank final String workflowId,
      @PathVariable @NotBlank final String injectorContractId) {
    checkWorkflowFeatureEnabled();
    return WorkflowInjectorContractOutput.fromInjectorContract(
        injectorContractService.injectorContractForWorkflow(injectorContractId, workflowId));
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
      TxCtx ctx,
      @PathVariable @NotBlank final String workflowId,
      @Valid @RequestBody final WorkflowConfigurationInput input) {
    checkWorkflowFeatureEnabled();
    return workflowConfigurationMapper.toOutput(
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
