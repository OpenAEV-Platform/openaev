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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
   * not open a transaction. On a miss, creation is delegated to {@link #createManager} through the
   * Spring proxy so its {@code @Transactional} applies: it joins the caller's transaction when one
   * exists, or opens its own read-write one otherwise. Creation deliberately does NOT use {@code
   * REQUIRES_NEW}: that opened a second DB session whose connector-row writes blocked forever on
   * rows the caller's still-uncommitted transaction already held (e.g. {@code @Transactional} API
   * tests that insert connectors and then hit a read endpoint) — a self-deadlock PostgreSQL cannot
   * detect because the waiting session's owner is the very thread suspended inside it. When the
   * joined transaction is read-only, {@link #createManager} skips the registration write instead
   * (see there). The in-memory {@link Lock} serializes concurrent creation per tenant, and creation
   * runs outside {@link ConcurrentHashMap#computeIfAbsent} so no map bin lock is held across DB
   * work; {@link ConcurrentHashMap#putIfAbsent} publishes the instance safely.
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
   * Creates a new {@link Manager} for the given tenant, joining the caller's transaction when one
   * exists (never {@code REQUIRES_NEW} — a second DB session deadlocks against the caller's
   * uncommitted connector rows, see {@link #getManager}).
   *
   * <p>Registering the tenant's built-in connectors is a write, so it is skipped when the joined
   * transaction is read-only (e.g. a first-access {@code GET /api/collectors} that runs {@code
   * readOnly} — previously this failed with "cannot execute DELETE in a read-only transaction").
   * Built-ins are guaranteed by the read-write paths instead: {@link #createDependencyForTenant} on
   * tenant creation, and {@link io.openaev.scheduler.jobs.ManagerIntegrationsSyncJob} which calls
   * {@link #getManager} inside a read-write tenant transaction on every cycle.
   *
   * <p>Public (not private) so {@link #getManager} can invoke it through the Spring proxy for
   * {@code @Transactional} to apply when the caller has no transaction. {@link
   * #createDependencyForTenant} deliberately calls it via {@code this} (self-invocation, proxy
   * bypassed) so it joins the surrounding tenant-creation transaction and rolls back with it.
   */
  @Transactional
  public Manager createManager(@NotBlank final String tenantId) {
    try {
      if (!TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
        for (BuiltinTenantRegistrable component : builtinRegistrables) {
          component.registerForTenant(tenantId);
        }
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
