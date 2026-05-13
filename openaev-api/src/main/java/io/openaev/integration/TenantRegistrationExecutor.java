package io.openaev.integration;

import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import io.openaev.multitenancy.DependenciesManagerException;
import jakarta.persistence.EntityManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Executes built-in tenant registration. Provides two entry points:
 *
 * <ul>
 *   <li>{@link #registerForTenantIsolated(Tenant)} — REQUIRES_NEW, used at startup when iterating
 *       over multiple existing tenants. Sets up TenantContext, Hibernate filter, and RLS in its own
 *       transaction to avoid JPA L1 cache identity collisions.
 *   <li>{@link #registerForTenant(Tenant)} — joins the current transaction, used when creating a
 *       new tenant. Assumes the caller has already set TenantContext, Hibernate filter, and RLS
 *       (e.g. TenantService.create()).
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantRegistrationExecutor {

  private final List<BuiltinTenantRegistrable> builtinRegistrables;
  private final EntityManager entityManager;

  /**
   * Registers built-in connectors in a NEW isolated transaction. Use this when processing multiple
   * tenants in a loop (startup path) to avoid persistence context collisions. We need to set
   * current tenant (Filter/RLS/TenantContext with ThreadLocal) as we're not coming from
   * TenantService.create As the transaction is closed we don't need to clean up on finally for
   * ((Filter/RLS) but we need to make sure ThreadLocal is reset to previous value Note: the
   * register is done sequentially
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
  public void registerForTenantIsolated(Tenant tenant) throws DependenciesManagerException {
    String previousTenant = TenantContext.getCurrentTenant();
    try {
      TenantContext.setCurrentTenant(tenant.getId());
      Session session = entityManager.unwrap(Session.class);
      session.enableFilter("tenantFilter").setParameter("tenantId", tenant.getId());
      // Sync PostgreSQL RLS session variable so that row-level security policies
      // use the correct tenant on this JDBC connection (mirrors TenantInterceptor behavior).
      session.doWork(
          connection -> {
            try (var stmt =
                connection.prepareStatement("SELECT set_config('app.current_tenant', ?, false)")) {
              stmt.setString(1, tenant.getId());
              stmt.execute();
            }
          });

      log.info("Register built-in connector(s) for tenant'{}'", tenant.getName());

      executeRegistrables(tenant);
    } finally {
      // REQUIRES_NEW cleans up Session/JDBC automatically, but TenantContext is a ThreadLocal
      // that survives beyond the transaction — must be restored manually.
      TenantContext.setCurrentTenant(previousTenant);
    }
  }

  /**
   * Registers built-in connectors in the CURRENT transaction. We don't need to set current tenant
   * (Filter/RLS/TenantContext) as we're not coming from TenatnService.create Assumes the caller has
   * already set up TenantContext, Hibernate filter, and RLS on the current Session (e.g.
   * TenantService.create()).
   */
  @Transactional(rollbackFor = Exception.class)
  public void registerForTenant(Tenant tenant) throws DependenciesManagerException {
    executeRegistrables(tenant);
  }

  private void executeRegistrables(Tenant tenant) throws DependenciesManagerException {
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
}
