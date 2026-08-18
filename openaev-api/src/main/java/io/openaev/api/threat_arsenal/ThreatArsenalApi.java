package io.openaev.api.threat_arsenal;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.api.asset.dto.SecurityPlatformSimpleOutput;
import io.openaev.api.threat_arsenal.dto.*;
import io.openaev.config.RequireTenantSelector;
import io.openaev.config.TenantWriteScopeResolver;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ChainingTypeRegistry;
import io.openaev.database.model.PrimitiveType;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.injector_contract.InjectorContractService;
import io.openaev.rest.injector_contract.input.InjectorContractSearchPaginationInput;
import io.openaev.rest.injector_contract.output.InjectorContractAuthorCountOutput;
import io.openaev.rest.injector_contract.output.InjectorContractBaseOutput;
import io.openaev.rest.injector_contract.output.InjectorContractDomainCountOutput;
import io.openaev.schema.model.PropertySchemaDTO;
import io.openaev.service.PreviewFeatureService;
import io.openaev.service.threat_arsenal.ThreatArsenalService;
import io.openaev.utils.mapper.SecurityPlatformMapper;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
public class ThreatArsenalApi {
  public static final String THREAT_ARSENAL_URL = "/api/threat_arsenals";
  public static final String TENANT_THREAT_ARSENAL_URL = TENANT_PREFIX + "/threat_arsenals";

  private final ThreatArsenalService threatArsenalService;
  private final PreviewFeatureService previewFeatureService;
  private final TenantWriteScopeResolver writeScopeResolver;

  // -- READ --

  @GetMapping({THREAT_ARSENAL_URL + "/{actionId}", TENANT_THREAT_ARSENAL_URL + "/{actionId}"})
  @Transactional
  @AccessControl(
      resourceId = "#actionId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.THREAT_ARSENAL)
  public ThreatArsenalActionFullOutput threatArsenal(@PathVariable String actionId) {
    return threatArsenalService.findById(actionId);
  }

  @Operation(
      summary = "Get all primitive chaining types",
      description = "Returns primitive types available for payload arguments.")
  @GetMapping({
    THREAT_ARSENAL_URL + "/argument-types/",
    TENANT_THREAT_ARSENAL_URL + "/argument-types/"
  })
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.THREAT_ARSENAL)
  public List<PrimitiveType> getArgumentTypes() {
    return resolveAvailableTypes();
  }

  @Operation(summary = "Get filterable property schemas for threat arsenal")
  @PostMapping({THREAT_ARSENAL_URL + "/schemas", TENANT_THREAT_ARSENAL_URL + "/schemas"})
  @Transactional
  @AccessControl(skipRBAC = true)
  public List<PropertySchemaDTO> schemas(
      @RequestParam final boolean filterableOnly,
      @RequestBody @Valid @NotNull List<String> filterNames)
      throws ClassNotFoundException {
    return threatArsenalService.getSchemas(filterableOnly, filterNames);
  }

