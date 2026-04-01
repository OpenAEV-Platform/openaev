package io.openaev.api.platform.groups;

import static io.openaev.api.platform.groups.PlatformGroupMapper.toOutput;

import io.openaev.aop.AccessControl;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.service.platform.groups.PlatformGroupService;
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
@RequestMapping("/api/platform-groups")
@RequiredArgsConstructor
public class PlatformGroupApi {

  private final PlatformGroupService platformGroupService;

  // -- CREATE --

  @Operation(summary = "Create a platform group")
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.PLATFORM_GROUP)
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public PlatformGroupOutput create(@Valid @RequestBody PlatformGroupInput input) {
    return toOutput(platformGroupService.createPlatformGroup(input.name(), input.description()));
  }

  // -- READ --

  @Operation(summary = "Get platform group by ID")
  @AccessControl(
      resourceId = "#platformGroupId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.PLATFORM_GROUP)
  @GetMapping("/{platformGroupId}")
  public PlatformGroupOutput findById(@PathVariable String platformGroupId) {
    return toOutput(platformGroupService.findById(platformGroupId));
  }

  @Operation(summary = "Search platform groups")
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.PLATFORM_GROUP)
  @PostMapping("/search")
  public Page<PlatformGroupOutput> search(
      @RequestBody @Valid final SearchPaginationInput searchPaginationInput) {
    return platformGroupService.search(searchPaginationInput);
  }

  @Operation(summary = "Get user IDs for a platform group")
  @AccessControl(
      resourceId = "#platformGroupId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.PLATFORM_GROUP)
  @GetMapping("/{platformGroupId}/users")
  public List<String> findUsers(@PathVariable String platformGroupId) {
    return platformGroupService.findUserIds(platformGroupId);
  }

  @Operation(summary = "Get platform role IDs for a platform group")
  @AccessControl(
      resourceId = "#platformGroupId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.PLATFORM_GROUP)
  @GetMapping("/{platformGroupId}/platform-roles")
  public Set<String> findPlatformRoles(@PathVariable String platformGroupId) {
    return platformGroupService.findPlatformRoleIds(platformGroupId);
  }

  // -- UPDATE --

  @Operation(summary = "Update a platform group")
  @AccessControl(
      resourceId = "#platformGroupId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.PLATFORM_GROUP)
  @PutMapping("/{platformGroupId}")
  public PlatformGroupOutput update(
      @PathVariable String platformGroupId, @Valid @RequestBody PlatformGroupInput input) {
    return toOutput(
        platformGroupService.updatePlatformGroup(
            platformGroupId, input.name(), input.description()));
  }

  @Operation(summary = "Update users of a platform group")
  @AccessControl(
      resourceId = "#platformGroupId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.PLATFORM_GROUP)
  @PutMapping("/{platformGroupId}/users")
  public List<String> updateUsers(
      @PathVariable String platformGroupId,
      @Valid @RequestBody PlatformGroupUpdateUsersInput input) {
    return platformGroupService.updatePlatformGroupUsers(platformGroupId, input.userIds());
  }

  @Operation(summary = "Update platform roles of a platform group")
  @AccessControl(
      resourceId = "#platformGroupId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.PLATFORM_GROUP)
  @PutMapping("/{platformGroupId}/platform-roles")
  public Set<String> updatePlatformRoles(
      @PathVariable String platformGroupId,
      @Valid @RequestBody PlatformGroupUpdateRolesInput input) {
    return platformGroupService.updatePlatformGroupRoles(platformGroupId, input.platformRoleIds());
  }

  // -- DELETE --

  @Operation(summary = "Delete a platform group")
  @AccessControl(
      resourceId = "#platformGroupId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.PLATFORM_GROUP)
  @DeleteMapping("/{platformGroupId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable String platformGroupId) {
    platformGroupService.deletePlatformGroup(platformGroupId);
  }
}
