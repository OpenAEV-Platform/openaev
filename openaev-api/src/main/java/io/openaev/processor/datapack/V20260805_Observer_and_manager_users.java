package io.openaev.processor.datapack;

import io.openaev.database.model.*;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.GroupRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.service.DataPackService;
import io.openaev.service.TenantRoleService;
import io.openaev.service.UserService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Creates Observer and Manager roles/groups (if missing) and a test user for each, so that
 * non-admin roles can be tested on a feature-branch deployment without manual setup.
 *
 * <p>Only active when the {@code test-feature-branch} Spring profile is enabled.
 */
@Component
@Profile("test-feature-branch")
@Slf4j
public class V20260805_Observer_and_manager_users extends DataPack {

  private static final String OBSERVER_EMAIL = "observer@openaev.io";
  private static final String MANAGER_EMAIL = "manager@openaev.io";

  /**
   * Password for the test-bench users, injected from the deployment configuration. When left blank,
   * a random password is generated so no login-capable account with a publicly known password is
   * ever created; an admin can then reset it if interactive access is needed.
   */
  @Value("${openaev.test-bench.user-password:}")
  private String configuredUserPassword;

  private final UserService userService;
  private final UserRepository userRepository;
  private final GroupRepository groupRepository;
  private final TenantRoleService tenantRoleService;
  @PersistenceContext private EntityManager entityManager;

  public V20260805_Observer_and_manager_users(
      DataPackService dataPackService,
      UserService userService,
      UserRepository userRepository,
      GroupRepository groupRepository,
      TenantRoleService tenantRoleService) {
    super(dataPackService);
    this.userService = userService;
    this.userRepository = userRepository;
    this.groupRepository = groupRepository;
    this.tenantRoleService = tenantRoleService;
  }

  @Override
  protected boolean doProcess(Tenant tenant) {
    String password = resolveUserPassword();
    ensureRolesAndGroups(tenant);
    createUserInGroup(tenant, OBSERVER_EMAIL, "Test", "Observer", "Observer", password);
    createUserInGroup(tenant, MANAGER_EMAIL, "Test", "Manager", "Manager", password);
    return true;
  }

  private String resolveUserPassword() {
    if (StringUtils.hasText(configuredUserPassword)) {
      return configuredUserPassword;
    }
    log.warn(
        "No openaev.test-bench.user-password configured: creating test-bench users with a random"
            + " password. Set the property before deployment or reset their password as an admin"
            + " to log in with them.");
    return UUID.randomUUID().toString();
  }

  private void ensureRolesAndGroups(Tenant tenant) {
    String tenantId = tenant.getId();
    PresetTenantData.DEFAULT_ROLES.forEach(
        (roleName, capabilities) -> {
          if (groupRepository.findByNameAndTenantId(roleName, tenantId).isPresent()) {
            return;
          }
          Role role = createRoleIfMissing(roleName, capabilities);
          Group group = new Group();
          group.setName(roleName);
          group.setDescription(roleName);
          group.setDefaultUserAssignation(false);
          group.setTenant(entityManager.getReference(Tenant.class, tenantId));
          group.setRoles(List.of(role));
          groupRepository.save(group);
          log.info("Created group {} for tenant {}", roleName, tenantId);
        });
  }

  private Role createRoleIfMissing(String roleName, Set<Capability> capabilities) {
    return tenantRoleService.createRoleInternal(
        UUID.randomUUID().toString(), roleName, roleName, capabilities, null);
  }

  private void createUserInGroup(
      Tenant tenant,
      String email,
      String firstname,
      String lastname,
      String groupName,
      String password) {
    if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
      log.info("User {} already exists, skipping", email);
      return;
    }

    User user =
        userService.createInternalUser(
            email, firstname, lastname, false, UUID.randomUUID().toString());
    user.setPassword(userService.encodeUserPassword(password));
    user.setTenants(new ArrayList<>(List.of(new Tenant(tenant.getId()))));

    groupRepository
        .findByNameAndTenantId(groupName, tenant.getId())
        .ifPresent(group -> user.setGroups(new ArrayList<>(List.of(group))));

    userRepository.save(user);
    log.info("Created test-bench user {} in group {}", email, groupName);
  }
}
