package io.openaev.utils;

import io.openaev.config.cache.TenantMembershipCacheManager;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.TenantRepository;
import io.openaev.multitenancy.DependenciesManagerException;
import io.openaev.service.tenants.TenantService;
import io.openaev.utils.fixtures.TenantGroupFixture;
import io.openaev.utils.fixtures.TenantRoleFixture;
import io.openaev.utils.fixtures.composers.TenantGroupComposer;
import io.openaev.utils.fixtures.composers.TenantRoleComposer;
import io.openaev.utils.fixtures.tenants.TenantComposer;
import io.openaev.utils.fixtures.tenants.TenantFixture;
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
  @Autowired private TenantRoleComposer tenantRoleComposer;
  @Autowired private TenantGroupComposer tenantGroupComposer;
  @Autowired private EntityManager entityManager;
  @Autowired private TenantComposer tenantComposer;

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
    grantCapabilitiesInTenant(tenant.getId(), capabilities);
    return tenant;
  }

  /**
   * Grants the current mock user real membership and specific capabilities in an EXISTING tenant
   * (e.g. the platform default tenant), without creating a new one.
   *
   * <p>Prefer this over {@link #createTenantWithCapabilities} when the test must write under the
   * ambient default tenant (e.g. {@code TenantContext.getCurrentTenant()}): creating a brand new
   * tenant also runs the full onboarding chain ({@code ManagerFactory}, built-in injector/connector
   * registration, ...), which can seed rows that collide with fixtures the test already created for
   * the default tenant (e.g. a well-known injector ID shared across tenants). This method sets up
   * the same Role → Group → User chain as {@link #createTenantWithCapabilities} but scoped to the
   * tenant passed in, so the {@code AccessControlAspect} finds real capabilities without requiring
   * {@code isAdmin = true}.
   *
   * @param tenantId the existing tenant to grant membership and capabilities in
   * @param capabilities the capabilities to grant to the current user in this tenant
   */
  @Transactional
  public void grantCapabilitiesInTenant(String tenantId, Set<Capability> capabilities) {
    User user = testUserHolder.get();
    String userId = user.getId();
    tenantRepository.addUserToTenant(userId, tenantId);
    tenantMembershipCacheManager.evict(userId, tenantId);

    // The role and group composers resolve the tenant via TenantContext.getCurrentTenant().
    String previousTenantId = TenantContext.getCurrentTenant();
    TenantContext.setCurrentTenant(tenantId);
    try {
      // Create a role with the requested capabilities using fixture + composer
      TenantRoleComposer.Composer roleComposer =
          tenantRoleComposer.forRole(TenantRoleFixture.getRole(capabilities));

      // Create a group in the tenant, assign the role and the user using fixture + composer
      var group = TenantGroupFixture.getGroup();
      group.setUsers(List.of(user));
      tenantGroupComposer.forGroup(group).withRole(roleComposer).persist();

      // Flush to DB and clear persistence context so that subsequent user loads
      // (e.g., userService.currentUser()) see the new group/role/capabilities
      entityManager.flush();
      entityManager.clear();
    } finally {
      TenantContext.setCurrentTenant(previousTenantId);
    }
  }

  /**
   * Creates a tenant via the service layer.
   *
   * <p>Note: uses service layer directly because {@code POST /api/tenants} requires Enterprise
   * Edition license.
   *
   * <p>Onboarding self-scopes the ambient transaction to this tenant ({@code
   * ManagerFactory#createDependencyForTenant} -> {@code ManagerCreator#createManager}, see its
   * javadoc): {@code setScopeOnCurrentTransaction} is an unconditional overwrite (unlike the
   * transaction aspect's guarded scope check), so calling this method more than once in the same
   * {@code @Transactional} test (the dominant two-tenant {@code @BeforeEach} idiom) leaves the
   * scope pinned to whichever tenant was created LAST. Reset it to empty here so the actual test
   * method's own request sets it fresh to whichever tenant path it targets, instead of tripping the
   * nesting guard against this leftover onboarding scope.
   *
   * @param name the tenant name
   * @return the persisted {@link Tenant}
   */
  public Tenant createTenant(String name) throws DependenciesManagerException {
    Tenant tenant =
        TenantFixture.getTenant(name + "-" + UUID.randomUUID().toString().substring(0, 8));
    Tenant created = tenantService.create(tenant);
    resetLeftoverOnboardingScope();
    return created;
  }

  /**
   * Resets the {@code app.current_tenants} transaction-local setting left behind by tenant
   * onboarding (see {@link #createTenant}'s javadoc). A no-op outside an active transaction or when
   * nothing was ever set.
   */
  private void resetLeftoverOnboardingScope() {
    entityManager
        .createNativeQuery("SELECT set_config('app.current_tenants', '', true)")
        .getSingleResult();
  }

  /**
   * Switches the current tenant context and enables the Hibernate tenant filter.
   *
   * @param tenantId the tenant ID to switch to
   * @param entityManager the current {@link EntityManager}
   */
  public void switchToTenant(String tenantId, EntityManager entityManager) {
    entityManager.flush();
    entityManager.clear();
    TenantContext.setCurrentTenant(tenantId);
    Session session = entityManager.unwrap(Session.class);
    session.enableFilter("tenantFilter").setParameter("tenantId", tenantId);
  }

  /**
   * Removes tenants that were COMMITTED by a non-transactional test class (tests around the
   * background transaction primitive cannot run inside a test transaction, so nothing rolls back).
   * Deletes the one tenant child without ON DELETE CASCADE ({@code collector_types}) first; every
   * other tenant-scoped row cascades with the tenant. Null ids are skipped so a partially failed
   * setup still cleans what it managed to create. Table-specific rows the caller created (and any
   * join table without a cascading FK) must be removed by the caller BEFORE this call. External
   * residue (per-tenant broker queues) cannot be removed here; that is the suite-wide pre-existing
   * pattern for service-created tenants.
   */
  @Transactional
  public void deleteCommittedTenants(String... tenantIds) {
    for (String tenantId : tenantIds) {
      if (tenantId == null) {
        continue;
      }
      entityManager
          .createNativeQuery("DELETE FROM collector_types WHERE tenant_id = :id")
          .setParameter("id", tenantId)
          .executeUpdate();
      entityManager
          .createNativeQuery("DELETE FROM tenants WHERE tenant_id = :id")
          .setParameter("id", tenantId)
          .executeUpdate();
    }
  }
}
