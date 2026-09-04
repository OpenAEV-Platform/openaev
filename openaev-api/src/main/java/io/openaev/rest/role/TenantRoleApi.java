package io.openaev.rest.role;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.rest.role.form.RoleMapper.toOutput;

import io.openaev.aop.AccessControl;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.role.form.RoleInput;
import io.openaev.rest.role.form.RoleMapper;
import io.openaev.rest.role.form.RoleOutput;
import io.openaev.service.TenantRoleService;
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

  private final TenantRoleService tenantRoleService;

  // -- CREATE --

  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.GROUP_ROLE)
  @PostMapping({ROLE_URI, TENANT_ROLE_URI})
  @Transactional(rollbackFor = Exception.class)
  @Operation(summary = "Create Role")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Role created"),
        @ApiResponse(responseCode = "409", description = "Role already exists")
      })
  public RoleOutput create(@Valid @RequestBody final RoleInput input) {
    return toOutput(
        tenantRoleService.createRole(input.name(), input.description(), input.capabilities()));
  }

  // -- READ --

  @AccessControl(
      resourceId = "#roleId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.GROUP_ROLE)
  @GetMapping({ROLE_URI + "/{roleId}", TENANT_ROLE_URI + "/{roleId}"})
  @Transactional
  @Operation(description = "Get Role by Id", summary = "Get Role")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Role found"),
        @ApiResponse(responseCode = "404", description = "Role not found")
      })
  public RoleOutput findRole(
      @PathVariable @NotBlank @Schema(description = "ID of the role") final String roleId) {
    return toOutput(tenantRoleService.findByIdInTenant(roleId));
  }

  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.GROUP_ROLE)
  @GetMapping({ROLE_URI, TENANT_ROLE_URI})
  @Transactional
  @Operation(description = "Get All Roles", summary = "Get Roles")
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of all Roles")})
  public List<RoleOutput> roles() {
    return tenantRoleService.findAll(TenantContext.getCurrentTenant()).stream()
        .map(RoleMapper::toOutput)
        .toList();
  }

  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.GROUP_ROLE)
  @PostMapping({ROLE_URI + "/search", TENANT_ROLE_URI + "/search"})
  @Transactional
  @Operation(
      description = "Search Roles corresponding to search criteria",
      summary = "Search Roles")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "The list of all Roles corresponding to the search criteria")
      })
  public Page<RoleOutput> search(@RequestBody @Valid SearchPaginationInput searchPaginationInput) {
    return tenantRoleService
        .search(searchPaginationInput, TenantContext.getCurrentTenant())
        .map(RoleMapper::toOutput);
  }

  // -- UPDATE --

  @AccessControl(
      resourceId = "#roleId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.GROUP_ROLE)
  @PutMapping({ROLE_URI + "/{roleId}", TENANT_ROLE_URI + "/{roleId}"})
  @Transactional(rollbackFor = Exception.class)
  @Operation(summary = "Update Role", description = "Role needs to exists")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Role updated"),
        @ApiResponse(responseCode = "404", description = "Role not found")
      })
  public RoleOutput update(
      @PathVariable @NotBlank @Schema(description = "ID of the role") final String roleId,
      @Valid @RequestBody final RoleInput input) {
    return toOutput(
        tenantRoleService.updateRole(
            roleId, input.name(), input.description(), input.capabilities()));
  }

  // -- DELETE --

  @AccessControl(
      resourceId = "#roleId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.GROUP_ROLE)
  @DeleteMapping({ROLE_URI + "/{roleId}", TENANT_ROLE_URI + "/{roleId}"})
  @Transactional(rollbackFor = Exception.class)
  @Operation(summary = "Delete Role", description = "Role needs to exists")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Role deleted"),
        @ApiResponse(responseCode = "404", description = "Role not found")
      })
  public void delete(
      @PathVariable @NotBlank @Schema(description = "ID of the role") final String roleId) {
    tenantRoleService.delete(roleId);
  }
}
