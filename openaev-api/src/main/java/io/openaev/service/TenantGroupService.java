package io.openaev.service;

import static io.openaev.database.model.Role.capabilitiesOf;
import static io.openaev.database.specification.GroupSpecification.tenantScope;
import static io.openaev.service.account.PrivilegeEscalationValidator.assertCanAssignCapabilities;
import static io.openaev.service.account.PrivilegeEscalationValidator.assertCanAssignGrant;
import static io.openaev.service.marking.MarkingEscalationValidator.assertCanAssignMarkings;

import io.openaev.api.groups.dto.GroupUpdateMarkingsInput;
import io.openaev.api.groups.dto.TenantGroupCreateInput;
import io.openaev.config.cache.MarkingClearanceCacheManager;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Action;
import io.openaev.database.model.Capability;
import io.openaev.database.model.CapabilityScope;
import io.openaev.database.model.Grant;
import io.openaev.database.model.Group;
import io.openaev.database.model.MarkingDefinition;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.Role;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.GroupRepository;
import io.openaev.database.repository.MarkingDefinitionRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.processor.datapack.PresetTenantData;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.exception.ForbiddenException;
import io.openaev.rest.group.form.GroupGrantInput;
import io.openaev.rest.group.form.GroupUpdateRolesInput;
import io.openaev.rest.group.form.GroupUpdateUsersInput;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.account.ReservedKeyValidator;
import io.openaev.telemetry.metric_collectors.MarkingMetricCollector;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
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
  private final MarkingDefinitionRepository markingDefinitionRepository;
  private final MarkingClearanceCacheManager markingClearanceCacheManager;
  private final MarkingMetricCollector markingMetricCollector;
  private final PreviewFeatureService previewFeatureService;
  @PersistenceContext private EntityManager entityManager;

  private PermissionService permissionService;

  @Autowired
  public void setPermissionService(@Lazy PermissionService permissionService) {
    this.permissionService = permissionService;
  }

  // -- CREATE --

  public Group createGroup(@NotBlank final String tenantId, TenantGroupCreateInput input) {
    return groupRepository.save(createGroupInner(tenantId, UUID.randomUUID().toString(), input));
  }

  public Group createGroup(@NotBlank final String name, @NotBlank final String tenantId) {
    return createGroup(tenantId, name, null, List.of());
  }

  public Group createGroup(
      @NotBlank final String tenantId, @NotBlank final String name, @NotNull final Role role) {
    return createGroup(tenantId, name, name, List.of(role));
  }

  public Group createInternalGroupWithRole(
      @NotBlank final String tenantId,
      @NotBlank final String id,
      TenantGroupCreateInput input,
      List<Role> roles) {
    Group group = createGroupInner(tenantId, id, input);
    group.setRoles(roles);
    return groupRepository.save(group);
  }

  @Transactional(readOnly = true)
  public List<Group> findByTenantIdAndName(
      @NotBlank final String tenantId, @NotBlank final String name) {
    return groupRepository.findByTenantIdAndName(tenantId, name);
  }

  /** The group of this name in the tenant, lowest id on a tie, as names are not unique. */
  @Transactional(readOnly = true)
  public Optional<Group> findFirstByName(
      @NotBlank final String tenantId, @NotBlank final String name) {
    return firstByName(tenantId, name);
  }

  /**
   * Finds or creates the Manager and Observer roles, then their groups: Admin is the bootstrap's.
   */
  public void findOrCreateDefaultGroups(@NotBlank final String tenantId) {
    Map<String, Role> roles = new LinkedHashMap<>();
    PresetTenantData.DEFAULT_ROLES.forEach(
        (name, capabilities) -> {
          if (!PresetTenantData.ADMIN.equals(name)) {
            roles.put(name, tenantRoleService.findOrCreate(tenantId, name, capabilities));
          }
        });
    roles.forEach((name, role) -> findOrCreate(tenantId, name, role));
  }

  /** The group of this name, left as is when it exists; else created with this role. */
  public Group findOrCreate(
      @NotBlank final String tenantId, @NotBlank final String name, @NotNull final Role role) {
    return firstByName(tenantId, name).orElseGet(() -> createGroup(tenantId, name, role));
  }

  // PRIVATE

  private Optional<Group> firstByName(String tenantId, String name) {
    return groupRepository.findByTenantIdAndName(tenantId, name).stream()
        .min(Comparator.comparing(Group::getId));
  }

  private Group createGroup(String tenantId, String name, String description, List<Role> roles) {
    Group group = new Group();
    group.setName(name);
    group.setDescription(description);
    group.setRoles(new ArrayList<>(roles));
    group.setTenant(entityManager.getReference(Tenant.class, tenantId));
    return groupRepository.save(group);
  }

  private Group createGroupInner(
      @NotBlank final String tenantId, @NotBlank final String id, TenantGroupCreateInput input) {
    Group group = new Group();
    group.setId(id);
    group.setUpdateAttributes(input);
    group.setTenant(entityManager.getReference(Tenant.class, tenantId));
    return group;
  }

  // -- READ --

  @Transactional(readOnly = true)
  public Optional<Group> findByIdAndTenant(
      @NotBlank final String groupId, @NotBlank final String tenantId) {
    return groupRepository.findByIdAndTenantId(groupId, tenantId);
  }

  @Transactional(readOnly = true)
  public Optional<Group> findByNameAndTenantId(
      @NotBlank final String name, @NotBlank final String tenantId) {
    return groupRepository.findByNameAndTenantId(name, tenantId);
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
   * Resolves a group for mutation in the tenant resolved by the API, plus the reserved-id check
   * that forbids touching system groups. Never the thread-local tenant: on the unprefixed {@code
   * /api/groups} route it falls back to the default tenant instead of the caller's scope.
   */
  private Group findByIdAndTenantForWrite(
      @NotBlank final String groupId, @NotBlank final String tenantId) {
    Group group =
        groupRepository
            .findByIdAndTenantId(groupId, tenantId)
            .orElseThrow(() -> new ElementNotFoundException("Group not found with id: " + groupId));
    ReservedKeyValidator.validateGroupId(group.getId(), tenantId);
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

  public Group updateGroup(
      @NotBlank final String tenantId,
      @NotBlank final String groupId,
      TenantGroupCreateInput input) {
    Group group = this.findByIdAndTenantForWrite(groupId, tenantId);
    if (input.isDefaultUserAssignation() && !group.isDefaultUserAssignation()) {
      assertCanAssignInTenant(tenantId, capabilitiesOf(group.getRoles()));
      assertHoldsGroupMarkings(tenantId, group);
    }
    group.setUpdateAttributes(input);
    return groupRepository.save(group);
  }

  public Group updateGroupRoles(
      @NotBlank final String tenantId,
      @NotBlank final String groupId,
      GroupUpdateRolesInput input) {
    Group group = this.findByIdAndTenantForWrite(groupId, tenantId);
    Set<String> uniqueRoleIds = new LinkedHashSet<>(input.getRoleIds());
    List<Role> roles = tenantRoleService.findAllByIdInTenant(tenantId, uniqueRoleIds);
    if (roles.size() != uniqueRoleIds.size()) {
      throw new EntityNotFoundException("One or more Role not found in: " + uniqueRoleIds);
    }
    roles.forEach(role -> ReservedKeyValidator.validateRoleId(role.getId(), tenantId));
    assertCanAssignInTenant(tenantId, capabilitiesOf(roles));
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
  public Group updateGroupUsers(
      @NotBlank final String tenantId,
      @NotBlank final String groupId,
      GroupUpdateUsersInput input) {
    Group group = this.findByIdAndTenantForWrite(groupId, tenantId);
    assertCanAssignInTenant(tenantId, capabilitiesOf(group.getRoles()));
    Set<String> uniqueUserIds = new LinkedHashSet<>(input.getUserIds());
    Set<String> currentIds = group.getUsers().stream().map(User::getId).collect(Collectors.toSet());
    if (!currentIds.containsAll(uniqueUserIds)) {
      assertHoldsGroupMarkings(tenantId, group);
    }
    List<User> users = userRepository.findAllByIdInAndTenantId(uniqueUserIds, tenantId);
    users.forEach(user -> ReservedKeyValidator.validateUserEmailPattern(user.getEmail()));
    if (users.size() != uniqueUserIds.size()) {
      throw new ElementNotFoundException("One or more users not found in the current tenant");
    }
    Set<String> affected = new LinkedHashSet<>(group.getUsers().stream().map(User::getId).toList());
    affected.addAll(uniqueUserIds);

    group.setUsers(users);
    Group saved = groupRepository.save(group);
    markingClearanceCacheManager.evictForUsers(affected);
    return saved;
  }

  // -- MARKINGS --

  /**
   * Replaces the markings the group grants its members.
   *
   * <p>Three guards, in this order:
   *
   * <ol>
   *   <li><b>Existence and tenant.</b> {@code marking_definitions} is a tenant-active table, so the
   *       statement inspector already restricts this read to the request scope: a marking from
   *       another tenant simply does not come back, and the size check turns that into a 404 rather
   *       than a silent partial assignment.
   *   <li><b>Escalation.</b> {@link MarkingEscalationValidator} — you may not grant what you do not
   *       hold. Without it, "may manage groups" would quietly mean "may read every marked row".
   *   <li><b>Capability, only if the set actually changes.</b> Any add or remove requires {@code
   *       MANAGE_MARKING_ASSIGNMENT}, additive to the group's own {@code WRITE} control.
   * </ol>
   *
   * <p>🔴 The eviction at the end is not an optimisation. A cached clearance is pure set
   * containment and never re-reads {@code groups_markings}, so a revoked grant that stays cached
   * keeps granting access: fail-open. Union of before and after, because {@code setMarkings}
   * replaces wholesale — a member losing a marking is only visible in the old set.
   *
   * @param tenantId resolved in the API layer and passed in, per the multi-tenancy convention; the
   *     tenant whose clearance and capabilities the caller is checked against
   */
  public Group updateGroupMarkings(
      @NotBlank final String tenantId,
      @NotBlank final String groupId,
      GroupUpdateMarkingsInput input) {
    Group group = this.findByIdAndTenantForWrite(groupId, tenantId);

    Set<String> uniqueMarkingIds = new LinkedHashSet<>(input.markingIds());
    // CrudRepository returns Iterable; the list is small (a tenant's scale) and needed twice.
    List<MarkingDefinition> markings = new ArrayList<>();
    markingDefinitionRepository.findAllById(uniqueMarkingIds).forEach(markings::add);
    if (markings.size() != uniqueMarkingIds.size()) {
      throw new ElementNotFoundException(
          "One or more marking definitions not found in the current tenant");
    }

    User currentUser = userService.currentUser();
    assertHolds(tenantId, currentUser, markings);

    // Read before setMarkings overwrites it below - this is the "before" side of the diff.
    Set<String> currentMarkingIds =
        group.getMarkings().stream().map(MarkingDefinition::getId).collect(Collectors.toSet());
    Set<String> removedIds = new HashSet<>(currentMarkingIds);
    removedIds.removeAll(uniqueMarkingIds);
    Set<String> addedIds = new HashSet<>(uniqueMarkingIds);
    addedIds.removeAll(currentMarkingIds);

    // Read as granted in tenantId - the group's tenant, not necessarily the caller's ambient one.
    boolean hasCapability =
        permissionService.holdsInTenant(
            tenantId,
            () ->
                permissionService.hasCapabilityPermission(
                    currentUser, ResourceType.MARKING_ASSIGNMENT, Action.WRITE));
    if ((!removedIds.isEmpty() || !addedIds.isEmpty()) && !hasCapability) {
      throw new ForbiddenException("Missing the MANAGE_MARKING_ASSIGNMENT capability");
    }

    Set<String> affected = new LinkedHashSet<>(group.getUsers().stream().map(User::getId).toList());

    markingMetricCollector.recordGroupMarkingsAssigned(addedIds.size());
    markingMetricCollector.recordGroupMarkingsUnassigned(removedIds.size());

    group.getMarkings().clear();
    group.getMarkings().addAll(markings);
    Group saved = groupRepository.save(group);
    updateAutoNewMarking(tenantId, saved, input.autoNewMarking());
    markingClearanceCacheManager.evictForUsers(affected);
    return saved;
  }

  /** Gives a new marking to every group of its tenant that receives new markings. */
  public void grantToAutoNewMarkingGroups(@NotNull final MarkingDefinition marking) {
    String tenantId = marking.getTenant().getId();
    groupRepository
        .findAllByTenantIdAndAutoNewMarkingTrue(tenantId)
        .forEach(group -> grantMissingMarkings(group, List.of(marking)));
  }

  /** Makes the group receive every marking of its tenant, existing and future. */
  public void receiveEveryMarking(@NotBlank final String tenantId, @NotNull final Group group) {
    receiveEveryMarking(group, markingDefinitionRepository.findAllByTenantId(tenantId));
  }

  /** Turning it on needs ASSIGN_MARKING and every marking; turning it off keeps them. */
  private void updateAutoNewMarking(String tenantId, Group group, Boolean autoNewMarking) {
    if (autoNewMarking == null || autoNewMarking == group.isAutoNewMarking()) {
      return;
    }
    if (!autoNewMarking) {
      group.setAutoNewMarking(false);
      groupRepository.save(group);
      return;
    }
    User currentUser = userService.currentUser();
    if (!permissionService.holdsInTenant(
        tenantId,
        () ->
            permissionService.hasCapabilityPermission(
                currentUser, ResourceType.MARKING_ASSIGNMENT, Action.WRITE))) {
      throw new ForbiddenException("Missing the ASSIGN_MARKING capability");
    }
    List<MarkingDefinition> markings = markingDefinitionRepository.findAllByTenantId(tenantId);
    assertHolds(tenantId, currentUser, markings);
    receiveEveryMarking(group, markings);
  }

  private void receiveEveryMarking(Group group, List<MarkingDefinition> markings) {
    group.setAutoNewMarking(true);
    boolean granted = group.getMarkings().addAll(markings);
    groupRepository.save(group);
    if (granted) {
      markingClearanceCacheManager.evictForUsers(
          group.getUsers().stream().map(User::getId).toList());
    }
  }

  /** Reads the caller's rights in the group's tenant, not in the tenant of the route. */
  private void assertCanAssignInTenant(String tenantId, Set<Capability> capabilities) {
    permissionService.inTenant(
        tenantId,
        () -> {
          assertCanAssignCapabilities(
              userService.currentUser(), capabilities, CapabilityScope.TENANT);
          return null;
        });
  }

  private void assertHoldsGroupMarkings(String tenantId, Group group) {
    if (previewFeatureService.isFeatureEnabled(PreviewFeature.MARKING)) {
      assertHolds(tenantId, userService.currentUser(), group.getMarkings());
    }
  }

  private void assertHolds(String tenantId, User user, Collection<MarkingDefinition> markings) {
    assertCanAssignMarkings(
        markingClearanceCacheManager.findClearance(user.getId(), tenantId), markings);
  }

  private void grantMissingMarkings(Group group, List<MarkingDefinition> markings) {
    if (!group.getMarkings().addAll(markings)) {
      return;
    }
    groupRepository.save(group);
    markingClearanceCacheManager.evictForUsers(group.getUsers().stream().map(User::getId).toList());
  }

  // -- DELETE --

  public void delete(@NotBlank final String tenantId, @NotBlank final String groupId) {
    Group group = this.findByIdAndTenantForWrite(groupId, tenantId);
    List<String> members = group.getUsers().stream().map(User::getId).toList();
    group.getUsers().forEach(user -> user.getUnscopedGroups().remove(group));
    groupRepository.delete(group);
    // Deleting the group revokes whatever markings it granted, for every member at once.
    markingClearanceCacheManager.evictForUsers(members);
  }

  // -- GRANTS --

  public Group addGrant(
      @NotBlank final String tenantId, @NotBlank final String groupId, GroupGrantInput input) {
    permissionService.inTenant(
        tenantId,
        () -> {
          grantService.validateResourceIdForGrant(
              input.getResourceId(), input.getResourceType(), tenantId);
          assertCanAssignGrant(userService.currentUser(), input.getName(), input.getResourceId());
          return null;
        });
    Group group = this.findByIdAndTenantForWrite(groupId, tenantId);
    Grant grant = Grant.of(input.getName(), group, input.getResourceId(), input.getResourceType());

    // Group owns the grant lifecycle (cascade ALL + orphanRemoval), so the save below persists it.
    group.getGrants().add(grant);
    return groupRepository.save(group);
  }

  public Group removeGrant(
      @NotBlank final String tenantId,
      @NotBlank final String groupId,
      @NotBlank final String grantId) {
    Group group = this.findByIdAndTenantForWrite(groupId, tenantId);
    Grant grant = Grant.find(group.getGrants(), grantId).orElseThrow(ElementNotFoundException::new);
    permissionService.inTenant(
        tenantId,
        () -> {
          assertCanAssignGrant(userService.currentUser(), grant.getName(), grant.getResourceId());
          return null;
        });

    group.getGrants().remove(grant);
    return groupRepository.save(group);
  }
}
