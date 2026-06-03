package io.openaev.integration;

import static io.openaev.aop.lock.LockResourceType.MANAGER_FACTORY;

import io.openaev.aop.lock.Lock;
import io.openaev.database.audit.TenantAssertionControl;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.TenantRepository;
import io.openaev.datapack.DataPackProcessor;
import io.openaev.multitenancy.DependenciesManager;
import io.openaev.multitenancy.DependenciesManagerException;
import io.openaev.rest.injector_contract.InjectorContractService;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
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

  private final ConcurrentHashMap<String, Manager> managers = new ConcurrentHashMap<>();

  /**
   * Returns the {@link Manager} for the given tenant, creating and initializing it on first access.
   * One Manager instance is maintained per tenant.
   *
   * @param tenantId the tenant identifier
   * @return the Manager for that tenant
   */
  @Transactional
  @Lock(type = MANAGER_FACTORY, key = "#tenantId")
  public Manager getManager(String tenantId) {
    return managers.computeIfAbsent(tenantId, this::createManager);
  }

  /**
   * Returns all currently active {@link Manager} instances across all tenants. Intended for
   * background jobs that need to operate on every tenant's manager (e.g. sync jobs).
   *
   * @return collection of all tenant managers
   */
  public Collection<Manager> getAllManagers() {
    return managers.values();
  }

  /**
   * Creates and fully initializes a new {@link Manager} for the given tenant. Registers built-in
   * connectors for that tenant, constructs the Manager, and runs the initial integration monitor.
   *
   * @param tenantId the tenant identifier
   * @return the newly created and initialized Manager
   */
  private Manager createManager(String tenantId) {
    try {
      registerBuiltinsForTenant(tenantId);
      Manager manager = new Manager(tenantId, factories);
      manager.monitorIntegrations();
      return manager;
    } catch (Exception e) {
      throw new RuntimeException("Failed to initialize Manager for tenant " + tenantId, e);
    }
  }

  /**
   * Ensures built-in connectors are registered for the given tenant. The registration runs in its
   * own transaction and persistence context (via {@link TenantRegistrationExecutor}) to avoid JPA
   * entity identity collisions when connector IDs are reused across tenants.
   *
   * @param tenantId the tenant identifier
   */
  private void registerBuiltinsForTenant(String tenantId) {
    Tenant tenant =
        tenantRepository
            .findById(tenantId)
            .orElseThrow(
                () -> new IllegalArgumentException("Tenant not found for id: " + tenantId));
    TenantAssertionControl.suppress();
    try {
      tenantRegistrationExecutor.registerForTenantIsolated(tenant);
    } catch (DependenciesManagerException e) {
      log.error(
          "Failed to register built-in connectors for tenant '{}': {}",
          tenant.getName(),
          e.getMessage(),
          e);
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
