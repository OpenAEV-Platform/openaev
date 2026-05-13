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
import org.springframework.transaction.annotation.Transactional;

/**
 * Executes built-in tenant registration. Provides two entry points:
 *
 * <ul>
 *   <li>{@link #registerForTenantIsolated(Tenant)} — startup path: switches tenant context,
 *       registers, then flushes/clears/restores.
 *   <li>{@link #registerForTenant(Tenant)} — tenant creation path: just registers (caller manages
 *       context).
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantRegistrationExecutor {

  private final List<BuiltinTenantRegistrable> builtinRegistrables;
  private final EntityManager entityManager;

  /**
   * Registers built-in connectors for a single tenant within the caller's transaction. Wraps {@link
   * #registerForTenant(Tenant)} with tenant context switch, flush/clear, and restore.
   */
  @Transactional(rollbackFor = Exception.class)
  public void registerForTenantIsolated(Tenant tenant) throws DependenciesManagerException {
    String previousTenant = TenantContext.getCurrentTenant();
    try {
      switchTenantContext(tenant.getId());
      registerForTenant(tenant);
      entityManager.flush();
      entityManager.clear();
    } finally {
      switchTenantContext(previousTenant);
    }
  }

  /**
   * Registers built-in connectors in the CURRENT transaction. Assumes the caller has already set up
   * TenantContext, Hibernate filter, and RLS (e.g. TenantService.create()).
   */
  @Transactional(rollbackFor = Exception.class)
  public void registerForTenant(Tenant tenant) throws DependenciesManagerException {
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

  /**
   * Sets ThreadLocal + Hibernate filter + PostgreSQL RLS in one call. No-op if tenantId is null.
   */
  private void switchTenantContext(String tenantId) {
    TenantContext.setCurrentTenant(tenantId);
    if (tenantId == null) {
      return;
    }
    Session session = entityManager.unwrap(Session.class);
    session.enableFilter("tenantFilter").setParameter("tenantId", tenantId);
    session.doWork(
        connection -> {
          try (var stmt =
              connection.prepareStatement("SELECT set_config('app.current_tenant', ?, false)")) {
            stmt.setString(1, tenantId);
            stmt.execute();
          }
        });
  }
}
