package io.openaev.service.account;

import static io.openaev.service.account.Constants.*;

import io.openaev.api.groups.dto.TenantGroupCreateInput;
import io.openaev.database.model.Group;
import io.openaev.database.model.Role;
import io.openaev.database.model.User;
import io.openaev.service.RoleService;
import io.openaev.service.TenantGroupService;
import io.openaev.service.UserService;
import io.openaev.service.tenants.TenantUserService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ServiceAccountPrivilegeService {
  public static final String SERVICE_EMAIL_PATTERN = "service-%s@openaev.invalid";
  private static final String SERVICE_FIRSTNAME = "discrete";
  private final RoleService roleService;
  private final TenantGroupService tenantGroupService;
  private final UserService userService;
  private final TenantUserService tenantUserService;

  @Transactional
  public void ensurePrivilegedUserExists(String tenantId) {
    Group group = createWellKnownGroupWithRole(createWellKnownRole(tenantId), tenantId);
    // UNIQUE by tenant
    String email = SERVICE_EMAIL_PATTERN.formatted(tenantId);

    Optional<User> existingEmailUser = userService.findByEmailIgnoreCase(email);

    if (existingEmailUser.isPresent()) {
      if (existingEmailUser.get().getTokens() == null) {
        // Email-matched user exists but has no token — reuse and attach token
        User user = existingEmailUser.get();
        log.warn(
            "User with email {} already exists, but no token found. Reusing existing user.",
            user.getEmail());

        applyAgentAttributes(user, email, group);

        user.setTokens(
            new ArrayList<>(List.of(userService.createUserToken(user, getNewTokenAgent()))));

        tenantUserService.attachToTenant(user.getId(), tenantId);
        userService.saveUser(user);
      }
    } else {
      // No user exists — create one
      User user =
          userService.createInternalUser(email, SERVICE_FIRSTNAME, null, false, getNewTokenAgent());
      user.setGroups(new ArrayList<>(List.of(group)));
      tenantUserService.attachToTenant(user.getId(), tenantId);
      userService.saveUser(user);
    }
  }

  public Optional<User> getUserServiceAccountByTenant(String tenantId) {
    String email = SERVICE_EMAIL_PATTERN.formatted(tenantId);

    return userService.findByEmailIgnoreCase(email);
  }

  private Role createWellKnownRole(String tenantId) {
    List<Role> processRoles = roleService.findAll(tenantId);
    Optional<Role> processRole =
        processRoles.stream().filter(role -> role.getName().equals(SERVICE_ROLE_NAME)).findFirst();

    if (processRole.isEmpty()) {
      processRole =
          Optional.of(
              roleService.createRole(
                  null, SERVICE_ROLE_NAME, SERVICE_ROLE_DESCRIPTION, SERVICE_ROLE_CAPABILITIES));
    }
    return processRole.get();
  }

  private Group createWellKnownGroupWithRole(Role role, String tenantId) {
    List<Group> processGroups = tenantGroupService.findAllByTenantId(tenantId);

    Optional<Group> groupByTenant =
        processGroups.stream()
            .filter(group -> group.getName().equals(SERVICE_GROUP_NAME))
            .reduce(
                (a, b) -> {
                  throw new UnsupportedOperationException(
                      "Duplicate service group '%s' found for tenant '%s' (conflicting group IDs: %s, %s)"
                          .formatted(SERVICE_GROUP_NAME, tenantId, a.getId(), b.getId()));
                });

    TenantGroupCreateInput input = new TenantGroupCreateInput();
    input.setName(SERVICE_GROUP_NAME);
    input.setDescription(SERVICE_GROUP_DESCRIPTION);
    input.setDefaultUserAssignation(false);

    Optional<Group> processGroup =
        groupByTenant
            .map(
                group ->
                    tenantGroupService.updateGroupInfoWithRoles(
                        group, input, new ArrayList<>(List.of(role))))
            .or(
                () ->
                    Optional.of(
                        tenantGroupService.createGroupWithRole(
                            null, input, new ArrayList<>(List.of(role)))));
    return processGroup.get();
  }

  private String getNewTokenAgent() {
    return UUID.randomUUID().toString();
  }

  private void applyAgentAttributes(User user, String email, Group group) {
    user.setFirstname(SERVICE_FIRSTNAME);
    user.setLastname(null);
    user.setEmail(email);
    user.setAdmin(false);
    user.setGroups(new ArrayList<>(List.of(group)));
  }
}
