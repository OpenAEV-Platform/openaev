package io.openaev.service.tenants;

import static io.openaev.config.SessionHelper.currentUser;

import io.openaev.database.repository.TenantRepository;
import io.openaev.multitenancy.DependenciesManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserTenantDependencyManager implements DependenciesManager {

  private final TenantRepository tenantRepository;

  @Override
  public void createDependencyForTenant(String tenantId) {
    tenantRepository.addUserToTenant(currentUser().getId(), tenantId);
  }

  @Override
  public void deleteDependencyForTenant(String tenantId) {
    // users_tenants rows are cascade-deleted via FK on tenants table
  }
}
