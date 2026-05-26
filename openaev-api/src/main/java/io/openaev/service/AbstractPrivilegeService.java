package io.openaev.service;

import io.openaev.api.groups.dto.TenantGroupCreateInput;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Group;
import io.openaev.database.model.Role;
import io.openaev.database.model.User;
import io.openaev.service.tenants.TenantUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractPrivilegeService {

  protected final RoleService roleService;
  protected final TenantGroupService tenantGroupService;
  protected final UserService userService;
  protected final TenantUserService tenantUserService;

  protected abstract String getRoleId();
  protected abstract String getRoleName();
  protected abstract String getRoleDescription();
  protected abstract Set<Capability> getRoleCapabilities();

  protected abstract String getGroupId();
  protected abstract String getGroupName();
  protected abstract String getGroupDescription();

  protected Role createWellKnownRole(String tenantId) {
    String id = getUUIDFromName(getRoleId(), tenantId);
    Optional<Role> role = roleService.findById(id);
    if (role.isEmpty()) {
      return roleService.createRoleInternal(
          id, getRoleName(), getRoleDescription(), getRoleCapabilities(), tenantId);
    }
    return roleService.updateRoleInternal(
        id, getRoleName(), getRoleDescription(), getRoleCapabilities());
  }

  protected Group createWellKnownGroupWithRole(Role role, String tenantId) {
    String groupId = getUUIDFromName(getGroupId(), tenantId);
    Optional<Group> group = tenantGroupService.findById(groupId);

    TenantGroupCreateInput input = new TenantGroupCreateInput();
    input.setName(getGroupName());
    input.setDescription(getGroupDescription());
    input.setDefaultUserAssignation(false);

    List<Role> roles = new ArrayList<>(List.of(role));
    if (group.isPresent()) {
      return tenantGroupService.updateGroupInfoWithRoles(group.get(), input, roles);
    }
    return tenantGroupService.createInternalGroupWithRole(groupId, input, roles, tenantId);
  }

  public static String getUUIDFromName(String name, String tenantId) {
    return UUID.nameUUIDFromBytes((UUID.fromString(name) + ":" + tenantId).getBytes()).toString();
  }

  public static void applyUserServiceAttributes(User user, String firstname, String lastname,
                                                String email, Group group) {
    user.setFirstname(firstname);
    user.setLastname(lastname);
    user.setEmail(email);
    user.setAdmin(false);
    user.setGroups(new ArrayList<>(List.of(group)));
  }
}
