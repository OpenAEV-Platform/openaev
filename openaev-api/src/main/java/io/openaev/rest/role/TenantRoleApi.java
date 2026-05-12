package io.openaev.rest.role;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.role.form.RoleInput;
import io.openaev.rest.role.form.RoleMapper;
import io.openaev.rest.role.form.RoleOutput;
import io.openaev.service.RoleService;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Tag(name = "Roles management", description = "Endpoints to manage Roles.")
public class TenantRoleApi extends RestBehavior {

  public static final String ROLE_URI = "/api/roles";
  private static final String TENANT_ROLE_URI = TENANT_PREFIX + "/roles";

  private final RoleService roleService;
  private final RoleMapper roleMapper;

  // -- CREATE --

  @LogExecutionTime
  @PostMapping({ROLE_URI, TENANT_ROLE_URI})
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.GROUP_ROLE)
  @Transactional(rollbackFor = Exception.class)
  @Operation(summary = "Create Role")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Role created"),
        @ApiResponse(responseCode = "409", description = "Role already exists")
      })
  public RoleOutput createRole(@Valid @RequestBody final RoleInput input) {
    return roleMapper.toRoleOutput(
        roleService.createRole(input.getName(), input.getDescription(), input.getCapabilities()));
  }

  // -- READ --

  @LogExecutionTime
  @GetMapping({ROLE_URI + "/{roleId}", TENANT_ROLE_URI + "/{roleId}"})
  @AccessControl(
      resourceId = "#roleId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.GROUP_ROLE)
  @Operation(description = "Get Role by Id", summary = "Get Role")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Role found"),
        @ApiResponse(responseCode = "404", description = "Role not found")
      })
  public RoleOutput findRole(
      @PathVariable @NotBlank @Schema(description = "ID of the role") final String roleId) {
    return roleMapper.toRoleOutput(roleService.findByIdInTenant(roleId));
  }

  @LogExecutionTime
  @GetMapping({ROLE_URI, TENANT_ROLE_URI})
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.GROUP_ROLE)
  @Operation(description = "Get All Roles", summary = "Get Roles")
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of all Roles")})
  public List<RoleOutput> roles() {
    return roleService.findAll(TenantContext.getCurrentTenant()).stream()
        .map(roleMapper::toRoleOutput)
        .toList();
  }

  @LogExecutionTime
  @PostMapping({ROLE_URI + "/search", TENANT_ROLE_URI + "/search"})
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.GROUP_ROLE)
  @Operation(
      description = "Search Roles corresponding to search criteria",
      summary = "Search Roles")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "The list of all Roles corresponding to the search criteria")
      })
  public Page<RoleOutput> searchRoles(
      @RequestBody @Valid SearchPaginationInput searchPaginationInput) {
    return roleService
        .searchRole(searchPaginationInput, TenantContext.getCurrentTenant())
        .map(roleMapper::toRoleOutput);
  }

  // -- UPDATE --

  @LogExecutionTime
  @PutMapping({ROLE_URI + "/{roleId}", TENANT_ROLE_URI + "/{roleId}"})
  @AccessControl(
      resourceId = "#roleId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.GROUP_ROLE)
  @Transactional(rollbackFor = Exception.class)
  @Operation(summary = "Update Role", description = "Role needs to exists")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Role updated"),
        @ApiResponse(responseCode = "404", description = "Role not found")
      })
  public RoleOutput updateRole(
      @PathVariable @NotBlank @Schema(description = "ID of the role") final String roleId,
      @Valid @RequestBody final RoleInput input) {
    return roleMapper.toRoleOutput(
        roleService.updateRole(
            roleId, input.getName(), input.getDescription(), input.getCapabilities()));
  }

  // -- DELETE --

  @LogExecutionTime
  @DeleteMapping({ROLE_URI + "/{roleId}", TENANT_ROLE_URI + "/{roleId}"})
  @AccessControl(
      resourceId = "#roleId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.GROUP_ROLE)
  @Transactional(rollbackFor = Exception.class)
  @Operation(summary = "Delete Role", description = "Role needs to exists")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Role deleted"),
        @ApiResponse(responseCode = "404", description = "Role not found")
      })
  public void deleteRole(
      @PathVariable @NotBlank @Schema(description = "ID of the role") final String roleId) {
    roleService.deleteRole(roleId);
  }
}
