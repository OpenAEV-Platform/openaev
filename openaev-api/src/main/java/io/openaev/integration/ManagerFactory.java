package io.openaev.integration;

import static io.openaev.aop.lock.LockResourceType.MANAGER_FACTORY;

import io.openaev.aop.lock.Lock;
import io.openaev.database.model.Tenant;
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
  private final List<BuiltinTenantRegistrable> builtinRegistrables;

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
   * Creates and fully initializes a new {@link Manager} for the given tenant. Built-in connector
   * registration is intentionally <b>not</b> performed here — it is the responsibility of the
   * {@link DependenciesManager} framework: {@link #createDependencyForTenant(Tenant)} is called at
   * application startup (for all existing tenants) and on every new tenant creation, so built-ins
   * are guaranteed to exist before the first {@link #getManager(String)} call.
   *
   * @param tenantId the tenant identifier
   * @return the newly created and initialized Manager
   */
  private Manager createManager(String tenantId) {
    try {
      Manager manager = new Manager(tenantId, factories);
      manager.monitorIntegrations();
      return manager;
    } catch (Exception e) {
      throw new RuntimeException("Failed to initialize Manager for tenant " + tenantId, e);
    }
  }

  // -- TENANT DEPENDENCIES --

  /**
   * Registers built-in connectors for the given tenant in the current transaction. Called by the
   * {@link DependenciesManager} framework at application startup (for all existing tenants) and on
   * every new tenant creation, ensuring built-ins exist before the first {@link
   * #getManager(String)} call.
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public void createDependencyForTenant(Tenant tenant) throws DependenciesManagerException {
    for (BuiltinTenantRegistrable registrable : builtinRegistrables) {
      try {
        registrable.registerForTenant();
      } catch (Exception e) {
        throw new DependenciesManagerException(
            "Failed to register built-in connector %s for tenant %s"
                .formatted(registrable.getClass().getSimpleName(), tenant.getName()),
            e);
      }
    }
    log.info(
        "Successfully registered {} built-in connector(s) for tenant '{}'",
        builtinRegistrables.size(),
        tenant.getName());
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
