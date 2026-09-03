package io.openaev.api.groups;

import static io.openaev.api.groups.dto.PlatformGroupMapper.toOutput;

import io.openaev.aop.AccessControl;
import io.openaev.api.groups.dto.PlatformGroupInput;
import io.openaev.api.groups.dto.PlatformGroupMapper;
import io.openaev.api.groups.dto.PlatformGroupOutput;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.service.platform.groups.PlatformGroupService;
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
@RequestMapping(PlatformGroupApi.PLATFORM_GROUPS_URI)
@RequiredArgsConstructor
public class PlatformGroupApi extends RestBehavior {

  public static final String PLATFORM_GROUPS_URI = "/api/platform-groups";
  private final PlatformGroupService platformGroupService;

  // -- CREATE --

  @Operation(summary = "Create a platform group")
  @AccessControl(
      actionPerformed = Action.CREATE,
      resourceType = ResourceType.PLATFORM_GROUP,
      isEnterpriseEdition = true)
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  public PlatformGroupOutput create(TxCtx ctx, @Valid @RequestBody PlatformGroupInput input) {
    return toOutput(
        platformGroupService.createPlatformGroup(
            input.name(), input.description(), input.defaultUserAssignation()));
  }

  // -- READ --

  @Operation(summary = "Get platform group by ID")
  @Transactional
  @AccessControl(
      resourceId = "#platformGroupId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.PLATFORM_GROUP,
      isEnterpriseEdition = true)
  @GetMapping("/{platformGroupId}")
  public PlatformGroupOutput findById(@PathVariable String platformGroupId) {
    return toOutput(platformGroupService.findById(platformGroupId));
  }

  @Operation(summary = "Search platform groups")
  @AccessControl(
      actionPerformed = Action.SEARCH,
      resourceType = ResourceType.PLATFORM_GROUP,
      isEnterpriseEdition = true)
  @PostMapping("/search")
  @Transactional
  public Page<PlatformGroupOutput> search(
      TxCtx ctx, @RequestBody @Valid final SearchPaginationInput searchPaginationInput) {
    return platformGroupService.search(searchPaginationInput).map(PlatformGroupMapper::toOutput);
  }

  @Operation(summary = "Get user IDs for a platform group")
  @Transactional
  @AccessControl(
      resourceId = "#platformGroupId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.PLATFORM_GROUP,
      isEnterpriseEdition = true)
  @GetMapping("/{platformGroupId}/users")
  public List<String> findUsers(@PathVariable String platformGroupId) {
    return platformGroupService.findUserIds(platformGroupId);
  }

  @Operation(summary = "Get platform role IDs for a platform group")
  @Transactional
  @AccessControl(
      resourceId = "#platformGroupId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.PLATFORM_GROUP,
      isEnterpriseEdition = true)
  @GetMapping("/{platformGroupId}/platform-roles")
  public Set<String> findPlatformRoles(@PathVariable String platformGroupId) {
    return platformGroupService.findRoleIds(platformGroupId);
  }

  // -- UPDATE --

  @Operation(summary = "Update a platform group")
  @AccessControl(
      resourceId = "#platformGroupId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.PLATFORM_GROUP,
      isEnterpriseEdition = true)
  @PutMapping("/{platformGroupId}")
  @Transactional
  public PlatformGroupOutput update(
      TxCtx ctx,
      @PathVariable String platformGroupId,
      @Valid @RequestBody PlatformGroupInput input) {
    return toOutput(
        platformGroupService.updatePlatformGroup(
            platformGroupId, input.name(), input.description(), input.defaultUserAssignation()));
  }

  @Operation(summary = "Update users of a platform group")
  @AccessControl(
      resourceId = "#platformGroupId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.PLATFORM_GROUP,
      isEnterpriseEdition = true)
  @PutMapping("/{platformGroupId}/users")
  @Transactional
  public List<String> updateUsers(
      TxCtx ctx,
      @PathVariable String platformGroupId,
      @Valid @RequestBody PlatformGroupUpdateUsersInput input) {
    return platformGroupService.updateGroupUsers(platformGroupId, input.userIds());
  }

  @Operation(summary = "Update platform roles of a platform group")
  @AccessControl(
      resourceId = "#platformGroupId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.PLATFORM_GROUP,
      isEnterpriseEdition = true)
  @PutMapping("/{platformGroupId}/platform-roles")
  @Transactional
  public Set<String> updatePlatformRoles(
      TxCtx ctx,
      @PathVariable String platformGroupId,
      @Valid @RequestBody PlatformGroupUpdateRolesInput input) {
    return platformGroupService.updateGroupRoles(platformGroupId, input.platformRoleIds());
  }

  // -- DELETE --

  @Operation(summary = "Delete a platform group")
  @AccessControl(
      resourceId = "#platformGroupId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.PLATFORM_GROUP,
      isEnterpriseEdition = true)
  @DeleteMapping("/{platformGroupId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Transactional
  public void delete(TxCtx ctx, @PathVariable String platformGroupId) {
    platformGroupService.delete(platformGroupId);
  }
}
