package io.openaev.integration;

import static io.openaev.aop.lock.LockResourceType.MANAGER_FACTORY;

import io.openaev.aop.lock.Lock;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManagerFactory {
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

  /**
   * Registers all built-in components (injectors, executors, collectors) for a newly created
   * tenant. Runs <b>after</b> the tenant-creation transaction has committed, so:
   *
   * <ul>
   *   <li>The tenant row is visible (FK constraints satisfied)
   *   <li>The Hibernate session is fresh (no dirty entities from other managers)
   * </ul>
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
  public void onTenantCreated(TenantCreatedEvent event) {
    Tenant tenant = event.getTenant();
    String previousTenant = TenantContext.getCurrentTenant();
    try {
      TenantContext.setCurrentTenant(tenant.getId());

      for (BuiltinTenantRegistrable registrable : builtinRegistrables) {
        registrable.registerForTenant();
      }

      log.info("Successfully registered built-in integrations for tenant '{}'", tenant.getName());
    } catch (Exception e) {
      log.error(
          "Failed to register built-in integrations for tenant '{}': {}",
          tenant.getName(),
          e.getMessage(),
          e);
    } finally {
      TenantContext.setCurrentTenant(previousTenant);
    }
  }
}
