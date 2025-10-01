package io.openaev.rest.role;

import io.openaev.aop.LogExecutionTime;
import io.openaev.aop.RBAC;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.exception.ElementNotFoundException;
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
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Roles management", description = "Endpoints to manage Roles.")
public class RoleApi {

  public static final String ROLE_URI = "/api/roles";

  private final RoleService roleService;
  private final RoleMapper roleMapper;

  public RoleApi(RoleService roleService, RoleMapper roleMapper) {
    super();
    this.roleService = roleService;
    this.roleMapper = roleMapper;
  }

  @LogExecutionTime
  @GetMapping(RoleApi.ROLE_URI + "/{roleId}")
  @RBAC(
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
    return roleService
        .findById(roleId)
        .map(roleMapper::toRoleOutput)
        .orElseThrow(() -> new ElementNotFoundException("Role not found with id: " + roleId));
  }

  @LogExecutionTime
  @GetMapping(RoleApi.ROLE_URI)
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.GROUP_ROLE)
  @Operation(description = "Get All Roles", summary = "Get Roles")
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of all Roles")})
  public List<RoleOutput> roles() {
    return roleService.findAll().stream().map(roleMapper::toRoleOutput).toList();
  }

  @LogExecutionTime
  @DeleteMapping(RoleApi.ROLE_URI + "/{roleId}")
  @RBAC(
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

  @LogExecutionTime
  @PostMapping(RoleApi.ROLE_URI)
  @RBAC(actionPerformed = Action.CREATE, resourceType = ResourceType.GROUP_ROLE)
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

  @LogExecutionTime
  @PutMapping(RoleApi.ROLE_URI + "/{roleId}")
  @RBAC(
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

  @LogExecutionTime
  @PostMapping(RoleApi.ROLE_URI + "/search")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.GROUP_ROLE)
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
    return roleService.searchRole(searchPaginationInput).map(roleMapper::toRoleOutput);
  }
}
