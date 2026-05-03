package io.openaev.service.platform.groups;

import static io.openaev.database.specification.GroupSpecification.platformScope;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;

import io.openaev.database.model.Group;
import io.openaev.database.model.Role;
import io.openaev.database.model.User;
import io.openaev.database.repository.GroupRepository;
import io.openaev.database.repository.RoleRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.utils.ReferenceResolver;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class PlatformGroupService {

  private final GroupRepository groupRepository;
  private final RoleRepository roleRepository;
  private final UserRepository userRepository;
  private final ReferenceResolver referenceResolver;

  // -- CREATE --

  public Group createPlatformGroup(
      @NotBlank final String name, final String description, boolean defaultUserAssignation) {
    Group group = new Group();
    group.setName(name);
    group.setDescription(description);
    group.setDefaultUserAssignation(defaultUserAssignation);
    return groupRepository.save(group);
  }

  // -- READ --

  @Transactional(readOnly = true)
  public Group findById(@NotBlank final String id) {
    return groupRepository
        .findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Platform group not found: " + id));
  }

  @Transactional(readOnly = true)
  public Page<Group> search(@NotNull SearchPaginationInput searchPaginationInput) {
    return buildPaginationJPA(
        (Specification<Group> spec, org.springframework.data.domain.Pageable pageable) ->
            groupRepository.findAll(platformScope().and(spec), pageable),
        searchPaginationInput,
        Group.class);
  }

  @Transactional(readOnly = true)
  public List<String> findUserIds(@NotBlank final String groupId) {
    return groupRepository.findUserIdsByGroupId(groupId);
  }

  @Transactional(readOnly = true)
  public Set<String> findRoleIds(@NotBlank final String groupId) {
    return groupRepository.findRoleIdsByGroupId(groupId);
  }

  // -- UPDATE --

  public Group updatePlatformGroup(
      @NotBlank final String groupId,
      @NotBlank final String name,
      final String description,
      boolean defaultUserAssignation) {
    Group group = findById(groupId);
    group.setName(name);
    group.setDescription(description);
    group.setDefaultUserAssignation(defaultUserAssignation);
    return groupRepository.save(group);
  }

  public List<String> updatePlatformGroupUsers(
      @NotBlank final String groupId, List<String> userIds) {
    Group group = findById(groupId);
    group.setUsers(
        new java.util.ArrayList<>(
            referenceResolver.resolve(userIds, User.class, userRepository::countByIdIn)));
    groupRepository.save(group);
    return groupRepository.findUserIdsByGroupId(groupId);
  }

  public Set<String> updatePlatformGroupRoles(
      @NotBlank final String groupId, List<String> roleIds) {
    Group group = findById(groupId);
    group.setRoles(
        new java.util.ArrayList<>(
            referenceResolver.resolve(roleIds, Role.class, roleRepository::countByIdIn)));
    groupRepository.save(group);
    return groupRepository.findRoleIdsByGroupId(groupId);
  }

  // -- DELETE --

  public void deletePlatformGroup(@NotBlank final String groupId) {
    Group group = findById(groupId);
    groupRepository.deleteByIdNative(group.getId());
  }
}
