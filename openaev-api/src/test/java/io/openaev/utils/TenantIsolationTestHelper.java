package io.openaev.utils;

import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.utils.fixtures.tenants.TenantComposer;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import io.openaev.utils.mockUser.TestUserHolder;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Reusable helper for tenant isolation integration tests.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * Tenant tenantXXX = helper.createTenant("Tenant XXX");
 * Tenant tenantYYY = helper.createTenant("Tenant YYY");
 * helper.switchToTenant(tenantXXX.getId(), entityManager);
 * // ... create data in tenant XXX ...
 * helper.switchToTenant(tenantYYY.getId(), entityManager);
 * // ... assert data is NOT visible via REST ...
 * }</pre>
 */
@Component
public class TenantIsolationTestHelper {

  @Autowired private TenantComposer tenantComposer;
  @Autowired private TestUserHolder testUserHolder;

  @Value("${openaev.rls.enabled:true}")
  private boolean rlsEnabled;

  /**
   * Creates a tenant with the given name.
   *
   * @param name the tenant name
   * @return the persisted {@link Tenant}
   */
  public Tenant createTenant(String name) {
    return tenantComposer.forTenant(TenantFixture.getTenant(name)).persist().get();
  }

  /**
   * Adds the current mock user (from {@link TestUserHolder}) to the given tenant. This is required
   * because the {@code AccessControlAspect} checks that the authenticated user belongs to the
   * tenant in {@link TenantContext}.
   *
   * @param tenant the tenant to add the user to
   * @param entityManager the current {@link EntityManager}
   */
  public void addCurrentUserToTenant(Tenant tenant, EntityManager entityManager) {
    User user = testUserHolder.get();
    user.getTenants().add(tenant);
    entityManager.merge(user);
    entityManager.flush();
  }

  /**
   * Switches the current tenant context, flushes/clears the persistence context, and sets the
   * PostgreSQL RLS session variable on the underlying DB connection.
   *
   * <p>When {@code openaev.rls.enabled=true} (default), this also applies {@code SET ROLE
   * openaev_app} so that the connection uses a non-superuser role subject to RLS policies.
   *
   * @param tenantId the tenant ID to switch to
   * @param entityManager the current {@link EntityManager}
   */
  public void switchToTenant(String tenantId, EntityManager entityManager) {
    entityManager.flush();
    entityManager.clear();
    TenantContext.setCurrentTenant(tenantId);
    if (rlsEnabled) {
      Session session = entityManager.unwrap(Session.class);
      session.doWork(
              connection -> {
                // Adopt the non-superuser role so PostgreSQL RLS policies are enforced
                try (var stmt = connection.createStatement()) {
                  stmt.execute("SET ROLE openaev_app");
                }
                try (var stmt =
                             connection.prepareStatement("SELECT set_config('app.current_tenant', ?, false)")) {
                  stmt.setString(1, tenantId);
                  stmt.execute();
                }
              });
    }
  }

  /** Clears the current tenant context. */
  public void clearTenantContext() {
    TenantContext.setCurrentTenant(null);
  }
}
