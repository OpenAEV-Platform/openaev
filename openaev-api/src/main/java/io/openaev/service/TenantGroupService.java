package io.openaev.service;

import static java.util.stream.Collectors.toList;

import io.openaev.api.groups.dto.TenantGroupCreateInput;
import io.openaev.database.model.Grant;
import io.openaev.database.model.Group;
import io.openaev.database.model.Role;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.GroupRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.group.form.GroupGrantInput;
import io.openaev.rest.group.form.GroupUpdateRolesInput;
import io.openaev.rest.group.form.GroupUpdateUsersInput;
import io.openaev.service.account.ReservedKeyValidator;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TenantGroupService {
  private final GroupRepository groupRepository;
  private final UserRepository userRepository;
  private final RoleService roleService;
  private final GrantService grantService;
  @PersistenceContext private EntityManager entityManager;

  // -- CREATE --

  public Group createGroup(String tenantId, TenantGroupCreateInput input) {
    return groupRepository.save(createGroupInner(tenantId, UUID.randomUUID().toString(), input));
  }

  public Group createInternalGroupWithRole(
      String tenantId, @NotBlank final String id, TenantGroupCreateInput input, List<Role> roles) {
    Group group = createGroupInner(tenantId, id, input);
    group.setRoles(roles);
    group.setTenant(new Tenant(tenantId));
    return groupRepository.save(group);
  }

  /** Add a grant to a tenant group. */
  public Group addGrant(String tenantId, @NotBlank final String groupId, GroupGrantInput input) {
    grantService.validateResourceIdForGrant(input.getResourceId());
    Group group = groupRepository.findById(groupId).orElseThrow(ElementNotFoundException::new);
    ReservedKeyValidator.validateGroupId(tenantId, group.getId());
    Grant grant = new Grant();
    grant.setName(input.getName());
    grant.setGroup(group);
    grant.setResourceId(input.getResourceId());
    grant.setGrantResourceType(input.getResourceType());

    group.getGrants().add(grant);
    return groupRepository.save(group);
  }

  private Group createGroupInner(
      String tenantId, @NotBlank final String id, TenantGroupCreateInput input) {
    Group group = new Group();
    group.setUpdateAttributes(input);
    group.setId(id);
    group.setTenant(new Tenant(tenantId));
    return group;
  }

  // -- READ --

  /** Find a tenant group by ID, scoped to the current tenant. */
  public Group findByIdInTenant(@NotBlank final String groupId) {
    return groupRepository.findById(groupId).orElseThrow(ElementNotFoundException::new);
  }

  /** Search tenant groups with pagination. */
  public Page<Group> search(SearchPaginationInput searchPaginationInput) {
    return io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA(
        groupRepository::findAll, searchPaginationInput, Group.class);
  }

  public Optional<Group> findById(@NotBlank final String id) {
    return groupRepository.findById(id);
  }

  // -- UPDATE --

  public Group updateGroupRoles(
      String tenantId, @NotBlank final String groupId, GroupUpdateRolesInput input) {
    Group group =
        groupRepository
            .findById(groupId)
            .orElseThrow(() -> new ElementNotFoundException("Group not found with id: " + groupId));
    ReservedKeyValidator.validateGroupId(tenantId, group.getId());
    List<Role> roles =
        input.getRoleIds().stream()
            .map(
                id ->
                    roleService
                        .findById(id)
                        .orElseThrow(
                            () -> new ElementNotFoundException("Role not found with id: " + id)))
            .collect(toList());

    roles.forEach(role -> ReservedKeyValidator.validateRoleId(tenantId, role.getId()));
    return this.updateGroupRoles(group, roles);
  }

  public Group updateGroupRoles(@NotBlank final Group group, List<Role> roles) {
    group.setRoles(roles);
    return groupRepository.save(group);
  }

  public Group updateGroupInfoWithRoles(
      @NotBlank final Group group, TenantGroupCreateInput input, List<Role> roles) {
    return this.updateGroup(this.updateGroupRoles(group, roles), input);
  }

  public Group updateGroup(String tenantId, String groupId, TenantGroupCreateInput input) {
    // Check if new name is reserved
    ReservedKeyValidator.validateGroupId(tenantId, groupId);
    Group group = groupRepository.findById(groupId).orElseThrow(ElementNotFoundException::new);
    // Check if previous name is reserved
    ReservedKeyValidator.validateGroupId(tenantId, group.getId());
    return this.updateGroup(group, input);
  }

  /** Update the users of a tenant group. */
  public Group updateUsers(
      String tenantId, @NotBlank final String groupId, GroupUpdateUsersInput input) {
    Group group = groupRepository.findById(groupId).orElseThrow(ElementNotFoundException::new);
    ReservedKeyValidator.validateGroupId(tenantId, group.getId());
    List<User> users = userRepository.findAllById(input.getUserIds());
    users.forEach(user -> ReservedKeyValidator.validateUserEmailPattern(user.getEmail()));
    if (users.size() != input.getUserIds().size()) {
      throw new ElementNotFoundException("One or more users not found in the current tenant");
    }
    group.setUsers(users);
    return groupRepository.save(group);
  }

  private Group updateGroup(Group group, TenantGroupCreateInput input) {
    group.setUpdateAttributes(input);
    return groupRepository.save(group);
  }

  // -- DELETE --

  public void delete(String tenantId, @NotBlank final String groupId) {
    Group group =
        groupRepository
            .findById(groupId)
            .orElseThrow(() -> new ElementNotFoundException("Group not found with id: " + groupId));

    ReservedKeyValidator.validateGroupId(tenantId, group.getId());
    // Clear bidirectional associations before delete to avoid TransientObjectException
    // (User entities in the persistence context would otherwise still reference the removed Group)
    group.getUsers().forEach(user -> user.getGroups().remove(group));
    groupRepository.delete(group);
  }

  /** Remove a grant from a tenant group. */
  public Group removeGrant(
      String tenantId, @NotBlank final String groupId, @NotBlank final String grantId) {
    Group group = groupRepository.findById(groupId).orElseThrow(ElementNotFoundException::new);
    ReservedKeyValidator.validateGroupId(tenantId, group.getId());
    Grant grant =
        group.getGrants().stream()
            .filter(g -> grantId.equals(g.getId()))
            .findFirst()
            .orElseThrow(ElementNotFoundException::new);
    group.getGrants().remove(grant);
    return groupRepository.save(group);
  }
}
