package io.openaev.processor.datapack;

import io.openaev.database.model.Capability;
import io.openaev.database.model.Role;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.RoleRepository;
import io.openaev.service.DataPackService;
import io.openaev.service.TenantGroupService;
import io.openaev.service.account.AdminPrivilegeService;
import java.util.List;
import java.util.Set;
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
 * <p>Groups: find-or-create on both sides, for Manager and Observer only: the Admin group belongs
 * to {@link AdminPrivilegeService}, which runs after the datapacks at every start. A group is bound
 * to the existing same-named role, and the role is created from the preset only when missing. An
 * existing group (an admin may already have created one) is never duplicated nor modified.
 *
 * <p>Capabilities: a Manager/Observer role is aligned with the preset when it holds nothing outside
 * it, so the alignment only ever adds capabilities. A role an admin extended beyond the preset is
 * left untouched.
 */
@Component
@Slf4j
public class V20261001_Default_tenant_roles_and_groups extends DataPack {

  private final RoleRepository roleRepository;
  private final TenantGroupService tenantGroupService;

  public V20261001_Default_tenant_roles_and_groups(
      DataPackService dataPackService,
      RoleRepository roleRepository,
      TenantGroupService tenantGroupService) {
    super(dataPackService);
    this.roleRepository = roleRepository;
    this.tenantGroupService = tenantGroupService;
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
      tenantGroupService.findOrCreateDefaultGroups(tenantId);
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
}
