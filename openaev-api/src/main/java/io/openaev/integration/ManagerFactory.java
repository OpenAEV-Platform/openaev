package io.openaev.integration;

import static io.openaev.aop.lock.LockResourceType.MANAGER_FACTORY;
import static io.openaev.helper.StreamHelper.fromIterable;

import io.openaev.aop.lock.Lock;
import io.openaev.context.TenantContext;
import io.openaev.database.audit.TenantAssertionControl;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.TenantRepository;
import io.openaev.datapack.DataPackProcessor;
import io.openaev.multitenancy.DependenciesManager;
import io.openaev.multitenancy.DependenciesManagerException;
import io.openaev.rest.injector_contract.InjectorContractService;
import io.openaev.service.PreviewFeatureService;
import io.openaev.service.tenants.UserTenantService;
import jakarta.persistence.EntityManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManagerFactory implements DependenciesManager {
  private final List<IntegrationFactory> factories;
  private final List<BuiltinTenantRegistrable> builtinRegistrables;
  private final TenantRepository tenantRepository;
  private final PreviewFeatureService previewFeatureService;
  private final EntityManager entityManager;

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
   * Ensures built-in connectors are registered for every existing tenant. This covers tenants
   * created before the builtin registration mechanism was introduced (e.g. the default tenant
   * created by Flyway migration) and is idempotent — safe to run on every startup.
   */
  private void registerBuiltinsForAllTenants() {
    List<Tenant> tenants = fromIterable(tenantRepository.findAll());
    TenantAssertionControl.suppress();
    try {
      for (Tenant tenant : tenants) {
        try {
          createDependencyForTenant(tenant);
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
  @Transactional
  public void createDependencyForTenant(Tenant tenant) throws DependenciesManagerException {

    String previousTenant = TenantContext.getCurrentTenant();
    try {
      TenantContext.setCurrentTenant(tenant.getId());
      // Re-enable the Hibernate filter with the correct tenant — the AOP aspect already activated
      // it with the previous tenant value before this method body runs.
      entityManager
          .unwrap(Session.class)
          .enableFilter("tenantFilter")
          .setParameter("tenantId", tenant.getId());
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
    } finally {
      TenantContext.setCurrentTenant(previousTenant);
    }
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
