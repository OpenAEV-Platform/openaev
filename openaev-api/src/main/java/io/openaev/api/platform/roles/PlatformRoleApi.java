package io.openaev.api.platform.roles;

import static io.openaev.api.platform.roles.PlatformRoleMapper.toOutput;

import io.openaev.aop.AccessControl;
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
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/platform-roles")
@RequiredArgsConstructor
public class PlatformRoleApi {

  private final PlatformRoleService platformRoleService;

  // -- CREATE --

  @Operation(summary = "Create a platform role")
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.PLATFORM_ROLE)
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public PlatformRoleOutput create(@Valid @RequestBody PlatformRoleInput input) {
    return toOutput(
        platformRoleService.createPlatformRole(
            input.name(), input.description(), input.capabilities()));
  }

  // -- READ --

  @Operation(summary = "Get platform role by ID")
  @AccessControl(
      resourceId = "#platformRoleId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.PLATFORM_ROLE)
  @GetMapping("/{platformRoleId}")
  public PlatformRoleOutput findById(@PathVariable String platformRoleId) {
    return toOutput(platformRoleService.findById(platformRoleId));
  }

  @Operation(summary = "Get capabilities of a platform role")
  @AccessControl(
      resourceId = "#platformRoleId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.PLATFORM_ROLE)
  @GetMapping("/{platformRoleId}/capabilities")
  public Set<Capability> findCapabilities(@PathVariable String platformRoleId) {
    return platformRoleService.findById(platformRoleId).getCapabilities();
  }

  @Operation(summary = "Search platform roles")
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.PLATFORM_ROLE)
  @PostMapping("/search")
  public Page<PlatformRoleOutput> search(
      @RequestBody @Valid final SearchPaginationInput searchPaginationInput) {
    return platformRoleService.search(searchPaginationInput);
  }

  @Operation(summary = "Find platform roles by IDs")
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.PLATFORM_ROLE)
  @PostMapping("/find")
  public List<PlatformRoleOutput> find(@RequestBody @Valid final List<String> ids) {
    return platformRoleService.findByIds(ids).stream().map(PlatformRoleMapper::toOutput).toList();
  }

  // -- UPDATE --

  @Operation(summary = "Update a platform role")
  @AccessControl(
      resourceId = "#platformRoleId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.PLATFORM_ROLE)
  @PutMapping("/{platformRoleId}")
  public PlatformRoleOutput update(
      @PathVariable String platformRoleId, @Valid @RequestBody PlatformRoleInput input) {
    return toOutput(
        platformRoleService.updatePlatformRole(
            platformRoleId, input.name(), input.description(), input.capabilities()));
  }

  // -- DELETE --

  @Operation(summary = "Delete a platform role")
  @AccessControl(
      resourceId = "#platformRoleId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.PLATFORM_ROLE)
  @DeleteMapping("/{platformRoleId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable String platformRoleId) {
    platformRoleService.deletePlatformRole(platformRoleId);
  }
}
