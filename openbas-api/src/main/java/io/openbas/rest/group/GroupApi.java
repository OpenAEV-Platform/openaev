package io.openbas.rest.group;

import static io.openbas.utils.pagination.PaginationUtils.buildPaginationJPA;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;

import io.openbas.aop.RBAC;
import io.openbas.database.model.*;
import io.openbas.database.repository.GrantRepository;
import io.openbas.database.repository.GroupRepository;
import io.openbas.database.repository.OrganizationRepository;
import io.openbas.database.repository.UserRepository;
import io.openbas.rest.exception.ElementNotFoundException;
import io.openbas.rest.group.form.*;
import io.openbas.rest.helper.RestBehavior;
import io.openbas.service.GrantService;
import io.openbas.service.RoleService;
import io.openbas.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import java.util.Spliterator;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@AllArgsConstructor
public class GroupApi extends RestBehavior {

  private final GrantRepository grantRepository;
  private final OrganizationRepository organizationRepository;
  private final GroupRepository groupRepository;
  private final UserRepository userRepository;
  private final RoleService roleService;
  private final GrantService grantService;

  @GetMapping("/api/groups")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.USER_GROUP)
  public Iterable<Group> groups() {
    return groupRepository.findAll();
  }

  @PostMapping("/api/groups/search")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.USER_GROUP)
  public Page<Group> users(@RequestBody @Valid final SearchPaginationInput searchPaginationInput) {
    return buildPaginationJPA(this.groupRepository::findAll, searchPaginationInput, Group.class);
  }

  @GetMapping("/api/groups/{groupId}")
  @RBAC(
      resourceId = "#groupId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.USER_GROUP)
  public Group group(@PathVariable String groupId) {
    return groupRepository.findById(groupId).orElseThrow(ElementNotFoundException::new);
  }

  @PostMapping("/api/groups")
  @RBAC(actionPerformed = Action.CREATE, resourceType = ResourceType.USER_GROUP)
  @Transactional(rollbackOn = Exception.class)
  public Group createGroup(@Valid @RequestBody GroupCreateInput input) {
    Group group = new Group();
    group.setUpdateAttributes(input);
    return groupRepository.save(group);
  }

  @PutMapping("/api/groups/{groupId}/users")
  @RBAC(
      resourceId = "#groupId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.USER_GROUP)
  @Transactional(rollbackOn = Exception.class)
  public Group updateGroupUsers(
      @PathVariable String groupId, @Valid @RequestBody GroupUpdateUsersInput input) {
    Group group = groupRepository.findById(groupId).orElseThrow(ElementNotFoundException::new);
    Spliterator<User> userSpliterator =
        userRepository.findAllById(input.getUserIds()).spliterator();
    group.setUsers(stream(userSpliterator, false).collect(toList()));
    return groupRepository.save(group);
  }

  @PutMapping("/api/groups/{groupId}/roles")
  @RBAC(
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
  @Transactional(rollbackOn = Exception.class)
  public Group updateGroupRoles(
      @PathVariable String groupId, @Valid @RequestBody GroupUpdateRolesInput input) {
    Group group =
        groupRepository
            .findById(groupId)
            .orElseThrow(() -> new ElementNotFoundException("Group not found with id: " + groupId));

    group.setRoles(
        input.getRoleIds().stream()
            .map(
                id ->
                    roleService
                        .findById(id)
                        .orElseThrow(
                            () -> new ElementNotFoundException("Role not found with id: " + id)))
            .collect(toList()));

    return groupRepository.save(group);
  }

  @PutMapping("/api/groups/{groupId}/information")
  @RBAC(
      resourceId = "#groupId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.USER_GROUP)
  @Transactional(rollbackOn = Exception.class)
  public Group updateGroupInformation(
      @PathVariable String groupId, @Valid @RequestBody GroupCreateInput input) {
    Group group = groupRepository.findById(groupId).orElseThrow(ElementNotFoundException::new);
    group.setUpdateAttributes(input);
    return groupRepository.save(group);
  }

  @PostMapping("/api/groups/{groupId}/grants")
  @RBAC(
      resourceId = "#groupId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.USER_GROUP)
  @Transactional(rollbackOn = Exception.class)
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

  @DeleteMapping("/api/groups/{groupId}/grants/{grantId}")
  @RBAC(
      resourceId = "#groupId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.USER_GROUP)
  @Transactional(rollbackOn = Exception.class)
  public Group deleteGrant(@PathVariable String groupId, @PathVariable String grantId) {
    Group group = groupRepository.findById(groupId).orElseThrow(ElementNotFoundException::new);
    Grant grant = grantRepository.findById(grantId).orElseThrow(ElementNotFoundException::new);
    group.getGrants().remove(grant);
    return this.groupRepository.save(group);
  }

  @PostMapping("/api/groups/{groupId}/organizations")
  @RBAC(
      resourceId = "#groupId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.USER_GROUP)
  @Transactional(rollbackOn = Exception.class)
  public Group groupOrganization(
      @PathVariable String groupId, @Valid @RequestBody OrganizationGrantInput input) {
    // Resolve dependencies
    Group group = groupRepository.findById(groupId).orElseThrow(ElementNotFoundException::new);
    Organization organization =
        organizationRepository
            .findById(input.getOrganizationId())
            .orElseThrow(ElementNotFoundException::new);
    group.getOrganizations().add(organization);
    return groupRepository.save(group);
  }

  @DeleteMapping("/api/groups/{groupId}/organizations/{organizationId}")
  @RBAC(
      resourceId = "#groupId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.USER_GROUP)
  public Group deleteGroupOrganization(
      @PathVariable String groupId, @PathVariable String organizationId) {
    Group group = groupRepository.findById(groupId).orElseThrow(ElementNotFoundException::new);
    Organization organization =
        organizationRepository.findById(organizationId).orElseThrow(ElementNotFoundException::new);
    group.getOrganizations().remove(organization);
    return groupRepository.save(group);
  }

  @DeleteMapping("/api/groups/{groupId}")
  @RBAC(
      resourceId = "#groupId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.USER_GROUP)
  @Transactional(rollbackOn = Exception.class)
  public void deleteGroup(@PathVariable String groupId) {
    groupRepository.deleteById(groupId);
  }
}
