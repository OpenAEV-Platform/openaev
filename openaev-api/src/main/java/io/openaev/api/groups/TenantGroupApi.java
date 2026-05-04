package io.openaev.api.groups;

import static io.openaev.api.groups.TenantGroupApi.GROUP_URI;
import static io.openaev.api.groups.TenantGroupApi.TENANT_GROUP_URI;
import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.database.specification.GroupSpecification.tenantScope;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.api.groups.dto.TenantGroupCreateInput;
import io.openaev.context.TenantContext;
import io.openaev.database.model.*;
import io.openaev.database.repository.GrantRepository;
import io.openaev.database.repository.GroupRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.group.form.GroupGrantInput;
import io.openaev.rest.group.form.GroupUpdateRolesInput;
import io.openaev.rest.group.form.GroupUpdateUsersInput;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.service.GrantService;
import io.openaev.service.TenantGroupService;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.util.Spliterator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({GROUP_URI, TENANT_GROUP_URI})
@RequiredArgsConstructor
public class TenantGroupApi extends RestBehavior {

  public static final String GROUP_URI = "/api/groups";
  public static final String TENANT_GROUP_URI = TENANT_PREFIX + "/groups";

  private final GrantRepository grantRepository;
  private final GroupRepository groupRepository;
  private final TenantGroupService tenantGroupService;
  private final UserRepository userRepository;
  private final GrantService grantService;

  // -- CREATE --

  @Operation(summary = "Create a tenant group")
  @PostMapping
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.USER_GROUP)
  @Transactional(rollbackFor = Exception.class)
  public Group createGroup(@Valid @RequestBody TenantGroupCreateInput input) {
    return tenantGroupService.createGroup(input);
  }

  @PostMapping("/{groupId}/grants")
  @AccessControl(
      resourceId = "#groupId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.USER_GROUP)
  @Transactional(rollbackFor = Exception.class)
  public Group groupGrant(@PathVariable String groupId, @Valid @RequestBody GroupGrantInput input) {
    // Validate the resourceId
    grantService.validateResourceIdForGrant(input.getResourceId());

    // Resolve dependencies
    Group group = groupRepository.findById(groupId).orElseThrow(ElementNotFoundException::new);

    // Create the grant
    Grant grant = new Grant();
    grant.setName(input.getName());
    grant.setGroup(group);
    grant.setResourceId(input.getResourceId());
    grant.setGrantResourceType(input.getResourceType());

    group.getGrants().add(grant);
    return groupRepository.save(group);
  }

  // -- READ --

  @GetMapping("/{groupId}")
  @AccessControl(
      resourceId = "#groupId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.USER_GROUP)
  public Group group(@PathVariable String groupId) {
    return groupRepository.findById(groupId).orElseThrow(ElementNotFoundException::new);
  }

  @LogExecutionTime
  @PostMapping("/search")
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.USER_GROUP)
  public Page<Group> groups(@RequestBody @Valid final SearchPaginationInput searchPaginationInput) {
    String tenantId = TenantContext.getCurrentTenant();
    return buildPaginationJPA(
        (Specification<Group> spec, org.springframework.data.domain.Pageable pageable) ->
            groupRepository.findAll(tenantScope(tenantId).and(spec), pageable),
        searchPaginationInput,
        Group.class);
  }

  // -- UPDATE --

  @LogExecutionTime
  @PutMapping("/{groupId}/users")
  @AccessControl(
      resourceId = "#groupId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.USER_GROUP)
  @Transactional(rollbackFor = Exception.class)
  public Group updateGroupUsers(
      @PathVariable String groupId, @Valid @RequestBody GroupUpdateUsersInput input) {
    Group group = groupRepository.findById(groupId).orElseThrow(ElementNotFoundException::new);
    Spliterator<User> userSpliterator =
        userRepository.findAllById(input.getUserIds()).spliterator();
    group.setUsers(stream(userSpliterator, false).collect(toList()));
    return groupRepository.save(group);
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
  @Transactional(rollbackFor = Exception.class)
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
  @Transactional(rollbackFor = Exception.class)
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
  @Transactional(rollbackFor = Exception.class)
  public Group deleteGrant(@PathVariable String groupId, @PathVariable String grantId) {
    Group group = groupRepository.findById(groupId).orElseThrow(ElementNotFoundException::new);
    Grant grant = grantRepository.findById(grantId).orElseThrow(ElementNotFoundException::new);
    group.getGrants().remove(grant);
    return this.groupRepository.save(group);
  }

  @DeleteMapping("/{groupId}")
  @AccessControl(
      resourceId = "#groupId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.USER_GROUP)
  @Transactional(rollbackFor = Exception.class)
  public void delete(@PathVariable String groupId) {
    tenantGroupService.delete(groupId);
  }
}
