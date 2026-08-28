package io.openaev.integration;

import static io.openaev.aop.lock.LockResourceType.MANAGER_FACTORY;

import io.openaev.aop.lock.Lock;
import io.openaev.database.model.Tenant;
import io.openaev.multitenancy.DependenciesManager;
import io.openaev.processor.MigrationProcessor;
import io.openaev.rest.injector_contract.InjectorContractService;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManagerFactory implements DependenciesManager {

  /**
   * Creation lives in a separate bean so the call always crosses the Spring proxy and its
   * {@code @Transactional} applies — a same-class call would silently bypass it.
   */
  private final ManagerCreator managerCreator;

  private final ConcurrentHashMap<String, Manager> managers = new ConcurrentHashMap<>();

  /**
   * Returns the {@link Manager} for the given tenant, creating and initializing it on first access.
   * One Manager instance is maintained per tenant.
   *
   * <p>Deliberately NOT {@code @Transactional}: a cache hit (the overwhelmingly common case) must
   * not open a transaction. On a miss, {@link ManagerCreator#createManager} joins the caller's
   * transaction when one exists, or opens its own read-write one otherwise; when the joined
   * transaction is read-only it skips the built-in registration write (see there for why {@code
   * REQUIRES_NEW} must not be used). The in-memory {@link Lock} serializes concurrent creation per
   * tenant, and creation runs outside {@link ConcurrentHashMap#computeIfAbsent} so no map bin lock
   * is held across DB work; {@link ConcurrentHashMap#putIfAbsent} publishes the instance safely.
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
    Manager created = managerCreator.createManager(tenantId);
    Manager previous = managers.putIfAbsent(tenantId, created);
    return previous != null ? previous : created;
  }

  // -- TENANT DEPENDENCIES --

  /**
   * Creates (or retrieves) the {@link Manager} for the given tenant. Called by the {@link
   * DependenciesManager} framework on every new tenant creation.
   *
   * <p>Also registers all built-in connectors (injectors, executor) for the new tenant: {@link
   * ManagerCreator#createManager} joins the surrounding tenant-creation transaction ({@code
   * REQUIRED} propagation), so the registration rolls back with it. {@link
   * io.openaev.context.TenantContext} is already set to the new tenant by {@link
   * io.openaev.service.tenants.TenantService} before this method is called.
   */
  @Override
  public void createDependencyForTenant(Tenant tenant) {
    if (managers.containsKey(tenant.getId())) {
      return;
    }
    Manager created = managerCreator.createManager(tenant.getId());
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
