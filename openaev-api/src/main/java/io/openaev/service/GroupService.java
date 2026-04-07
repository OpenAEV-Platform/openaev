package io.openaev.service;

import static java.util.stream.Collectors.toList;

import io.openaev.api.exception.ElementNotFoundException;
import io.openaev.api.group.form.GroupCreateInput;
import io.openaev.api.group.form.GroupUpdateRolesInput;
import io.openaev.database.model.Group;
import io.openaev.database.model.Role;
import io.openaev.database.repository.GroupRepository;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GroupService {
  private final GroupRepository groupRepository;
  private final RoleService roleService;

  public Group createGroup(GroupCreateInput input) {
    return groupRepository.save(createGroupInner(UUID.randomUUID().toString(), input));
  }

  public Group createGroupWithRole(GroupCreateInput input, List<Role> roles) {
    return createGroupWithRole(UUID.randomUUID().toString(), input, roles);
  }

  public Group createGroupWithRole(
      @NotBlank final String id, GroupCreateInput input, List<Role> roles) {
    Group group = createGroupInner(id, input);
    group.setRoles(roles);
    return groupRepository.save(group);
  }

  private Group createGroupInner(@NotBlank final String id, GroupCreateInput input) {
    Group group = new Group();
    group.setUpdateAttributes(input);
    group.setId(id);
    return group;
  }

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
      @NotBlank final Group group, GroupCreateInput input, List<Role> roles) {
    return this.updateGroup(this.updateGroupRoles(group, roles), input);
  }

  public Group updateGroup(String groupId, GroupCreateInput input) {
    Group group = groupRepository.findById(groupId).orElseThrow(ElementNotFoundException::new);
    return this.updateGroup(group, input);
  }

  private Group updateGroup(Group group, GroupCreateInput input) {
    group.setUpdateAttributes(input);
    return groupRepository.save(group);
  }

  public Optional<Group> findById(@NotBlank final String id) {
    return groupRepository.findById(id);
  }
}