  @PostMapping({
    THREAT_ARSENAL_URL + "/domain-counts",
    TENANT_THREAT_ARSENAL_URL + "/domain-counts"
  })
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.THREAT_ARSENAL)
  public List<InjectorContractDomainCountOutput> getDomainCounts(
      @RequestBody @Valid final SearchPaginationInput input) {
    return threatArsenalService.getDomainCounts(input);
  }

  @PostMapping({
    THREAT_ARSENAL_URL + "/author-counts",
    TENANT_THREAT_ARSENAL_URL + "/author-counts"
  })
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.THREAT_ARSENAL)
  public List<InjectorContractAuthorCountOutput> getAuthorCounts(
      @RequestBody @Valid final SearchPaginationInput input) {
    return threatArsenalService.getAuthorCounts(input);
  }

  @Operation(summary = "Platform and payload-status facet counts for the sidebar")
  @PostMapping({THREAT_ARSENAL_URL + "/facet-counts", TENANT_THREAT_ARSENAL_URL + "/facet-counts"})
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.THREAT_ARSENAL)
  public ThreatArsenalFacetCountsOutput getFacetCounts(
      @RequestBody @Valid final SearchPaginationInput input) {
    return threatArsenalService.getFacetCounts(input);
  }

  @Operation(summary = "Search threat arsenal")
  @ApiResponse(
      responseCode = "200",
      content =
          @Content(
              schema =
                  @Schema(
                      oneOf = {
                        ThreatArsenalAction.class,
                        ThreatArsenalActionWithContentOutput.class,
                      })))
  @PostMapping({THREAT_ARSENAL_URL + "/search", TENANT_THREAT_ARSENAL_URL + "/search"})
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.THREAT_ARSENAL)
  public Page<? extends InjectorContractBaseOutput> threatArsenals(
      @RequestBody @Valid final InjectorContractSearchPaginationInput input) {
    InjectorContractService.OutputMode outputMode =
        input.isIncludeContentDetails()
            ? InjectorContractService.OutputMode.THREAT_ARSENAL_CONTENT
            : InjectorContractService.OutputMode.THREAT_ARSENAL;
    return this.threatArsenalService.searchInjectorContracts(outputMode, input);
  }

  @Operation(
      summary =
          "Search non-tabletop threat arsenal actions (excludes email, SMS, challenges, media pressure)")
  @PostMapping({
    THREAT_ARSENAL_URL + "/search/non-tabletop",
    TENANT_THREAT_ARSENAL_URL + "/search/non-tabletop"
  })
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.THREAT_ARSENAL)
  public Page<? extends InjectorContractBaseOutput> threatArsenalsNonTabletop(
      @RequestBody @Valid final InjectorContractSearchPaginationInput input) {
    InjectorContractService.OutputMode outputMode =
        input.isIncludeContentDetails()
            ? InjectorContractService.OutputMode.THREAT_ARSENAL_CONTENT
            : InjectorContractService.OutputMode.THREAT_ARSENAL;
    return this.threatArsenalService.searchNonTabletopInjectorContracts(outputMode, input);
  }

  @GetMapping(TENANT_THREAT_ARSENAL_URL + "/{actionId}/security-platforms")
  @AccessControl(
      resourceId = "#actionId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.THREAT_ARSENAL)
  @Operation(summary = "Get the Security platforms used in a action remediation")
  @Transactional
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "The list of Security platforms used in a action remediation")
      })
  public List<SecurityPlatformSimpleOutput> securityPlatformsFromAction(
      @PathVariable String actionId) {
    return SecurityPlatformMapper.toSimpleOutputs(
        threatArsenalService.getSecurityPlatformsForActionRemediation(actionId));
  }

  private List<PrimitiveType> resolveAvailableTypes() {
    return ChainingTypeRegistry.getPrimitiveTypes();
  }

  // -- CREATE --

  @PostMapping({THREAT_ARSENAL_URL, TENANT_THREAT_ARSENAL_URL})
  @Transactional
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.THREAT_ARSENAL)
  public ThreatArsenalAction createAction(
      @RequireTenantSelector TxCtx ctx, @Valid @RequestBody ThreatArsenalActionCreateInput input) {
    writeScopeResolver.tenantForWrite(ctx, null);
    return threatArsenalService.create(input);
  }

  @PutMapping({THREAT_ARSENAL_URL + "/{actionId}", TENANT_THREAT_ARSENAL_URL + "/{actionId}"})
  @Transactional
  @AccessControl(
      resourceId = "#actionId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.THREAT_ARSENAL)
  public ThreatArsenalAction updateAction(
      @RequireTenantSelector TxCtx ctx,
      @NotBlank @PathVariable final String actionId,
      @Valid @RequestBody ThreatArsenalActionUpdateInput input) {
    writeScopeResolver.tenantForWrite(ctx, null);
    return threatArsenalService.update(actionId, input);
  }

  @PostMapping({
    THREAT_ARSENAL_URL + "/{actionId}/duplicate",
    TENANT_THREAT_ARSENAL_URL + "/{actionId}/duplicate"
  })
  @Transactional
  @AccessControl(
      resourceId = "#actionId",
      actionPerformed = Action.DUPLICATE,
      resourceType = ResourceType.THREAT_ARSENAL)
  public ThreatArsenalAction duplicateAction(
      @RequireTenantSelector TxCtx ctx, @NotBlank @PathVariable final String actionId) {
    writeScopeResolver.tenantForWrite(ctx, null);
    return threatArsenalService.duplicate(actionId);
  }

  @DeleteMapping(TENANT_THREAT_ARSENAL_URL + "/{actionId}")
  @Transactional
  @AccessControl(
      resourceId = "#actionId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.THREAT_ARSENAL)
  public void deleteAction(@PathVariable String actionId) {
    threatArsenalService.delete(actionId);
  }

  @Operation(summary = "Bulk delete threat arsenal actions")
  @PostMapping({THREAT_ARSENAL_URL + "/bulk-delete", TENANT_THREAT_ARSENAL_URL + "/bulk-delete"})
  // SUPPORTS (not REQUIRED) on purpose: the service resolves the scope in a short read transaction
  // and deletes chunk by chunk (each chunk in its own transaction), tracked as a massive operation.
  @Transactional(propagation = Propagation.SUPPORTS)
  @AccessControl(actionPerformed = Action.DELETE, resourceType = ResourceType.THREAT_ARSENAL)
  public ThreatArsenalBulkDeleteOutput bulkDeleteActions(
      @RequestBody @Valid final InjectorContractSearchPaginationInput input) {
    return ThreatArsenalBulkDeleteOutput.of(threatArsenalService.bulkDelete(input));
  }
}
