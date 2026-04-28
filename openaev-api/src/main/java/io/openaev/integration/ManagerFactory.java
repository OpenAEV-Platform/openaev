package io.openaev.integration;

import static io.openaev.aop.lock.LockResourceType.MANAGER_FACTORY;

import io.openaev.aop.lock.Lock;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import io.openaev.datapack.DataPackProcessor;
import io.openaev.multitenancy.DependenciesManager;
import io.openaev.multitenancy.DependenciesManagerException;
import io.openaev.rest.injector_contract.InjectorContractService;
import io.openaev.service.tenants.UserTenantService;
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
  private final List<BuiltinTenantRegistrable> builtinRegistrables;

  private volatile Manager manager = null;

  @Transactional
  @Lock(type = MANAGER_FACTORY, key = "manager-factory")
  public Manager getManager() {
    if (manager == null) {
      try {
        this.manager = new Manager(factories);
        this.manager.monitorIntegrations();
      } catch (Exception e) {
        throw new RuntimeException("Failed to initialize Manager", e);
      }
    }
    return this.manager;
  }

  // -- TENANT DEPENDENCIES --

  @Override
  public void createDependencyForTenant(Tenant tenant) throws DependenciesManagerException {

    String previousTenant = TenantContext.getCurrentTenant();
    try {
      TenantContext.setCurrentTenant(tenant.getId());
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
    return List.of(InjectorContractService.class, UserTenantService.class, DataPackProcessor.class);
  }
}
