package io.openaev.api.groups;

import static io.openaev.api.groups.TenantGroupApi.GROUP_URI;
import static io.openaev.api.groups.TenantGroupApi.TENANT_GROUP_URI;
import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.api.groups.dto.GroupUpdateMarkingsInput;
import io.openaev.api.groups.dto.TenantGroupCreateInput;
import io.openaev.api.groups.dto.TenantGroupMarkingsOutput;
import io.openaev.config.TenantWriteScopeResolver;
import io.openaev.context.TxCtx;
import io.openaev.database.model.*;
import io.openaev.rest.group.form.GroupGrantInput;
import io.openaev.rest.group.form.GroupUpdateRolesInput;
import io.openaev.rest.group.form.GroupUpdateUsersInput;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.service.TenantGroupService;
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
  public Group createGroup(TxCtx ctx, @Valid @RequestBody TenantGroupCreateInput input) {
    return tenantGroupService.createGroup(input);
  }

  @PostMapping("/{groupId}/grants")
  @Transactional
  @AccessControl(
      resourceId = "#groupId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.USER_GROUP)
  public Group groupGrant(
      TxCtx ctx, @PathVariable String groupId, @Valid @RequestBody GroupGrantInput input) {
    return tenantGroupService.addGrant(groupId, input);
  }

  // -- READ --

  @GetMapping("/{groupId}")
  @Transactional
  @AccessControl(
      resourceId = "#groupId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.USER_GROUP)
  public Group group(TxCtx ctx, @PathVariable String groupId) {
    return tenantGroupService.findByIdInTenant(groupId);
  }

  @LogExecutionTime
  @PostMapping("/search")
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.USER_GROUP)
  public Page<Group> groups(
      TxCtx ctx, @RequestBody @Valid final SearchPaginationInput searchPaginationInput) {
    return tenantGroupService.search(searchPaginationInput);
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
      TxCtx ctx, @PathVariable String groupId, @Valid @RequestBody GroupUpdateUsersInput input) {
    return tenantGroupService.updateGroupUsers(groupId, input);
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
      TxCtx ctx, @PathVariable String groupId, @Valid @RequestBody GroupUpdateRolesInput input) {
    return tenantGroupService.updateGroupRoles(groupId, input);
  }

  @LogExecutionTime
  @PutMapping("/{groupId}/markings")
  @Transactional
  @AccessControl(
      resourceId = "#groupId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.USER_GROUP)
  @Operation(
      summary = "Replace the markings a group grants its members",
      description =
          "Replaces the whole set: an empty list revokes every grant. Requires ASSIGN_MARKING to"
              + " add any marking the group does not already grant, and/or"
              + " DELETE_MARKING_ASSIGNMENT to remove any it currently does - whichever of the two"
              + " this request's payload actually does, checked independently and in addition to"
              + " the group's own WRITE control above. A caller may only assign markings they hold"
              + " themselves, and only markings defined in their own tenant. Every member's cached"
              + " clearance is evicted, so the change takes effect on their next request.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Group updated"),
        @ApiResponse(
            responseCode = "403",
            description =
                "Missing ASSIGN_MARKING/DELETE_MARKING_ASSIGNMENT for what this payload changes,"
                    + " or assigning a marking the caller lacks"),
        @ApiResponse(responseCode = "404", description = "Group or marking not found")
      })
  // The @AccessControl WRITE check above answers "may you change what this group grants" (same
  // gate as updateGroupUsers/updateGroupRoles); it says nothing about markings specifically. Which
  // of ASSIGN_MARKING / DELETE_MARKING_ASSIGNMENT this request additionally needs depends on the
  // diff between the payload and the group's current markings, so that check is done in
  // TenantGroupService.updateGroupMarkings, which already loads the group's current state - see
  // its doc. Per-definition escalation (you may not grant a marking you do not hold yourself) is a
  // separate, narrower check also enforced there, via MarkingEscalationValidator.
  public TenantGroupMarkingsOutput updateGroupMarkings(
      TxCtx ctx, @PathVariable String groupId, @Valid @RequestBody GroupUpdateMarkingsInput input) {
    // Tenant resolved here and passed down, per the multi-tenancy convention: the service never
    // touches TenantContext. It is the tenant whose clearance the caller is checked against.
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
      TxCtx ctx, @PathVariable String groupId, @Valid @RequestBody TenantGroupCreateInput input) {
    return tenantGroupService.updateGroup(groupId, input);
  }

  // -- DELETE --

  @LogExecutionTime
  @Transactional
  @DeleteMapping("/{groupId}/grants/{grantId}")
  @AccessControl(
      resourceId = "#groupId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.USER_GROUP)
  public Group deleteGrant(TxCtx ctx, @PathVariable String groupId, @PathVariable String grantId) {
    return tenantGroupService.removeGrant(groupId, grantId);
  }

  @DeleteMapping("/{groupId}")
  @Transactional
  @AccessControl(
      resourceId = "#groupId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.USER_GROUP)
  public void delete(TxCtx ctx, @PathVariable String groupId) {
    tenantGroupService.delete(groupId);
  }
}
