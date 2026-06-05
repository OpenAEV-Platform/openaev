package io.openaev.integration;

import static io.openaev.aop.lock.LockResourceType.MANAGER_FACTORY;
import static io.openaev.helper.StreamHelper.fromIterable;

import io.openaev.aop.lock.Lock;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.TenantRepository;
import io.openaev.datapack.DataPackProcessor;
import io.openaev.multitenancy.DependenciesManager;
import io.openaev.multitenancy.DependenciesManagerException;
import io.openaev.rest.injector_contract.InjectorContractService;
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
  private final TenantRepository tenantRepository;

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
   * Loads all tenants from the database, ensures a {@link Manager} exists for each one (creating it
   * lazily if needed), and calls {@link Manager#monitorIntegrations()} on each. This is the entry
   * point for the {@link io.openaev.scheduler.jobs.ManagerIntegrationsSyncJob}: driving from the DB
   * guarantees that tenants created between restarts are not missed, even though the in-memory map
   * starts empty after every restart.
   *
   * <p>{@link TenantContext} is set per tenant before each {@link Manager#monitorIntegrations()}
   * call because the Hibernate tenant filter and {@link
   * io.openaev.database.audit.TenantBaseListener} are wired to {@link TenantContext}, not to method
   * arguments. Without it, {@code findById()} inside {@link
   * io.openaev.integration.Integration#initialise()} returns {@code null} and the integration stops
   * instead of starting.
   *
   * <p>Not annotated with {@code @Transactional} — each sub-call ({@link
   * io.openaev.integration.Integration#initialise()}) manages its own transaction, keeping DB
   * connections short-lived and failures isolated per tenant.
   */
  public void monitorAllTenants() {
    List<Tenant> tenants = fromIterable(tenantRepository.findAll());
    for (Tenant tenant : tenants) {
      log.info("==> Monitoring tenant " + tenant.getName());
      TenantContext.setCurrentTenant(tenant.getId());
      try {
        getManager(tenant.getId()).monitorIntegrations();
      } catch (Exception e) {
        log.error(
            "==> monitorAllTenants: error monitoring tenant '{}': {}",
            tenant.getName(),
            e.getMessage(),
            e);
        // do not rethrow; continue with remaining tenants
      } finally {
        TenantContext.clearCurrentTenant();
      }
    }
  }

  /**
   * Creates a new {@link Manager} for the given tenant. Integration discovery and startup are
   * handled by the next {@link io.openaev.scheduler.jobs.ManagerIntegrationsSyncJob} cycle via
   * {@link #monitorAllTenants()} — no immediate {@link Manager#monitorIntegrations()} call here to
   * avoid connecting to external services (Caldera, Tanium, etc.) during bean initialization or
   * tenant creation, where those services may not be reachable.
   */
  private Manager createManager(String tenantId) {
    try {
      return new Manager(tenantId, factories);
    } catch (Exception e) {
      throw new RuntimeException("Failed to initialize Manager for tenant " + tenantId, e);
    }
  }

  /**
   * Returns the IDs of all tenants currently in the database. Used for logging/diagnostics.
   *
   * @return list of tenant IDs
   */
  @Transactional(readOnly = true)
  public List<String> getTenantIds() {
    return fromIterable(tenantRepository.findAll()).stream().map(Tenant::getId).toList();
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
  public void createDependencyForTenant(Tenant tenant) throws DependenciesManagerException {
    // Register built-in connectors (injectors/executor) for the new tenant.
    // TenantContext is already set by TenantService.create() at this point.
    for (BuiltinTenantRegistrable registrable : builtinRegistrables) {
      try {
        registrable.registerForTenant();
      } catch (Exception e) {
        throw new DependenciesManagerException(
            "Failed to register built-in connector for tenant " + tenant.getName(), e);
      }
    }
    // Create the Manager for this tenant (must run after registration so connectors exist in DB).
    managers.computeIfAbsent(tenant.getId(), this::createManager);
  }

  @Override
  public void deleteDependencyForTenant(String tenantId) {
    managers.remove(tenantId);
  }

  @Override
  public List<Class<? extends DependenciesManager>> getPrerequisite() {
    return List.of(InjectorContractService.class, DataPackProcessor.class);
  }
}
