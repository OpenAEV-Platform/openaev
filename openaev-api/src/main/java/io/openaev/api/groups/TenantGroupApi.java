package io.openaev.api.groups;

import static io.openaev.api.groups.TenantGroupApi.GROUP_URI;
import static io.openaev.api.groups.TenantGroupApi.TENANT_GROUP_URI;
import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.api.groups.dto.GroupUpdateMarkingsInput;
import io.openaev.api.groups.dto.TenantGroupCreateInput;
import io.openaev.api.groups.dto.TenantGroupMarkingsOutput;
import io.openaev.config.RequireTenantSelector;
import io.openaev.config.TenantWriteScopeResolver;
import io.openaev.context.TxCtx;
import io.openaev.database.model.*;
import io.openaev.rest.group.form.GroupGrantInput;
import io.openaev.rest.group.form.GroupUpdateRolesInput;
import io.openaev.rest.group.form.GroupUpdateUsersInput;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.service.TenantGroupService;
import io.openaev.utils.TxCtxScopeUtils;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({GROUP_URI, TENANT_GROUP_URI})
@RequiredArgsConstructor
public class TenantGroupApi extends RestBehavior {

  public static final String GROUP_URI = "/api/groups";
  public static final String TENANT_GROUP_URI = TENANT_PREFIX + "/groups";

  private final TenantGroupService tenantGroupService;
  private final TenantWriteScopeResolver writeScopeResolver;

  // -- CREATE --

  @Operation(summary = "Create a tenant group")
  @PostMapping
  @Transactional
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.USER_GROUP)
  public Group createGroup(
      @RequireTenantSelector TxCtx ctx, @Valid @RequestBody TenantGroupCreateInput input) {
    return tenantGroupService.createGroup(writeScopeResolver.tenantForWrite(ctx, null), input);
  }

  @PostMapping("/{groupId}/grants")
  @Transactional
  @AccessControl(
      resourceId = "#groupId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.USER_GROUP)
  public Group groupGrant(
      @RequireTenantSelector TxCtx ctx,
      @PathVariable String groupId,
      @Valid @RequestBody GroupGrantInput input) {
    return tenantGroupService.addGrant(
        writeScopeResolver.tenantForWrite(ctx, null), groupId, input);
  }

  // -- READ --

  @GetMapping("/{groupId}")
  @Transactional
  @AccessControl(
      resourceId = "#groupId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.USER_GROUP)
  public Group group(@RequireTenantSelector TxCtx ctx, @PathVariable String groupId) {
    return tenantGroupService.findByIdInTenants(groupId, TxCtxScopeUtils.tenantIdsFromHTTPCtx(ctx));
  }

  @LogExecutionTime
  @PostMapping("/search")
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.USER_GROUP)
  public Page<Group> groups(
      @RequireTenantSelector TxCtx ctx,
      @RequestBody @Valid final SearchPaginationInput searchPaginationInput) {
    return tenantGroupService.search(
        searchPaginationInput, TxCtxScopeUtils.tenantIdsFromHTTPCtx(ctx));
  }

  // -- UPDATE --

  @LogExecutionTime
  @PutMapping("/{groupId}/users")
  @Transactional
  @AccessControl(
      resourceId = "#groupId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.USER_GROUP)
  public Group updateGroupUsers(
      @RequireTenantSelector TxCtx ctx,
      @PathVariable String groupId,
      @Valid @RequestBody GroupUpdateUsersInput input) {
    return tenantGroupService.updateGroupUsers(
        writeScopeResolver.tenantForWrite(ctx, null), groupId, input);
  }

  @LogExecutionTime
  @PutMapping("/{groupId}/roles")
  @Transactional
  @AccessControl(
      resourceId = "#groupId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.USER_GROUP)
  @Operation(
      description = "Update roles associated to a group",
      summary = "Update roles associated to a group")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Group updated"),
        @ApiResponse(responseCode = "404", description = "Role or Group not found")
      })
  public Group updateGroupRoles(
      @RequireTenantSelector TxCtx ctx,
      @PathVariable String groupId,
      @Valid @RequestBody GroupUpdateRolesInput input) {
    return tenantGroupService.updateGroupRoles(
        writeScopeResolver.tenantForWrite(ctx, null), groupId, input);
  }

  @LogExecutionTime
  @PutMapping("/{groupId}/markings")
  @Transactional
  @AccessControl(
      resourceId = "#groupId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.USER_GROUP)
  @Operation(summary = "Replace the whole set of markings a group grants its members")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Group updated"),
        @ApiResponse(
            responseCode = "403",
            description =
                "Missing MANAGE_MARKING_ASSIGNMENT, or assigning a marking the caller lacks"),
        @ApiResponse(responseCode = "404", description = "Group or marking not found")
      })
  // MANAGE_MARKING_ASSIGNMENT and escalation are checked in TenantGroupService instead - see its
  // doc.
  public TenantGroupMarkingsOutput updateGroupMarkings(
      @RequireTenantSelector TxCtx ctx,
      @PathVariable String groupId,
      @Valid @RequestBody GroupUpdateMarkingsInput input) {
    // Tenant resolved here and passed down - the service never touches TenantContext directly.
    return TenantGroupMarkingsOutput.from(
        tenantGroupService.updateGroupMarkings(
            writeScopeResolver.tenantForWrite(ctx, null), groupId, input));
  }

  @LogExecutionTime
  @PutMapping("/{groupId}/information")
  @Transactional
  @AccessControl(
      resourceId = "#groupId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.USER_GROUP)
  public Group updateGroupInformation(
      @RequireTenantSelector TxCtx ctx,
      @PathVariable String groupId,
      @Valid @RequestBody TenantGroupCreateInput input) {
    return tenantGroupService.updateGroup(
        writeScopeResolver.tenantForWrite(ctx, null), groupId, input);
  }

  // -- DELETE --

  @LogExecutionTime
  @Transactional
  @DeleteMapping("/{groupId}/grants/{grantId}")
  @AccessControl(
      resourceId = "#groupId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.USER_GROUP)
  public Group deleteGrant(
      @RequireTenantSelector TxCtx ctx,
      @PathVariable String groupId,
      @PathVariable String grantId) {
    return tenantGroupService.removeGrant(
        writeScopeResolver.tenantForWrite(ctx, null), groupId, grantId);
  }

  @DeleteMapping("/{groupId}")
  @Transactional
  @AccessControl(
      resourceId = "#groupId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.USER_GROUP)
  public void delete(@RequireTenantSelector TxCtx ctx, @PathVariable String groupId) {
    tenantGroupService.delete(writeScopeResolver.tenantForWrite(ctx, null), groupId);
  }
}
