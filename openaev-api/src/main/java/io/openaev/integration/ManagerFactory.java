package io.openaev.integration;

import static io.openaev.aop.lock.LockResourceType.MANAGER_FACTORY;
import static io.openaev.helper.StreamHelper.fromIterable;

import io.openaev.aop.lock.Lock;
import io.openaev.database.audit.TenantAssertionControl;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.TenantRepository;
import io.openaev.datapack.DataPackProcessor;
import io.openaev.multitenancy.DependenciesManager;
import io.openaev.multitenancy.DependenciesManagerException;
import io.openaev.rest.injector_contract.InjectorContractService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManagerFactory implements DependenciesManager {
  private final List<IntegrationFactory> factories;
  private final TenantRepository tenantRepository;
  private final TenantRegistrationExecutor tenantRegistrationExecutor;

  private volatile Manager manager = null;

  @Transactional
  @Lock(type = MANAGER_FACTORY, key = "manager-factory")
  public Manager getManager() {
    if (manager == null) {
      try {
        registerBuiltinsForAllTenants();
        this.manager = new Manager(factories);
        this.manager.monitorIntegrations();
      } catch (Exception e) {
        throw new RuntimeException("Failed to initialize Manager", e);
      }
    }
    return this.manager;
  }

  /**
   * Ensures built-in connectors are registered for every existing tenant. Each tenant registration
   * runs in its own transaction and persistence context (via {@link TenantRegistrationExecutor}) to
   * avoid JPA entity identity collisions when connector IDs are reused across tenants.
   */
  private void registerBuiltinsForAllTenants() {
    List<Tenant> tenants = fromIterable(tenantRepository.findAll());
    TenantAssertionControl.suppress();
    try {
      for (Tenant tenant : tenants) {
        try {
          // Use isolated transaction per tenant to avoid JPA L1 cache identity collisions
          // (connector IDs are reused across tenants).
          tenantRegistrationExecutor.registerForTenantIsolated(tenant);
        } catch (DependenciesManagerException e) {
          log.error(
              "Failed to register built-in connectors for tenant '{}': {}",
              tenant.getName(),
              e.getMessage(),
              e);
        }
      }
    } finally {
      TenantAssertionControl.restore();
    }
  }

  // -- TENANT DEPENDENCIES --

  @Override
  public void createDependencyForTenant(Tenant tenant) throws DependenciesManagerException {
    tenantRegistrationExecutor.registerForTenant(tenant);
  }

  @Override
  public void deleteDependencyForTenant(String tenantId) {
    // Built-in connectors are tenant-scoped and deleted by CASCADE.
  }

  @Override
  public List<Class<? extends DependenciesManager>> getPrerequisite() {
    return List.of(InjectorContractService.class, DataPackProcessor.class);
  }
}
