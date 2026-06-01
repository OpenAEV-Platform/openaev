package io.openaev.integration;

import static io.openaev.aop.lock.LockResourceType.MANAGER_FACTORY;

import io.openaev.aop.lock.Lock;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import io.openaev.datapack.DataPackProcessor;
import io.openaev.multitenancy.DependenciesManager;
import io.openaev.multitenancy.DependenciesManagerException;
import io.openaev.rest.injector_contract.InjectorContractService;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
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
  private final List<BuiltinTenantRegistrable> builtinComponents;
  private final EntityManager entityManager;

  private final ConcurrentMap<String, Manager> managers = new ConcurrentHashMap<>();

  @Transactional
  @Lock(type = MANAGER_FACTORY, key = "#tenantId")
  public Manager getManager(String tenantId) {
    return managers.computeIfAbsent(tenantId, this::createManager);
  }

  // -- TENANT DEPENDENCIES --

  @Override
  public void createDependencyForTenant(Tenant tenant) throws DependenciesManagerException {
    // Directly invoke logic (not via getManager() to avoid self-call proxy bypass issues).
    managers.computeIfAbsent(tenant.getId(), this::createManager);
  }

  @Override
  public void deleteDependencyForTenant(String tenantId) {
    managers.remove(tenantId);
    for (BuiltinTenantRegistrable component : builtinComponents) {
      component.unregisterForTenant(tenantId);
    }
    // DB rows (collectors, etc.) are deleted by CASCADE on tenant removal.
  }

  @Override
  public List<Class<? extends DependenciesManager>> getPrerequisite() {
    return List.of(InjectorContractService.class, DataPackProcessor.class);
  }

  // -- INTERNAL --

  private Manager createManager(String tenantId) {
    try {
      TenantContext.setCurrentTenant(tenantId);
      entityManager
          .unwrap(Session.class)
          .enableFilter("tenantFilter")
          .setParameter("tenantId", tenantId);

      for (BuiltinTenantRegistrable component : builtinComponents) {
        component.registerForTenant(tenantId);
      }
      Manager manager = new Manager(factories, tenantId);
      manager.monitorIntegrations();
      return manager;
    } catch (Exception e) {
      throw new RuntimeException("Failed to initialize Manager for tenant " + tenantId, e);
    }
  }
}
