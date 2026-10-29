package io.openaev.processor.datapack;

import static java.util.stream.Collectors.toSet;

import io.openaev.database.model.Capability;
import io.openaev.database.model.Group;
import io.openaev.database.model.Role;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.GroupRepository;
import io.openaev.database.repository.RoleRepository;
import io.openaev.service.AbstractPrivilegeService;
import io.openaev.service.DataPackService;
import io.openaev.service.TenantRoleService;
import io.openaev.service.account.AdminPrivilegeService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Brings the default tenant's Admin/Manager/Observer roles and groups in line with every tenant
 * provisioned at runtime.
 *
 * <p>{@link V20260330_Default_tenant_data} creates those roles and groups for new tenants but skips
 * the default tenant, whose roles were seeded by Flyway ({@code V4_29__Add_default_roles}) without
 * any group, and with a frozen capability list that has drifted from {@link
 * PresetTenantData#DEFAULT_ROLES} ever since.
 *
 * <p>Groups: find-or-create on both sides. A group is bound to the existing same-named role, and
 * the role is created from the preset only when missing. An existing group (an admin may already
 * have created one) is never duplicated nor modified.
 *
 * <p>Capabilities: a Manager/Observer role is aligned with the preset when it holds nothing outside
 * it, so the alignment only ever adds capabilities. A role an admin extended beyond the preset is
 * left untouched.
 */
@Component
@Slf4j
public class V20261001_Default_tenant_roles_and_groups extends DataPack {

  private final RoleRepository roleRepository;
  private final GroupRepository groupRepository;
  private final TenantRoleService tenantRoleService;
  @PersistenceContext private EntityManager entityManager;

  public V20261001_Default_tenant_roles_and_groups(
      DataPackService dataPackService,
      RoleRepository roleRepository,
      GroupRepository groupRepository,
      TenantRoleService tenantRoleService) {
    super(dataPackService);
    this.roleRepository = roleRepository;
    this.groupRepository = groupRepository;
    this.tenantRoleService = tenantRoleService;
  }

  @Override
  protected boolean doProcess(Tenant tenant) {
    // Runtime-provisioned tenants already got them from V20260330_Default_tenant_data
    if (!Tenant.DEFAULT_TENANT_UUID.equals(tenant.getId())) {
      return true;
    }
    try {
      String tenantId = tenant.getId();
      List<Role> roles = roleRepository.findAllByTenantId(tenantId);
      roles.forEach(role -> alignCapabilitiesWithPreset(role, tenantId));
      Set<String> groupNames =
          groupRepository.findAllByTenantId(tenantId).stream().map(Group::getName).collect(toSet());
      PresetTenantData.DEFAULT_ROLES.forEach(
          (roleName, capabilities) -> {
            if (groupNames.contains(roleName)) {
              log.info("Group {} already exists for tenant {}, skipping", roleName, tenantId);
              return;
            }
            Role role =
                findDefaultRole(roles, roleName, tenantId)
                    .orElseGet(
                        () ->
                            tenantRoleService.createRoleInternal(
                                UUID.randomUUID().toString(),
                                roleName,
                                roleName,
                                capabilities,
                                tenantId));
            Group group = new Group();
            group.setName(roleName);
            group.setDescription(roleName);
            group.setDefaultUserAssignation(false);
            group.setTenant(entityManager.getReference(Tenant.class, tenantId));
            group.setRoles(List.of(role));
            groupRepository.save(group);
            log.info("Created group {} for tenant {}", roleName, tenantId);
          });
      return true;
    } catch (Exception e) {
      log.error("Unexpected error during DataPack 20261001 initialization.", e);
      return false;
    }
  }

  private void alignCapabilitiesWithPreset(Role role, String tenantId) {
    Set<Capability> preset = PresetTenantData.DEFAULT_ROLES.get(role.getName());
    // Admin already holds BYPASS: never grant it through a subset match (an empty role is one)
    if (preset == null || PresetTenantData.ADMIN.equals(role.getName())) {
      return;
    }
    Set<Capability> target = Capability.resolveWithParents(preset);
    Set<Capability> current = Capability.resolveWithParents(role.getCapabilities());
    if (current.equals(target)) {
      return;
    }
    if (!target.containsAll(current)) {
      log.info(
          "Role '{}' ({}) of tenant {} holds capabilities outside the preset — leaving it"
              + " untouched.",
          role.getName(),
          role.getId(),
          tenantId);
      return;
    }
    role.setCapabilities(preset);
    roleRepository.save(role);
    log.info(
        "Aligned capabilities of default role '{}' ({}) of tenant {} with the preset.",
        role.getName(),
        role.getId(),
        tenantId);
  }

  /**
   * Skips the well-known "Admin" role owned by the bootstrap "Administrators" group (see {@link
   * AdminPrivilegeService}), so the Admin group binds to the Flyway-seeded one instead of sharing
   * it.
   */
  private Optional<Role> findDefaultRole(List<Role> roles, String roleName, String tenantId) {
    String bootstrapAdminRoleId =
        AbstractPrivilegeService.getUUIDFromName(AdminPrivilegeService.ADMIN_ROLE_ID, tenantId);
    return roles.stream()
        .filter(role -> roleName.equals(role.getName()))
        .filter(role -> !bootstrapAdminRoleId.equals(role.getId()))
        .findFirst();
  }
}
