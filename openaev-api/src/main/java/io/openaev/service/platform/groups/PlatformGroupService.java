package io.openaev.service.platform.groups;

import static io.openaev.database.model.CapabilityScope.PLATFORM;
import static io.openaev.database.model.Role.capabilitiesOf;
import static io.openaev.database.specification.GroupSpecification.platformScope;
import static io.openaev.service.account.PrivilegeEscalationValidator.assertCanAssignCapabilities;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;

import io.openaev.config.cache.MarkingClearanceCacheManager;
import io.openaev.database.model.Group;
import io.openaev.database.model.Role;
import io.openaev.database.model.User;
import io.openaev.database.repository.GroupRepository;
import io.openaev.database.repository.RoleRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.service.UserService;
import io.openaev.utils.ReferenceResolver;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.LinkedHashSet;
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
  private final UserService userService;
  private final ReferenceResolver referenceResolver;
  private final MarkingClearanceCacheManager markingClearanceCacheManager;

  // -- CREATE --

  public Group createPlatformGroup(
      @NotBlank final String name, final String description, boolean defaultUserAssignation) {
    Group group = new Group();
    group.setName(name);
    group.setDescription(description);
    group.setDefaultUserAssignation(defaultUserAssignation);
    return groupRepository.save(group);
  }

  /**
   * Idempotently creates or updates a system-managed platform group (with the given roles) using a
   * deterministic id (used by the bootstrap to seed the platform administrators group). Keeps the
   * group platform-scoped (no tenant) and does not touch its membership. Fails fast if a group
   * already exists under the id but is tenant-scoped, since {@code tenant_id} is {@code
   * updatable=false} and silently reusing it would corrupt the platform scope invariant.
   */
  public Group ensureInternalPlatformGroupWithRoles(
      @NotBlank final String id,
      @NotBlank final String name,
      final String description,
      final List<Role> roles) {
    Group group = groupRepository.findById(id).orElseGet(Group::new);
    if (group.getTenant() != null) {
      throw new IllegalStateException(
          "Group " + id + " already exists as a tenant-scoped group; expected a platform group");
    }
    group.setId(id);
    group.setName(name);
    group.setDescription(description);
    group.setDefaultUserAssignation(false);
    group.setRoles(new ArrayList<>(roles));
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
    if (defaultUserAssignation && !group.isDefaultUserAssignation()) {
      assertCanAssignCapabilities(
          userService.currentUser(), capabilitiesOf(group.getRoles()), PLATFORM);
    }
    group.setName(name);
    group.setDescription(description);
    group.setDefaultUserAssignation(defaultUserAssignation);
    return groupRepository.save(group);
  }

  public Set<String> updateGroupRoles(@NotBlank final String groupId, List<String> roleIds) {
    Group group = findById(groupId);
    Set<String> uniqueRoleIds = new LinkedHashSet<>(roleIds);
    List<Role> roles = roleRepository.findAllById(uniqueRoleIds);
    if (roles.size() != uniqueRoleIds.size()) {
      throw new EntityNotFoundException("One or more Role not found in: " + uniqueRoleIds);
    }
    assertCanAssignCapabilities(userService.currentUser(), capabilitiesOf(roles), PLATFORM);
    group.setRoles(new ArrayList<>(roles));
    groupRepository.save(group);
    return groupRepository.findRoleIdsByGroupId(groupId);
  }

  public List<String> updateGroupUsers(@NotBlank final String groupId, List<String> userIds) {
    Group group = findById(groupId);
    assertCanAssignCapabilities(
        userService.currentUser(), capabilitiesOf(group.getRoles()), PLATFORM);
    Set<String> uniqueUserIds = new LinkedHashSet<>(userIds);
    // Union of before and after: a user dropped from the group loses clearance (fail-open if the
    // stale entry survives), a user added gains it (fail-closed, but still wrong until evicted).
    Set<String> affected = new LinkedHashSet<>(group.getUsers().stream().map(User::getId).toList());
    affected.addAll(uniqueUserIds);

    group.setUsers(
        new ArrayList<>(
            referenceResolver.resolve(uniqueUserIds, User.class, userRepository::countByIdIn)));
    groupRepository.save(group);
    markingClearanceCacheManager.evictForUsers(affected);
    return groupRepository.findUserIdsByGroupId(groupId);
  }

  // -- DELETE --

  public void delete(@NotBlank final String groupId) {
    Group group = findById(groupId);
    // Clear bidirectional associations before delete to avoid TransientObjectException
    // (User entities in the persistence context would otherwise still reference the removed Group)
    List<String> members = group.getUsers().stream().map(User::getId).toList();
    group.getUsers().forEach(user -> user.getUnscopedGroups().remove(group));
    groupRepository.delete(group);
    // Deleting the group revokes whatever markings it granted, for every member at once.
    markingClearanceCacheManager.evictForUsers(members);
  }
}
