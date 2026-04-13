package io.openaev.rest.group;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.database.model.*;
import io.openaev.database.repository.GrantRepository;
import io.openaev.database.repository.GroupRepository;
import io.openaev.database.repository.OrganizationRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.group.form.GroupCreateInput;
import io.openaev.rest.group.form.GroupGrantInput;
import io.openaev.rest.group.form.GroupUpdateRolesInput;
import io.openaev.rest.group.form.GroupUpdateUsersInput;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.service.GrantService;
import io.openaev.service.GroupService;
import io.openaev.service.RoleService;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.util.Spliterator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class GroupApi extends RestBehavior {

  public static final String GROUP_URI = "/api/groups";
  private static final String TENANT_GROUP_URI = TENANT_PREFIX + "/groups";

  private final GrantRepository grantRepository;
  private final OrganizationRepository organizationRepository;
  private final GroupRepository groupRepository;
  private final GroupService groupService;
  private final UserRepository userRepository;
  private final RoleService roleService;
  private final GrantService grantService;

  // -- CREATE --

  @LogExecutionTime
  @PostMapping({GROUP_URI, TENANT_GROUP_URI})
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.USER_GROUP)
  @Transactional(rollbackFor = Exception.class)
  public Group createGroup(@Valid @RequestBody GroupCreateInput input) {
    return groupService.createGroup(input);
  }

  @LogExecutionTime
  @PostMapping({GROUP_URI + "/{groupId}/grants", TENANT_GROUP_URI + "/{groupId}/grants"})
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

  @LogExecutionTime
  @GetMapping({GROUP_URI, TENANT_GROUP_URI})
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.USER_GROUP)
  public Iterable<Group> groups() {
    return groupRepository.findAll();
  }

  @LogExecutionTime
  @PostMapping({GROUP_URI + "/search", TENANT_GROUP_URI + "/search"})
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.USER_GROUP)
  public Page<Group> users(@RequestBody @Valid final SearchPaginationInput searchPaginationInput) {
    return buildPaginationJPA(this.groupRepository::findAll, searchPaginationInput, Group.class);
  }

  @LogExecutionTime
  @GetMapping({GROUP_URI + "/{groupId}", TENANT_GROUP_URI + "/{groupId}"})
  @AccessControl(
      resourceId = "#groupId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.USER_GROUP)
  public Group group(@PathVariable String groupId) {
    return groupRepository.findById(groupId).orElseThrow(ElementNotFoundException::new);
  }

  // -- UPDATE --

  @LogExecutionTime
  @PutMapping({GROUP_URI + "/{groupId}/users", TENANT_GROUP_URI + "/{groupId}/users"})
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
  @PutMapping({GROUP_URI + "/{groupId}/roles", TENANT_GROUP_URI + "/{groupId}/roles"})
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
    return groupService.updateGroupRoles(groupId, input);
  }

  @LogExecutionTime
  @PutMapping({GROUP_URI + "/{groupId}/information", TENANT_GROUP_URI + "/{groupId}/information"})
  @AccessControl(
      resourceId = "#groupId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.USER_GROUP)
  @Transactional(rollbackFor = Exception.class)
  public Group updateGroupInformation(
      @PathVariable String groupId, @Valid @RequestBody GroupCreateInput input) {
    return groupService.updateGroup(groupId, input);
  }

  // -- DELETE --

  @LogExecutionTime
  @DeleteMapping({
    GROUP_URI + "/{groupId}/grants/{grantId}",
    TENANT_GROUP_URI + "/{groupId}/grants/{grantId}"
  })
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

  @LogExecutionTime
  @DeleteMapping({GROUP_URI + "/{groupId}", TENANT_GROUP_URI + "/{groupId}"})
  @AccessControl(
      resourceId = "#groupId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.USER_GROUP)
  @Transactional(rollbackFor = Exception.class)
  public void deleteGroup(@PathVariable String groupId) {
    groupRepository.deleteById(groupId);
  }
}
