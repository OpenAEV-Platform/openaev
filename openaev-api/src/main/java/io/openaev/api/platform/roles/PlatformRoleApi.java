package io.openaev.api.platform.roles;

import static io.openaev.rest.role.form.RoleMapper.toOutput;

import io.openaev.aop.AccessControl;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.role.form.RoleInput;
import io.openaev.rest.role.form.RoleMapper;
import io.openaev.rest.role.form.RoleOutput;
import io.openaev.service.platform.roles.PlatformRoleService;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(PlatformRoleApi.PLATFORM_ROLES_URI)
@RequiredArgsConstructor
public class PlatformRoleApi extends RestBehavior {

  public static final String PLATFORM_ROLES_URI = "/api/platform-roles";
  private final PlatformRoleService platformRoleService;

  // -- CREATE --

  @AccessControl(
      actionPerformed = Action.CREATE,
      resourceType = ResourceType.PLATFORM_ROLE,
      isEnterpriseEdition = true)
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional(rollbackFor = Exception.class)
  @Operation(summary = "Create a platform role")
  public RoleOutput create(@Valid @RequestBody RoleInput input) {
    return toOutput(
        platformRoleService.createPlatformRole(
            input.name(), input.description(), input.capabilities()));
  }

  // -- READ --

  @AccessControl(
      resourceId = "#platformRoleId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.PLATFORM_ROLE,
      isEnterpriseEdition = true)
  @GetMapping("/{platformRoleId}")
  @Transactional
  @Operation(summary = "Get platform role by ID")
  public RoleOutput findById(@PathVariable String platformRoleId) {
    return toOutput(platformRoleService.findById(platformRoleId));
  }

  @AccessControl(
      actionPerformed = Action.SEARCH,
      resourceType = ResourceType.PLATFORM_ROLE,
      isEnterpriseEdition = true)
  @PostMapping("/search")
  @Transactional
  @Operation(summary = "Search platform roles")
  public Page<RoleOutput> search(
      @RequestBody @Valid final SearchPaginationInput searchPaginationInput) {
    return platformRoleService.search(searchPaginationInput).map(RoleMapper::toOutput);
  }

  @AccessControl(
      actionPerformed = Action.SEARCH,
      resourceType = ResourceType.PLATFORM_ROLE,
      isEnterpriseEdition = true)
  @PostMapping("/find")
  @Transactional
  @Operation(summary = "Find platform roles by IDs")
  public List<RoleOutput> find(@RequestBody @Valid final List<String> ids) {
    return platformRoleService.findByIds(ids).stream().map(RoleMapper::toOutput).toList();
  }

  // -- UPDATE --

  @AccessControl(
      resourceId = "#platformRoleId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.PLATFORM_ROLE,
      isEnterpriseEdition = true)
  @PutMapping("/{platformRoleId}")
  @Transactional(rollbackFor = Exception.class)
  @Operation(summary = "Update a platform role")
  public RoleOutput update(
      @PathVariable String platformRoleId, @Valid @RequestBody RoleInput input) {
    return toOutput(
        platformRoleService.updatePlatformRole(
            platformRoleId, input.name(), input.description(), input.capabilities()));
  }

  // -- DELETE --

  @AccessControl(
      resourceId = "#platformRoleId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.PLATFORM_ROLE,
      isEnterpriseEdition = true)
  @DeleteMapping("/{platformRoleId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Transactional(rollbackFor = Exception.class)
  @Operation(summary = "Delete a platform role")
  public void delete(@PathVariable String platformRoleId) {
    platformRoleService.delete(platformRoleId);
  }
}
