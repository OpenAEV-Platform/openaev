package io.openaev.service.account;

import io.openaev.api.groups.dto.TenantGroupCreateInput;
import io.openaev.config.TenantWriteScopeResolver;
import io.openaev.config.cache.MarkingClearanceCacheManager;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Group;
import io.openaev.database.model.Role;
import io.openaev.database.model.User;
import io.openaev.service.TenantGroupService;
import io.openaev.service.TenantRoleService;
import io.openaev.service.UserGroupMembershipService;
import io.openaev.service.UserService;
import io.openaev.service.tenants.TenantUserService;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractPrivilegeService {

  protected final TenantRoleService tenantRoleService;
  protected final TenantGroupService tenantGroupService;
  protected final UserService userService;
  protected final TenantUserService tenantUserService;
  protected final MarkingClearanceCacheManager markingClearanceCacheManager;
  protected final TenantWriteScopeResolver writeScopeResolver;
  protected final UserGroupMembershipService userGroupMembershipService;

  protected abstract WellKnownAccess wellKnownAccess();

  /** Whether the group receives every marking of its tenant, existing and future. */
  protected boolean receivesEveryMarking() {
    return false;
  }

  /**
   * Creates or updates the well-known role of {@code tenantId}. The role is written in that tenant
   * only when the scope {@code ctx} contains it; a missing scope or another tenant's scope is
   * refused with a {@link io.openaev.rest.exception.TenantWriteScopeException}.
   */
  protected Role ensureWellKnownRole(TxCtx ctx, String tenantId) {
    String roleTenantId = writeScopeResolver.tenantForWrite(ctx, tenantId);
    WellKnownAccess access = wellKnownAccess();
    String id = getUUIDFromName(access.roleId(), roleTenantId);
    Optional<Role> role = tenantRoleService.findByIdAndTenant(id, roleTenantId);
    if (role.isEmpty()) {
      return tenantRoleService.createRoleInternal(
          id, access.roleName(), access.roleDescription(), access.capabilities(), roleTenantId);
    }
    return tenantRoleService.updateRoleInternal(
        id, access.roleName(), access.roleDescription(), access.capabilities(), roleTenantId);
  }

  protected Group ensureWellKnownGroupWithRole(Role role, String tenantId) {
    WellKnownAccess access = wellKnownAccess();
    String groupId = getUUIDFromName(access.groupId(), tenantId);
    Optional<Group> group = tenantGroupService.findByIdAndTenant(groupId, tenantId);
    TenantGroupCreateInput input = wellKnownGroupInput();
    List<Role> roles = new ArrayList<>(List.of(role));
    Group saved =
        group.isPresent()
            ? tenantGroupService.updateInternalGroupWithRoles(group.get(), input, roles)
            : tenantGroupService.createInternalGroupWithRole(tenantId, groupId, input, roles);
    if (receivesEveryMarking()) {
      tenantGroupService.receiveEveryMarking(tenantId, saved);
    }
    return saved;
  }

  protected TenantGroupCreateInput wellKnownGroupInput() {
    WellKnownAccess access = wellKnownAccess();
    TenantGroupCreateInput input = new TenantGroupCreateInput();
    input.setName(access.groupName());
    input.setDescription(access.groupDescription());
    input.setDefaultUserAssignation(false);
    return input;
  }

  public static String getUUIDFromName(String name, String tenantId) {
    return UUID.nameUUIDFromBytes((UUID.fromString(name) + ":" + tenantId).getBytes()).toString();
  }

  protected void applyUserServiceAttributes(
      User user, String firstname, String lastname, String email, Group group) {
    user.setFirstname(firstname);
    user.setLastname(lastname);
    user.setEmail(email);
    user.setAdmin(false);
    // The markings a user sees are cached from their groups: refresh them when the groups change.
    if (makeOnlyMembershipOf(user, group)) {
      markingClearanceCacheManager.evictForUser(user.getId());
    }
  }

  /**
   * Makes {@code user}'s whole membership exactly {@code group}: adds it if missing and removes
   * every other group the user currently holds, one {@code users_groups} row at a time through
   * {@link UserGroupMembershipService}, rather than a blanket collection replace that would
   * recreate every tenant's membership row from this one read. Returns whether anything changed.
   */
  private boolean makeOnlyMembershipOf(User user, Group group) {
    List<String> otherGroupIds =
        user.getUnscopedGroups().stream()
            .map(Group::getId)
            .filter(id -> !id.equals(group.getId()))
            .toList();
    boolean changed = userGroupMembershipService.addMembership(user.getId(), group.getId());
    for (String otherGroupId : otherGroupIds) {
      changed |= userGroupMembershipService.removeMembership(user.getId(), otherGroupId);
    }
    return changed;
  }
}
