package io.openaev.api.groups;

import static io.openaev.api.groups.TenantGroupApi.GROUP_URI;
import static io.openaev.api.groups.TenantGroupApi.TENANT_GROUP_URI;
import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.api.groups.dto.GroupUpdateMarkingsInput;
import io.openaev.api.groups.dto.TenantGroupCreateInput;
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
  public Group createGroup(@Valid @RequestBody TenantGroupCreateInput input) {
    return tenantGroupService.createGroup(input);
  }

  @PostMapping("/{groupId}/grants")
  @Transactional
  @AccessControl(
      resourceId = "#groupId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.USER_GROUP)
  public Group groupGrant(@PathVariable String groupId, @Valid @RequestBody GroupGrantInput input) {
    return tenantGroupService.addGrant(groupId, input);
  }

  // -- READ --

  @GetMapping("/{groupId}")
  @Transactional
  @AccessControl(
      resourceId = "#groupId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.USER_GROUP)
  public Group group(@PathVariable String groupId) {
    return tenantGroupService.findByIdInTenant(groupId);
  }

  @LogExecutionTime
  @PostMapping("/search")
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.USER_GROUP)
  public Page<Group> groups(@RequestBody @Valid final SearchPaginationInput searchPaginationInput) {
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
      @PathVariable String groupId, @Valid @RequestBody GroupUpdateUsersInput input) {
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
      @PathVariable String groupId, @Valid @RequestBody GroupUpdateRolesInput input) {
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
          "Replaces the whole set: an empty list revokes every grant. A caller may only assign"
              + " markings they hold themselves, and only markings defined in their own tenant."
              + " Every member's cached clearance is evicted, so the change takes effect on their"
              + " next request.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Group updated"),
        @ApiResponse(responseCode = "403", description = "Assigning a marking the caller lacks"),
        @ApiResponse(responseCode = "404", description = "Group or marking not found")
      })
  // TODO: replace with the "Assign marking" capability chain (design Q8) once Task 1 lands. The
  // group's own WRITE control is the honest interim: it is what already governs who may change what
  // a group grants, and the marking PoC is deliberately capability-free (design Q12).
  public Group updateGroupMarkings(
      TxCtx ctx, @PathVariable String groupId, @Valid @RequestBody GroupUpdateMarkingsInput input) {
    // Tenant resolved here and passed down, per the multi-tenancy convention: the service never
    // touches TenantContext. It is the tenant whose clearance the caller is checked against.
    return tenantGroupService.updateGroupMarkings(
        writeScopeResolver.tenantForWrite(ctx, null), groupId, input);
  }

  @LogExecutionTime
  @PutMapping("/{groupId}/information")
  @Transactional
  @AccessControl(
      resourceId = "#groupId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.USER_GROUP)
  public Group updateGroupInformation(
      @PathVariable String groupId, @Valid @RequestBody TenantGroupCreateInput input) {
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
  public Group deleteGrant(@PathVariable String groupId, @PathVariable String grantId) {
    return tenantGroupService.removeGrant(groupId, grantId);
  }

  @DeleteMapping("/{groupId}")
  @Transactional
  @AccessControl(
      resourceId = "#groupId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.USER_GROUP)
  public void delete(@PathVariable String groupId) {
    tenantGroupService.delete(groupId);
  }
}
