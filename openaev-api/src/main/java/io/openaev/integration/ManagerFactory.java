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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManagerFactory implements DependenciesManager {
  private final List<IntegrationFactory> factories;
  private final List<BuiltinTenantRegistrable> builtinRegistrables;

  /**
   * Self-reference used to invoke {@link #createManager} through the Spring proxy so its {@code
   * REQUIRES_NEW} transaction actually applies. An {@link ObjectProvider} is a lazy lookup, so it
   * does not create a circular dependency at construction time.
   */
  private final ObjectProvider<ManagerFactory> self;

  private final ConcurrentHashMap<String, Manager> managers = new ConcurrentHashMap<>();

  /**
   * Returns the {@link Manager} for the given tenant, creating and initializing it on first access.
   * One Manager instance is maintained per tenant.
   *
   * <p>Deliberately NOT {@code @Transactional}: a cache hit (the overwhelmingly common case) must
   * not open a transaction, and — crucially — first-access creation must not run inside the
   * caller's transaction. Read endpoints (e.g. {@code GET /api/collectors}) run {@code readOnly},
   * and creation registers built-in injectors (which writes); joining that read-only transaction
   * made the write fail with "cannot execute DELETE in a read-only transaction" and then aborted
   * the whole request. Creation is therefore delegated to {@link #createManager} in its own {@code
   * REQUIRES_NEW} read-write transaction. The in-memory {@link Lock} still serializes concurrent
   * creation per tenant.
   *
   * <p>Creation runs <b>outside</b> {@link ConcurrentHashMap#computeIfAbsent}: that method holds
   * the map's bin lock for the whole mapping function, and running the {@code REQUIRES_NEW}
   * transaction (a DB write to the tenant's connector rows) under that lock can deadlock against a
   * concurrent tenant-creation thread that holds those rows while waiting on the same bin — a cycle
   * PostgreSQL cannot detect because one side is a JVM lock. The per-tenant {@link Lock} already
   * guarantees {@link #createManager} runs at most once per tenant, and {@link
   * ConcurrentHashMap#putIfAbsent} publishes the instance safely.
   *
   * @param tenantId the tenant identifier
   * @return the Manager for that tenant
   */
  @Lock(type = MANAGER_FACTORY, key = "#tenantId")
  public Manager getManager(String tenantId) {
    Manager existing = managers.get(tenantId);
    if (existing != null) {
      return existing;
    }
    Manager created = self.getObject().createManager(tenantId);
    Manager previous = managers.putIfAbsent(tenantId, created);
    return previous != null ? previous : created;
  }

  /**
   * Creates a new {@link Manager} for the given tenant. Runs in its own {@code REQUIRES_NEW}
   * read-write transaction so that registering the tenant's built-in connectors (a write) succeeds
   * and stays isolated from any read-only caller transaction (see {@link #getManager}).
   *
   * <p>Integration discovery and startup are handled by the next {@link
   * io.openaev.scheduler.jobs.ManagerIntegrationsSyncJob} cycle — no immediate {@link
   * Manager#monitorIntegrations()} call here to avoid connecting to external services (Caldera,
   * Tanium, etc.) during bean initialization or tenant creation, where those services may not be
   * reachable.
   *
   * <p>Public (not private) so {@link #getManager} can invoke it through the Spring proxy for the
   * {@code REQUIRES_NEW} transaction to apply. {@link #createDependencyForTenant} deliberately
   * calls it via {@code this} (self-invocation, proxy bypassed) so it joins the surrounding
   * tenant-creation transaction and rolls back with it.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Manager createManager(@NotBlank final String tenantId) {
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
    // Invoked via `this` (proxy bypassed) so createManager joins the surrounding tenant-creation
    // transaction and rolls back with it. Kept outside computeIfAbsent so the map's bin lock is not
    // held across the DB write (see getManager for the deadlock this avoids).
    if (managers.containsKey(tenant.getId())) {
      return;
    }
    Manager created = this.createManager(tenant.getId());
    managers.putIfAbsent(tenant.getId(), created);
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
