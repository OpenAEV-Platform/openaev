package io.openaev.service.account;

import io.openaev.database.model.Capability;
import io.openaev.database.model.Group;
import io.openaev.database.model.Role;
import io.openaev.database.model.User;
import io.openaev.service.*;
import io.openaev.service.platform.groups.PlatformGroupService;
import io.openaev.service.platform.roles.PlatformRoleService;
import io.openaev.service.tenants.TenantUserService;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ensures the platform admin user holds full administrative privileges through well-known
 * "Administrators" groups: a platform-scoped group (tenant = null) granting platform BYPASS and a
 * per-tenant group granting tenant BYPASS, with the admin user enrolled in both.
 *
 * <p>New tenants get their default Admin/Manager/Observer groups seeded through the tenant
 * provisioning chain (see {@code V20260330_Default_tenant_data}). The default tenant, however, is
 * created by a Flyway migration and never goes through that chain, so a fresh platform ended up
 * with no admin group at all. The platform admin still bypasses RBAC through {@code user_admin =
 * true}, but the missing groups broke group-based administration parity with every other tenant.
 * This regression was introduced together with multi-tenancy (PR #4864).
 *
 * <p>The two scopes are both required: a tenant BYPASS only expands to tenant-scoped capabilities
 * (see {@link io.openaev.database.model.User#getCapabilities()}), so a platform-scoped admin group
 * is needed to grant the platform-only capabilities (tenants, platform settings, platform
 * users/groups/roles) through group membership rather than only via {@code user_admin}.
 *
 * <p>Bootstrapping the groups from {@link io.openaev.runner.InitAdminCommandLineRunner} is the only
 * place that runs after the admin user is guaranteed to exist, which is why the membership is wired
 * here rather than in the datapack.
 */
@Service
@Slf4j
public class AdminPrivilegeService extends AbstractPrivilegeService {

  public static final String ADMIN_ROLE_ID = "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d";
  public static final String ADMIN_ROLE_NAME = "Admin";
  public static final String ADMIN_ROLE_DESCRIPTION = "Full administrative access to the tenant.";
  public static final Set<Capability> ADMIN_ROLE_CAPABILITIES = Set.of(Capability.BYPASS);

  public static final String ADMIN_GROUP_ID = "2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e";
  public static final String ADMIN_GROUP_NAME = "Administrators";
  public static final String ADMIN_GROUP_DESCRIPTION = "Tenant administrators.";

  public static final String PLATFORM_ADMIN_ROLE_ID = "3c4d5e6f-7a8b-4c9d-8e0f-2a3b4c5d6e7f";
  public static final String PLATFORM_ADMIN_ROLE_NAME = "Admin";
  public static final String PLATFORM_ADMIN_ROLE_DESCRIPTION =
      "Full administrative access to the platform.";

  public static final String PLATFORM_ADMIN_GROUP_ID = "4d5e6f7a-8b9c-4d0e-9f1a-3b4c5d6e7f8a";
  public static final String PLATFORM_ADMIN_GROUP_NAME = "Administrators";
  public static final String PLATFORM_ADMIN_GROUP_DESCRIPTION = "Platform administrators.";

  private final PlatformRoleService platformRoleService;
  private final PlatformGroupService platformGroupService;

  @Autowired
  public AdminPrivilegeService(
      TenantRoleService tenantRoleService,
      TenantGroupService tenantGroupService,
      UserService userService,
      TenantUserService tenantUserService,
      PlatformRoleService platformRoleService,
      PlatformGroupService platformGroupService) {
    super(tenantRoleService, tenantGroupService, userService, tenantUserService);
    this.platformRoleService = platformRoleService;
    this.platformGroupService = platformGroupService;
  }

  @Override
  protected String getRoleId() {
    return ADMIN_ROLE_ID;
  }

  @Override
  protected String getRoleName() {
    return ADMIN_ROLE_NAME;
  }

  @Override
  protected String getRoleDescription() {
    return ADMIN_ROLE_DESCRIPTION;
  }

  @Override
  protected Set<Capability> getRoleCapabilities() {
    return ADMIN_ROLE_CAPABILITIES;
  }

  @Override
  protected String getGroupId() {
    return ADMIN_GROUP_ID;
  }

  @Override
  protected String getGroupName() {
    return ADMIN_GROUP_NAME;
  }

  @Override
  protected String getGroupDescription() {
    return ADMIN_GROUP_DESCRIPTION;
  }

  /**
   * Idempotently ensures the given tenant owns the admin group (with the BYPASS role), that the
   * given user belongs to that tenant, and that the user is a member of the group. Safe to call on
   * every platform startup.
   *
   * <p>The caller passes the already-resolved admin {@link User} (the bootstrap runner has just
   * created or loaded it) rather than an id, so the membership check never depends on re-fetching
   * the user through {@code UserService} - a service that some test contexts replace with a mock.
   *
   * @param tenantId the tenant the group belongs to
   * @param adminUser the user to enroll as an administrator
   * @return the (created or existing) admin group
   */
  @Transactional
  public Group ensureAdminGroup(String tenantId, User adminUser) {
    Group group = createWellKnownGroupWithRole(createWellKnownRole(tenantId), tenantId);
    boolean alreadyMember =
        adminUser.getUnscopedGroups().stream().anyMatch(g -> g.getId().equals(group.getId()));
    if (!alreadyMember) {
      adminUser.getUnscopedGroups().add(group);
      userService.saveUser(adminUser);
      log.info(
          "Enrolled admin user {} into the '{}' group for tenant {}",
          adminUser.getId(),
          getGroupName(),
          tenantId);
    }
    // Mirror ServiceAccountPrivilegeService: make sure the admin belongs to the tenant that owns
    // the group, so the self-heal also covers installs whose users_tenants row is missing. The call
    // is idempotent and clears the persistence context, so it runs after the group enrollment.
    tenantUserService.attachToTenant(adminUser.getId(), tenantId);
    return group;
  }

  /**
   * Idempotently ensures a platform-scoped (tenant = null) admin group granting platform BYPASS
   * exists and that the given user is a member. Unlike a tenant BYPASS, a platform BYPASS expands
   * to the platform-only capabilities (tenants, platform settings, platform users/groups/roles).
   * Safe to call on every platform startup.
   *
   * @param adminUser the user to enroll as a platform administrator
   * @return the (created or existing) platform admin group
   */
  @Transactional
  public Group ensurePlatformAdminGroup(User adminUser) {
    Role role =
        platformRoleService.ensureInternalPlatformRole(
            PLATFORM_ADMIN_ROLE_ID,
            PLATFORM_ADMIN_ROLE_NAME,
            PLATFORM_ADMIN_ROLE_DESCRIPTION,
            Set.of(Capability.BYPASS));
    Group group =
        platformGroupService.ensureInternalPlatformGroupWithRoles(
            PLATFORM_ADMIN_GROUP_ID,
            PLATFORM_ADMIN_GROUP_NAME,
            PLATFORM_ADMIN_GROUP_DESCRIPTION,
            List.of(role));
    boolean alreadyMember =
        adminUser.getUnscopedGroups().stream().anyMatch(g -> g.getId().equals(group.getId()));
    if (!alreadyMember) {
      adminUser.getUnscopedGroups().add(group);
      userService.saveUser(adminUser);
      log.info(
          "Enrolled admin user {} into the platform '{}' group",
          adminUser.getId(),
          PLATFORM_ADMIN_GROUP_NAME);
    }
    return group;
  }
}
