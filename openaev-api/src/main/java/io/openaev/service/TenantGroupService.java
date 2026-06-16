package io.openaev.service;

import static io.openaev.database.specification.GroupSpecification.tenantScope;
import static java.util.stream.Collectors.toList;

import io.openaev.api.groups.dto.TenantGroupCreateInput;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Grant;
import io.openaev.database.model.Group;
import io.openaev.database.model.Role;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.GrantRepository;
import io.openaev.database.repository.GroupRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.group.form.GroupGrantInput;
import io.openaev.rest.group.form.GroupUpdateRolesInput;
import io.openaev.rest.group.form.GroupUpdateUsersInput;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
  private final GrantRepository grantRepository;
  private final UserRepository userRepository;
  private final RoleService roleService;
  private final GrantService grantService;
  @PersistenceContext private EntityManager entityManager;

  // -- CREATE --

  public Group createGroup(TenantGroupCreateInput input) {
    return groupRepository.save(createGroupInner(UUID.randomUUID().toString(), input));
  }

  public Group createGroupWithRole(
      @NotBlank final String id, TenantGroupCreateInput input, List<Role> roles) {
    Group group = createGroupInner(id, input);
    group.setRoles(roles);
    return groupRepository.save(group);
  }

  /** Add a grant to a tenant group. */
  public Group addGrant(@NotBlank final String groupId, GroupGrantInput input) {
    grantService.validateResourceIdForGrant(input.getResourceId());
    String tenantId = TenantContext.getCurrentTenant();
    Group group =
        groupRepository
            .findByIdAndTenantId(groupId, tenantId)
            .orElseThrow(ElementNotFoundException::new);

    Grant grant = new Grant();
    grant.setName(input.getName());
    grant.setGroup(group);
    grant.setResourceId(input.getResourceId());
    grant.setGrantResourceType(input.getResourceType());

    group.getGrants().add(grant);
    return groupRepository.save(group);
  }

  private Group createGroupInner(@NotBlank final String id, TenantGroupCreateInput input) {
    Group group = new Group();
    group.setUpdateAttributes(input);
    group.setId(id);
    group.setTenant(entityManager.getReference(Tenant.class, TenantContext.getCurrentTenant()));
    return group;
  }

  // -- READ --

  /** Find a tenant group by ID, scoped to the current tenant. */
  @Transactional(readOnly = true)
  public Group findByIdInTenant(@NotBlank final String groupId) {
    String tenantId = TenantContext.getCurrentTenant();
    return groupRepository
        .findByIdAndTenantId(groupId, tenantId)
        .orElseThrow(ElementNotFoundException::new);
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

  public Optional<Group> findById(@NotBlank final String id) {
    return groupRepository.findById(id);
  }

  // -- UPDATE --

  public Group updateGroupRoles(@NotBlank final String groupId, GroupUpdateRolesInput input) {
    String tenantId = TenantContext.getCurrentTenant();
    return this.updateGroupRoles(
        groupRepository
            .findByIdAndTenantId(groupId, tenantId)
            .orElseThrow(() -> new ElementNotFoundException("Group not found with id: " + groupId)),
        input.getRoleIds().stream()
            .map(
                id ->
                    roleService
                        .findById(id)
                        .orElseThrow(
                            () -> new ElementNotFoundException("Role not found with id: " + id)))
            .collect(toList()));
  }

  public Group updateGroupRoles(@NotBlank final Group group, List<Role> roles) {
    group.setRoles(roles);
    return groupRepository.save(group);
  }

  public Group updateGroupInfoWithRoles(
      @NotBlank final Group group, TenantGroupCreateInput input, List<Role> roles) {
    return this.updateGroup(this.updateGroupRoles(group, roles), input);
  }

  public Group updateGroup(String groupId, TenantGroupCreateInput input) {
    String tenantId = TenantContext.getCurrentTenant();
    Group group =
        groupRepository
            .findByIdAndTenantId(groupId, tenantId)
            .orElseThrow(ElementNotFoundException::new);
    return this.updateGroup(group, input);
  }

  /** Update the users of a tenant group. */
  public Group updateUsers(@NotBlank final String groupId, GroupUpdateUsersInput input) {
    String tenantId = TenantContext.getCurrentTenant();
    Group group =
        groupRepository
            .findByIdAndTenantId(groupId, tenantId)
            .orElseThrow(ElementNotFoundException::new);
    List<User> users = userRepository.findAllByIdInAndTenantId(input.getUserIds(), tenantId);
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

  public void delete(@NotBlank final String groupId) {
    String tenantId = TenantContext.getCurrentTenant();
    Group group =
        groupRepository
            .findByIdAndTenantId(groupId, tenantId)
            .orElseThrow(() -> new ElementNotFoundException("Group not found with id: " + groupId));
    groupRepository.delete(group);
  }

  /** Remove a grant from a tenant group. */
  public Group removeGrant(@NotBlank final String groupId, @NotBlank final String grantId) {
    String tenantId = TenantContext.getCurrentTenant();
    Group group =
        groupRepository
            .findByIdAndTenantId(groupId, tenantId)
            .orElseThrow(ElementNotFoundException::new);
    Grant grant =
        group.getGrants().stream()
            .filter(g -> grantId.equals(g.getId()))
            .findFirst()
            .orElseThrow(ElementNotFoundException::new);
    group.getGrants().remove(grant);
    return groupRepository.save(group);
  }
}
