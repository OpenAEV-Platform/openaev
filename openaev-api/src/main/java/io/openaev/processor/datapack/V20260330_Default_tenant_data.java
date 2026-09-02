package io.openaev.processor.datapack;

import static io.openaev.config.SessionHelper.currentUser;

import io.openaev.context.TenantContext;
import io.openaev.database.model.*;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.CweRepository;
import io.openaev.database.repository.GroupRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.database.repository.VulnerabilityRepository;
import io.openaev.service.DataPackService;
import io.openaev.service.TenantRoleService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class V20260330_Default_tenant_data extends DataPack {

  private final VulnerabilityRepository vulnerabilityRepository;
  private final CweRepository cweRepository;
  private final TenantRoleService tenantRoleService;
  private final GroupRepository groupRepository;
  private final UserRepository userRepository;
  @PersistenceContext private EntityManager entityManager;

  public V20260330_Default_tenant_data(
      DataPackService dataPackService,
      VulnerabilityRepository vulnerabilityRepository,
      CweRepository cweRepository,
      TenantRoleService tenantRoleService,
      GroupRepository groupRepository,
      UserRepository userRepository) {
    super(dataPackService);
    this.cweRepository = cweRepository;
    this.vulnerabilityRepository = vulnerabilityRepository;
    this.tenantRoleService = tenantRoleService;
    this.groupRepository = groupRepository;
    this.userRepository = userRepository;
  }

  @Override
  public boolean doProcess(Tenant tenant) {
    try {
      if (!Tenant.DEFAULT_TENANT_UUID.equals(TenantContext.getCurrentTenant())) {
        // Init vulnerabilities
        PresetTenantData.createDefaultVulnerabilityCwes()
            .forEach(
                input -> {
                  Cwe cwe = cweRepository.save(input.cwe());
                  Vulnerability vulnerability = input.vulnerability();
                  vulnerability.setCwes(new ArrayList<>(List.of(cwe)));
                  vulnerabilityRepository.save(vulnerability);
                });
        // Init roles/groups and the current user (if he exists) to admin group/role for the new
        // tenant created
        PresetTenantData.DEFAULT_ROLES.forEach(
            (roleName, capabilities) -> {
              Role role =
                  tenantRoleService.createRoleInternal(
                      UUID.randomUUID().toString(),
                      roleName,
                      roleName,
                      capabilities,
                      TenantContext.getCurrentTenant());
              Group group = new Group();
              group.setName(roleName);
              group.setDescription(roleName);
              group.setDefaultUserAssignation(false);
              group.setTenant(
                  entityManager.getReference(Tenant.class, TenantContext.getCurrentTenant()));
              group.setRoles(List.of(role));
              if (PresetTenantData.ADMIN.equals(roleName)) {
                userRepository
                    .findById(currentUser().getId())
                    .ifPresent(user -> group.setUsers(List.of(user)));
              }
              groupRepository.save(group);
            });
      }
      return true;
    } catch (Exception e) {
      log.error("Unexpected error during DataPack 20260330 initialization.", e);
      return false;
    }
  }
}
