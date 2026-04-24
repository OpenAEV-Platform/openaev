package io.openaev.service.tenants;

import static io.openaev.config.SessionHelper.currentUser;

import io.openaev.config.cache.TenantMembershipCacheManager;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.TenantRepository;
import io.openaev.multitenancy.DependenciesManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserTenantService implements DependenciesManager {

  private final TenantRepository tenantRepository;
  private final TenantMembershipCacheManager tenantMembershipCacheManager;

  @Override
  public void createDependencyForTenant(Tenant tenant) {
    tenantRepository.addUserToTenant(currentUser().getId(), tenant.getId());
    tenantMembershipCacheManager.evict(currentUser().getId(), tenant.getId());
  }

  @Override
  public void deleteDependencyForTenant(String tenantId) {
    // users_tenants rows are cascade-deleted via FK on tenants table
  }
}
