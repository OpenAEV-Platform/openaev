package io.openaev.utils;

import io.openaev.config.cache.TenantMembershipCacheManager;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Group;
import io.openaev.database.model.Role;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.GroupRepository;
import io.openaev.database.repository.RoleRepository;
import io.openaev.database.repository.TenantRepository;
import io.openaev.multitenancy.DependenciesManagerException;
import io.openaev.service.tenants.TenantService;
import io.openaev.utils.mockUser.TestUserHolder;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.hibernate.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reusable helper for tenant isolation integration tests.
 *
 * <p>Tenant creation uses the service layer directly because the REST endpoint requires an
 * Enterprise Edition license that is not available in the test environment. User attachment and all
 * test assertions go through the REST API.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * Tenant tenantXXX = helper.createTenantWithCurrentUser("Tenant XXX");
 * Tenant tenantYYY = helper.createTenantWithCurrentUser("Tenant YYY");
 * // create data in tenant XXX via POST /api/tenants/{tenantXXX}/scenarios
 * // assert data is NOT visible via GET /api/tenants/{tenantYYY}/scenarios/{id}
 * }</pre>
 */
@Component
public class TenantIsolationTestHelper {

  @Autowired private TenantService tenantService;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private TenantMembershipCacheManager tenantMembershipCacheManager;
  @Autowired private TestUserHolder testUserHolder;
  @Autowired private RoleRepository roleRepository;
  @Autowired private GroupRepository groupRepository;
  @Autowired private EntityManager entityManager;

  /**
   * Creates a tenant and attaches the current mock user to it.
   *
   * <p>Tenant creation uses the service layer (EE-gated REST endpoint not available in tests). User
   * attachment uses the REST API.
   *
   * @param name the tenant name
   * @return the persisted {@link Tenant}
   */
  @Transactional
  public Tenant createTenantWithCurrentUser(String name) throws DependenciesManagerException {
    Tenant tenant = createTenant(name);
    String userId = testUserHolder.get().getId();
    tenantRepository.addUserToTenant(userId, tenant.getId());
    tenantMembershipCacheManager.evict(userId, tenant.getId());
    return tenant;
  }

  /**
   * Creates a tenant, attaches the current mock user, and grants them specific capabilities.
   *
   * <p>This sets up a full Role → Group → User chain in the new tenant so that the {@code
   * AccessControlAspect} finds real capabilities without requiring {@code isAdmin = true}.
   *
   * @param name the tenant name
   * @param capabilities the capabilities to grant to the current user in this tenant
   * @return the persisted {@link Tenant}
   */
  @Transactional
  public Tenant createTenantWithCapabilities(String name, Set<Capability> capabilities)
      throws DependenciesManagerException {
    Tenant tenant = createTenantWithCurrentUser(name);
    User user = testUserHolder.get();

    // Create a role with the requested capabilities in the new tenant
    Role role = new Role();
    role.setId(UUID.randomUUID().toString());
    role.setName("test-role-" + UUID.randomUUID().toString().substring(0, 8));
    role.setCapabilities(capabilities);
    role.setTenant(tenant);
    roleRepository.save(role);

    // Create a group in the new tenant, assign the role and the user
    Group group = new Group();
    group.setId(UUID.randomUUID().toString());
    group.setName("test-group-" + UUID.randomUUID().toString().substring(0, 8));
    group.setRoles(List.of(role));
    group.setUsers(List.of(user));
    group.setTenant(tenant);
    groupRepository.save(group);

    // Flush to DB and clear persistence context so that subsequent user loads
    // (e.g., userService.currentUser()) see the new group/role/capabilities
    entityManager.flush();
    entityManager.clear();

    return tenant;
  }

  /**
   * Creates a tenant via the service layer.
   *
   * <p>Note: uses service layer directly because {@code POST /api/tenants} requires Enterprise
   * Edition license.
   *
   * @param name the tenant name
   * @return the persisted {@link Tenant}
   */
  public Tenant createTenant(String name) throws DependenciesManagerException {
    Tenant tenant = new Tenant();
    tenant.setName(name + "-" + UUID.randomUUID().toString().substring(0, 8));
    return tenantService.create(tenant);
  }

  /**
   * Adds the current mock user to the given tenant via the repository.
   *
   * @param tenant the tenant to add the user to
   */
  @Transactional
  public void addCurrentUserToTenant(Tenant tenant) {
    String userId = testUserHolder.get().getId();
    tenantRepository.addUserToTenant(userId, tenant.getId());
    tenantMembershipCacheManager.evict(userId, tenant.getId());
  }

  /**
   * Switches the current tenant context and configures the DB connection for RLS enforcement.
   *
   * <p>Applies {@code SET ROLE openaev_app} so that the connection uses a non-superuser role
   * subject to RLS policies.
   *
   * @param tenantId the tenant ID to switch to
   * @param entityManager the current {@link EntityManager}
   */
  public void switchToTenant(String tenantId, EntityManager entityManager) {
    entityManager.flush();
    entityManager.clear();
    TenantContext.setCurrentTenant(tenantId);
    Session session = entityManager.unwrap(Session.class);
    session.doWork(
        connection -> {
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

  /** Clears the current tenant context. */
  public void clearTenantContext() {
    TenantContext.setCurrentTenant(null);
  }
}
