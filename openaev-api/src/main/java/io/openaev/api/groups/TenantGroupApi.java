package io.openaev.api.groups;

import static io.openaev.api.groups.TenantGroupApi.GROUP_URI;
import static io.openaev.api.groups.TenantGroupApi.TENANT_GROUP_URI;
import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.api.groups.dto.TenantGroupCreateInput;
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
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({GROUP_URI, TENANT_GROUP_URI})
@RequiredArgsConstructor
public class TenantGroupApi extends RestBehavior {

  public static final String GROUP_URI = "/api/groups";
  public static final String TENANT_GROUP_URI = TENANT_PREFIX + "/groups";

  private final TenantGroupService tenantGroupService;

  // -- CREATE --

  @Operation(summary = "Create a tenant group")
  @PostMapping
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.USER_GROUP)
  public Group createGroup(@Valid @RequestBody TenantGroupCreateInput input) {
    return tenantGroupService.createGroup(input);
  }

  @PostMapping("/{groupId}/grants")
  @AccessControl(
      resourceId = "#groupId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.USER_GROUP)
  public Group groupGrant(@PathVariable String groupId, @Valid @RequestBody GroupGrantInput input) {
    return tenantGroupService.addGrant(groupId, input);
  }

  // -- READ --

  @GetMapping("/{groupId}")
  @AccessControl(
      resourceId = "#groupId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.USER_GROUP)
  public Group group(@PathVariable String groupId) {
    return tenantGroupService.findByIdInTenant(groupId);
  }

  @LogExecutionTime
  @PostMapping("/search")
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.USER_GROUP)
  public Page<Group> groups(@RequestBody @Valid final SearchPaginationInput searchPaginationInput) {
    return tenantGroupService.search(searchPaginationInput);
  }

  // -- UPDATE --

  @LogExecutionTime
  @PutMapping("/{groupId}/users")
  @AccessControl(
      resourceId = "#groupId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.USER_GROUP)
  public Group updateGroupUsers(
      @PathVariable String groupId, @Valid @RequestBody GroupUpdateUsersInput input) {
    return tenantGroupService.updateUsers(groupId, input);
  }

  @LogExecutionTime
  @PutMapping("/{groupId}/roles")
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
  @PutMapping("/{groupId}/information")
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
  @DeleteMapping("/{groupId}/grants/{grantId}")
  @AccessControl(
      resourceId = "#groupId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.USER_GROUP)
  public Group deleteGrant(@PathVariable String groupId, @PathVariable String grantId) {
    return tenantGroupService.removeGrant(groupId, grantId);
  }

  @DeleteMapping("/{groupId}")
  @AccessControl(
      resourceId = "#groupId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.USER_GROUP)
  public void delete(@PathVariable String groupId) {
    tenantGroupService.delete(groupId);
  }
}
