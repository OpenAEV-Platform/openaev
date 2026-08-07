package io.openaev.service;

import static io.openaev.database.model.Role.capabilitiesOf;
import static io.openaev.database.specification.GroupSpecification.tenantScope;
import static io.openaev.service.account.PrivilegeEscalationValidator.assertCanAssignCapabilities;
import static io.openaev.service.account.PrivilegeEscalationValidator.assertCanAssignGrant;

import io.openaev.api.groups.dto.TenantGroupCreateInput;
import io.openaev.context.TenantContext;
import io.openaev.database.model.CapabilityScope;
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
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.constraints.NotBlank;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class TenantGroupService {
  private final GroupRepository groupRepository;
  private final UserRepository userRepository;
  private final TenantRoleService tenantRoleService;
  private final UserService userService;
  private final GrantService grantService;
  @PersistenceContext private EntityManager entityManager;

  // -- CREATE --

  public Group createGroup(TenantGroupCreateInput input) {
    return groupRepository.save(createGroupInner(UUID.randomUUID().toString(), input));
  }

  public Group createInternalGroupWithRole(
      @NotBlank final String id, TenantGroupCreateInput input, List<Role> roles, String tenantId) {
    Group group = createGroupInner(id, input);
    group.setRoles(roles);
    group.setTenant(new Tenant(tenantId));
    return groupRepository.save(group);
  }

  // PRIVATE

  private Group createGroupInner(@NotBlank final String id, TenantGroupCreateInput input) {
    Group group = new Group();
    group.setId(id);
    group.setUpdateAttributes(input);
    group.setTenant(entityManager.getReference(Tenant.class, TenantContext.getCurrentTenant()));
    return group;
  }

  // -- READ --

  @Transactional(readOnly = true)
  public Optional<Group> findByIdAndTenant(
      @NotBlank final String groupId, @NotBlank final String tenantId) {
    return groupRepository.findByIdAndTenantId(groupId, tenantId);
  }

  /** Find a tenant group by ID, scoped to the current tenant. */
  @Transactional(readOnly = true)
  public Group findByIdInTenant(@NotBlank final String groupId) {
    String tenantId = TenantContext.getCurrentTenant();
    return groupRepository
        .findByIdAndTenantId(groupId, tenantId)
        .orElseThrow(() -> new ElementNotFoundException("Group not found with id: " + groupId));
  }

  /**
   * Resolves a group for mutation: same tenant scoping as {@link #findByIdInTenant}, plus the
   * reserved-id check that forbids touching system groups. Kept apart from the plain lookup, which
   * also serves reads — system groups stay readable, only writes are refused.
   */
  private Group findByIdInTenantForWrite(@NotBlank final String groupId) {
    Group group = this.findByIdInTenant(groupId);
    ReservedKeyValidator.validateGroupId(group.getId());
    return group;
  }

  /** Search tenant groups with pagination. */
  @Transactional(readOnly = true)
  public Page<Group> search(SearchPaginationInput searchPaginationInput) {
    String tenantId = TenantContext.getCurrentTenant();
    return io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA(
        (Specification<Group> spec, org.springframework.data.domain.Pageable pageable) ->
            groupRepository.findAll(tenantScope(tenantId).and(spec), pageable),
        searchPaginationInput,
        Group.class);
  }

  // -- UPDATE --

  public Group updateGroup(String groupId, TenantGroupCreateInput input) {
    Group group = this.findByIdInTenantForWrite(groupId);
    if (input.isDefaultUserAssignation() && !group.isDefaultUserAssignation()) {
      assertCanAssignCapabilities(
          userService.currentUser(), capabilitiesOf(group.getRoles()), CapabilityScope.TENANT);
    }
    group.setUpdateAttributes(input);
    return groupRepository.save(group);
  }

  public Group updateGroupRoles(@NotBlank final String groupId, GroupUpdateRolesInput input) {
    Group group = this.findByIdInTenantForWrite(groupId);
    Set<String> uniqueRoleIds = new LinkedHashSet<>(input.getRoleIds());
    List<Role> roles = tenantRoleService.findAllByIdInTenant(uniqueRoleIds);
    if (roles.size() != uniqueRoleIds.size()) {
      throw new EntityNotFoundException("One or more Role not found in: " + uniqueRoleIds);
    }
    roles.forEach(role -> ReservedKeyValidator.validateRoleId(role.getId()));
    assertCanAssignCapabilities(
        userService.currentUser(), capabilitiesOf(roles), CapabilityScope.TENANT);
    group.setRoles(new ArrayList<>(roles));
    return groupRepository.save(group);
  }

  public Group updateInternalGroupWithRoles(
      final Group group, TenantGroupCreateInput input, List<Role> roles) {
    group.setRoles(roles);
    // Applies the remaining attributes and saves once, roles included
    group.setUpdateAttributes(input);
    return groupRepository.save(group);
  }

  /** Update the users of a tenant group. */
  public Group updateGroupUsers(@NotBlank final String groupId, GroupUpdateUsersInput input) {
    Group group = this.findByIdInTenantForWrite(groupId);
    assertCanAssignCapabilities(
        userService.currentUser(), capabilitiesOf(group.getRoles()), CapabilityScope.TENANT);
    Set<String> uniqueUserIds = new LinkedHashSet<>(input.getUserIds());
    List<User> users =
        userRepository.findAllByIdInAndTenantId(uniqueUserIds, TenantContext.getCurrentTenant());
    users.forEach(user -> ReservedKeyValidator.validateUserEmailPattern(user.getEmail()));
    if (users.size() != uniqueUserIds.size()) {
      throw new ElementNotFoundException("One or more users not found in the current tenant");
    }
    group.setUsers(users);
    return groupRepository.save(group);
  }

  // -- DELETE --

  public void delete(@NotBlank final String groupId) {
    Group group = this.findByIdInTenantForWrite(groupId);
    // Clear bidirectional associations before delete to avoid TransientObjectException
    // (User entities in the persistence context would otherwise still reference the removed Group)
    group.getUsers().forEach(user -> user.getUnscopedGroups().remove(group));
    groupRepository.delete(group);
  }

  // -- GRANTS --

  public Group addGrant(@NotBlank final String groupId, GroupGrantInput input) {
    grantService.validateResourceIdForGrant(input.getResourceId());
    Group group = this.findByIdInTenantForWrite(groupId);
    Grant grant = Grant.of(input.getName(), group, input.getResourceId(), input.getResourceType());
    assertCanAssignGrant(userService.currentUser(), input.getName(), input.getResourceId());

    // Group owns the grant lifecycle (cascade ALL + orphanRemoval), so the save below persists it.
    group.getGrants().add(grant);
    return groupRepository.save(group);
  }

  public Group removeGrant(@NotBlank final String groupId, @NotBlank final String grantId) {
    Group group = this.findByIdInTenantForWrite(groupId);
    Grant grant = Grant.find(group.getGrants(), grantId).orElseThrow(ElementNotFoundException::new);
    assertCanAssignGrant(userService.currentUser(), grant.getName(), grant.getResourceId());

    group.getGrants().remove(grant);
    return groupRepository.save(group);
  }
}
