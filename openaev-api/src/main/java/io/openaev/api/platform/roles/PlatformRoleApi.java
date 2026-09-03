package io.openaev.api.platform.roles;

import static io.openaev.api.platform.roles.PlatformRoleMapper.toOutput;

import io.openaev.aop.AccessControl;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.Capability;
import io.openaev.database.model.ResourceType;
import io.openaev.service.platform.roles.PlatformRoleService;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(PlatformRoleApi.PLATFORM_ROLES_URI)
@RequiredArgsConstructor
public class PlatformRoleApi {

  public static final String PLATFORM_ROLES_URI = "/api/platform-roles";
  private final PlatformRoleService platformRoleService;

  // -- CREATE --

  @Operation(summary = "Create a platform role")
  @AccessControl(
      actionPerformed = Action.CREATE,
      resourceType = ResourceType.PLATFORM_ROLE,
      isEnterpriseEdition = true)
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  public PlatformRoleOutput create(TxCtx ctx, @Valid @RequestBody PlatformRoleInput input) {
    return toOutput(
        platformRoleService.createPlatformRole(
            input.name(), input.description(), input.capabilities()));
  }

  // -- READ --

  @Operation(summary = "Get platform role by ID")
  @Transactional
  @AccessControl(
      resourceId = "#platformRoleId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.PLATFORM_ROLE,
      isEnterpriseEdition = true)
  @GetMapping("/{platformRoleId}")
  public PlatformRoleOutput findById(@PathVariable String platformRoleId) {
    return toOutput(platformRoleService.findById(platformRoleId));
  }

  @Operation(summary = "Get capabilities of a platform role")
  @Transactional
  @AccessControl(
      resourceId = "#platformRoleId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.PLATFORM_ROLE,
      isEnterpriseEdition = true)
  @GetMapping("/{platformRoleId}/capabilities")
  public Set<Capability> findCapabilities(@PathVariable String platformRoleId) {
    return platformRoleService.findById(platformRoleId).getCapabilities();
  }

  @Operation(summary = "Search platform roles")
  @AccessControl(
      actionPerformed = Action.SEARCH,
      resourceType = ResourceType.PLATFORM_ROLE,
      isEnterpriseEdition = true)
  @PostMapping("/search")
  @Transactional
  public Page<PlatformRoleOutput> search(
      TxCtx ctx, @RequestBody @Valid final SearchPaginationInput searchPaginationInput) {
    return platformRoleService.search(searchPaginationInput).map(PlatformRoleMapper::toOutput);
  }

  @Operation(summary = "Find platform roles by IDs")
  @AccessControl(
      actionPerformed = Action.SEARCH,
      resourceType = ResourceType.PLATFORM_ROLE,
      isEnterpriseEdition = true)
  @PostMapping("/find")
  @Transactional
  public List<PlatformRoleOutput> find(TxCtx ctx, @RequestBody @Valid final List<String> ids) {
    return platformRoleService.findByIds(ids).stream().map(PlatformRoleMapper::toOutput).toList();
  }

  // -- UPDATE --

  @Operation(summary = "Update a platform role")
  @AccessControl(
      resourceId = "#platformRoleId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.PLATFORM_ROLE,
      isEnterpriseEdition = true)
  @PutMapping("/{platformRoleId}")
  @Transactional
  public PlatformRoleOutput update(
      TxCtx ctx, @PathVariable String platformRoleId, @Valid @RequestBody PlatformRoleInput input) {
    return toOutput(
        platformRoleService.updatePlatformRole(
            platformRoleId, input.name(), input.description(), input.capabilities()));
  }

  // -- DELETE --

  @Operation(summary = "Delete a platform role")
  @AccessControl(
      resourceId = "#platformRoleId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.PLATFORM_ROLE,
      isEnterpriseEdition = true)
  @DeleteMapping("/{platformRoleId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Transactional
  public void delete(TxCtx ctx, @PathVariable String platformRoleId) {
    platformRoleService.delete(platformRoleId);
  }
}
