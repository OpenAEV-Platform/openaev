package io.openaev.service.account;

import io.openaev.api.groups.dto.TenantGroupCreateInput;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Group;
import io.openaev.database.model.Role;
import io.openaev.database.model.User;
import io.openaev.service.*;
import io.openaev.service.platform.groups.PlatformGroupService;
import io.openaev.service.platform.roles.PlatformRoleService;
import io.openaev.service.tenants.TenantUserService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ensures the platform admin user holds full administrative privileges through admin groups: a
 * platform-scoped group (tenant = null) granting platform BYPASS and a per-tenant "Admin" group
 * granting tenant BYPASS, with the admin user enrolled in both.
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

  /** Not a {@link ReservedServiceAccount}: this role and group are not reserved. */
  public static final WellKnownAccess ADMIN_ACCESS =
      new WellKnownAccess(
          "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
          "Admin",
          "Full administrative access to the tenant.",
          Set.of(Capability.BYPASS),
          "2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e",
          "Admin",
          "Tenant administrators.");

  /** Platform-scoped (tenant = null): its ids are stored as is, not derived per tenant. */
  public static final WellKnownAccess PLATFORM_ADMIN_ACCESS =
      new WellKnownAccess(
          "3c4d5e6f-7a8b-4c9d-8e0f-2a3b4c5d6e7f",
          "Admin",
          "Full administrative access to the platform.",
          Set.of(Capability.BYPASS),
          "4d5e6f7a-8b9c-4d0e-9f1a-3b4c5d6e7f8a",
          "Administrators",
          "Platform administrators.");

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
  protected WellKnownAccess wellKnownAccess() {
    return ADMIN_ACCESS;
  }

  /**
   * Idempotently ensures the given tenant owns an admin group (with a BYPASS role), that the given
   * user belongs to that tenant, and that the user is a member of the group. Safe to call on every
   * platform startup.
   *
   * <p>Reuses, in this order: the well-known group while it holds a BYPASS role, left unchanged;
   * else the tenant's default Admin role and its Admin group, the ones the Flyway migration and the
   * default-roles datapack create, so a fresh install gets a single Admin role and group; else
   * creates them.
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
    Group group = resolveAdminGroup(tenantId);
    boolean alreadyMember =
        adminUser.getUnscopedGroups().stream().anyMatch(g -> g.getId().equals(group.getId()));
    if (!alreadyMember) {
      adminUser.getUnscopedGroups().add(group);
      userService.saveUser(adminUser);
      log.info(
          "Enrolled admin user {} into the '{}' group for tenant {}",
          adminUser.getId(),
          group.getName(),
          tenantId);
    }
    // Mirror AgentServiceAccountPrivilegeService: make sure the admin belongs to the tenant that
    // owns the group, so the self-heal also covers installs whose users_tenants row is missing. The
    // call is idempotent and clears the persistence context, so it runs after the group enrollment.
    tenantUserService.attachToTenant(adminUser.getId(), tenantId);
    return group;
  }

  /** The well-known id of the tenant's admin group, used when it is created and still free. */
  public static String adminGroupId(String tenantId) {
    return getUUIDFromName(ADMIN_ACCESS.groupId(), tenantId);
  }

  private Group resolveAdminGroup(String tenantId) {
    String groupId = adminGroupId(tenantId);
    Optional<Group> wellKnown = tenantGroupService.findByIdAndTenant(groupId, tenantId);
    if (wellKnown.isPresent() && wellKnown.get().getRoles().stream().anyMatch(this::hasBypass)) {
      log.info("Kept admin group {} of tenant {}", groupId, tenantId);
      return wellKnown.get();
    }
    Role role =
        tenantRoleService
            .findDefaultRole(ADMIN_ACCESS.roleName(), tenantId)
            .orElseGet(() -> createAdminRole(tenantId));
    Optional<Group> existing =
        tenantGroupService.findLargestByNameAndRole(ADMIN_ACCESS.groupName(), role, tenantId);
    if (existing.isPresent()) {
      log.info(
          "Reused admin group {} and role {} of tenant {}",
          existing.get().getId(),
          role.getId(),
          tenantId);
      return existing.get();
    }
    TenantGroupCreateInput input = new TenantGroupCreateInput();
    input.setName(ADMIN_ACCESS.groupName());
    input.setDescription(ADMIN_ACCESS.groupDescription());
    input.setDefaultUserAssignation(false);
    String newGroupId = wellKnown.isPresent() ? UUID.randomUUID().toString() : groupId;
    Group group =
        tenantGroupService.createInternalGroupWithRole(
            newGroupId, input, new ArrayList<>(List.of(role)), tenantId);
    log.info(
        "Created admin group {} bound to role {} of tenant {}",
        group.getId(),
        role.getId(),
        tenantId);
    return group;
  }

  private Role createAdminRole(String tenantId) {
    String roleId = getUUIDFromName(ADMIN_ACCESS.roleId(), tenantId);
    if (tenantRoleService.findByIdAndTenant(roleId, tenantId).isPresent()) {
      roleId = UUID.randomUUID().toString();
    }
    return tenantRoleService.createRoleInternal(
        roleId,
        ADMIN_ACCESS.roleName(),
        ADMIN_ACCESS.roleDescription(),
        ADMIN_ACCESS.capabilities(),
        tenantId);
  }

  private boolean hasBypass(Role role) {
    return role.getCapabilities().contains(Capability.BYPASS);
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
            PLATFORM_ADMIN_ACCESS.roleId(),
            PLATFORM_ADMIN_ACCESS.roleName(),
            PLATFORM_ADMIN_ACCESS.roleDescription(),
            PLATFORM_ADMIN_ACCESS.capabilities());
    Group group =
        platformGroupService.ensureInternalPlatformGroupWithRoles(
            PLATFORM_ADMIN_ACCESS.groupId(),
            PLATFORM_ADMIN_ACCESS.groupName(),
            PLATFORM_ADMIN_ACCESS.groupDescription(),
            List.of(role));
    boolean alreadyMember =
        adminUser.getUnscopedGroups().stream().anyMatch(g -> g.getId().equals(group.getId()));
    if (!alreadyMember) {
      adminUser.getUnscopedGroups().add(group);
      userService.saveUser(adminUser);
      log.info(
          "Enrolled admin user {} into the platform '{}' group",
          adminUser.getId(),
          PLATFORM_ADMIN_ACCESS.groupName());
    }
    return group;
  }
}
