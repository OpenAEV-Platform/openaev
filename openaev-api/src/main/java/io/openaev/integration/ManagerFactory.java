package io.openaev.integration;

import static io.openaev.aop.lock.LockResourceType.MANAGER_FACTORY;

import io.openaev.aop.lock.Lock;
import io.openaev.database.model.Tenant;
import io.openaev.multitenancy.DependenciesManager;
import io.openaev.processor.MigrationProcessor;
import io.openaev.rest.injector_contract.InjectorContractService;
import jakarta.validation.constraints.NotBlank;
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
   * Creates a new {@link Manager} for the given tenant. Integration discovery and startup are
   * handled by the next {@link io.openaev.scheduler.jobs.ManagerIntegrationsSyncJob} cycle — no
   * immediate {@link Manager#monitorIntegrations()} call here to avoid connecting to external
   * services (Caldera, Tanium, etc.) during bean initialization or tenant creation, where those
   * services may not be reachable.
   */
  private Manager createManager(@NotBlank final String tenantId) {
    try {
      for (BuiltinTenantRegistrable component : builtinRegistrables) {
        component.registerForTenant(tenantId);
      }
      Manager manager = new Manager(tenantId, factories);
      manager.monitorIntegrations();
      return manager;
    } catch (Exception e) {
      throw new RuntimeException("Failed to initialize Manager for tenant " + tenantId, e);
    }
  }

  // -- TENANT DEPENDENCIES --

  /**
   * Creates (or retrieves) the {@link Manager} for the given tenant. Called by the {@link
   * DependenciesManager} framework on every new tenant creation.
   *
   * <p>Also registers all built-in connectors (injectors, executor) for the new tenant. {@link
   * io.openaev.context.TenantContext} is already set to the new tenant by {@link
   * io.openaev.service.tenants.TenantService} before this method is called.
   */
  @Override
  public void createDependencyForTenant(Tenant tenant) {
    // Create the Manager for this tenant (must run after registration so connectors exist in DB).
    managers.computeIfAbsent(tenant.getId(), this::createManager);
  }

  @Override
  public void deleteDependencyForTenant(String tenantId) {
    managers.remove(tenantId);
  }

  @Override
  public List<Class<? extends DependenciesManager>> getPrerequisite() {
    return List.of(InjectorContractService.class, MigrationProcessor.class);
  }
}
