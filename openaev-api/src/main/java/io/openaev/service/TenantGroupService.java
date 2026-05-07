package io.openaev.service;

import static java.util.stream.Collectors.toList;

import io.openaev.api.groups.dto.TenantGroupCreateInput;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Group;
import io.openaev.database.model.Role;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.GroupRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.group.form.GroupUpdateRolesInput;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TenantGroupService {
  private final GroupRepository groupRepository;
  private final RoleService roleService;
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

  private Group createGroupInner(@NotBlank final String id, TenantGroupCreateInput input) {
    Group group = new Group();
    group.setUpdateAttributes(input);
    group.setId(id);
    group.setTenant(entityManager.getReference(Tenant.class, TenantContext.getCurrentTenant()));
    return group;
  }

  // -- READ --

  public Group updateGroupRoles(@NotBlank final String groupId, GroupUpdateRolesInput input) {
    return this.updateGroupRoles(
        groupRepository
            .findById(groupId)
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
    Group group = groupRepository.findById(groupId).orElseThrow(ElementNotFoundException::new);
    return this.updateGroup(group, input);
  }

  private Group updateGroup(Group group, TenantGroupCreateInput input) {
    group.setUpdateAttributes(input);
    return groupRepository.save(group);
  }

  public Optional<Group> findById(@NotBlank final String id) {
    return groupRepository.findById(id);
  }

  // -- DELETE --

  public void delete(@NotBlank final String groupId) {
    Group group =
        groupRepository
            .findById(groupId)
            .orElseThrow(() -> new ElementNotFoundException("Group not found with id: " + groupId));
    groupRepository.delete(group);
  }
}
