package io.openaev.rest.user;

import static io.openaev.rest.user.TenantUserApi.USER_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.api.users.dto.UserInput;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Tenant;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * What keeps a tenant membership row ({@code users_tenants}) out of reach of an operator of another
 * tenant. The table is not filtered by the statement inspector: every statement on it carries its
 * own {@code user_id} or {@code tenant_id} predicate, and the endpoints that write it resolve the
 * tenant from the request. So the boundary is the pair of checks in front of those endpoints, and
 * these tests pin both halves of it.
 *
 * <p>Both routes of {@link TenantUserApi} are covered, because they do not get their tenant from
 * the same place. On {@code /api/tenants/{tenantId}/users} the caller's membership in the addressed
 * tenant is checked twice, independently: by {@code TenantInterceptor} and by {@code
 * TenantScopeResolver} when it resolves the handler's {@code TxCtx}. Either one refuses the call on
 * its own, so the test goes red only when both are removed. On {@code /api/users} there is no
 * tenant in the request at all: the service falls back to the default tenant, and what refuses the
 * call is the capability resolution, which reads the caller's groups in that same default tenant.
 * Cross-tenant read isolation on the prefixed route is covered by {@code TenantUserApiTest}.
 *
 * <p>The tests declare the production active tables: the table is not gated by the inspector, and
 * the boundary has to hold in the configuration production runs.
 */
@TestInstance(PER_CLASS)
@Transactional
@DisplayName("Tenant membership guard")
public class TenantMembershipGuardTest extends IntegrationTest {

  /**
   * The test classpath declares no active tables, so without this the inspector rewrites nothing
   * and the boundary is pinned in a configuration production does not run.
   */
  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantIsolationHelper;

  private static UserInput userInput() {
    return new UserInput(
        "membership-" + UUID.randomUUID() + "@filigran.io",
        "Membership",
        "Guard",
        UserFixture.RAW_PASSWORD,
        null,
        null,
        null,
        null,
        List.of(),
        false,
        List.of());
  }

  private long membershipRowsIn(String tenantId) {
    return ((Number)
            entityManager
                .createNativeQuery("SELECT count(*) FROM users_tenants WHERE tenant_id = :tenantId")
                .setParameter("tenantId", tenantId)
                .getSingleResult())
        .longValue();
  }

  @Nested
  @DisplayName("Non-prefixed route")
  class NonPrefixedRoute {

    @Test
    @WithMockUser
    @DisplayName("A tenant operator with no grant in the default tenant writes no membership there")
    void given_noGrantInTheFallbackTenant_should_refuseTheMembershipWrite() throws Exception {
      // -- PREPARE --
      // The caller manages users in its own tenant, and holds nothing in the default tenant: the
      // mock user's own group carries an empty role there.
      tenantIsolationHelper.createTenantWithCapabilities(
          "Tenant operator",
          Set.of(
              Capability.ACCESS_TENANT_USERS_GROUPS_AND_ROLES,
              Capability.MANAGE_TENANT_USERS_GROUPS_AND_ROLES));
      // The fixtures leave a tenant on the test thread; production reaches this route with none.
      TenantContext.clearCurrentTenant();
      assertEquals(
          Tenant.DEFAULT_TENANT_UUID,
          TenantContext.getCurrentTenant(),
          "The non-prefixed route must fall back to the default tenant for this test to mean"
              + " anything");
      assertFalse(
          tenantRepository.existsByUserIdAndTenantId(
              testUserHolder.get().getId(), Tenant.DEFAULT_TENANT_UUID),
          "The caller must not be a member of the fallback tenant");
      long before = membershipRowsIn(Tenant.DEFAULT_TENANT_UUID);

      // -- EXECUTE --
      mvc.perform(
              post(USER_URI)
                  .content(asJsonString(userInput()))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          // -- ASSERT --
          .andExpect(status().isForbidden());

      entityManager.flush();
      assertEquals(
          before,
          membershipRowsIn(Tenant.DEFAULT_TENANT_UUID),
          "A membership row was written in the fallback tenant");
    }

    @Test
    @WithMockUser
    @DisplayName("The same caller reads no user from the default tenant either")
    void given_noGrantInTheFallbackTenant_should_refuseTheRead() throws Exception {
      // -- PREPARE --
      tenantIsolationHelper.createTenantWithCapabilities(
          "Tenant reader", Set.of(Capability.ACCESS_TENANT_USERS_GROUPS_AND_ROLES));
      TenantContext.clearCurrentTenant();

      // -- EXECUTE --
      mvc.perform(get(USER_URI).accept(MediaType.APPLICATION_JSON))
          // -- ASSERT --
          .andExpect(status().isForbidden());
    }
  }

  @Nested
  @DisplayName("Prefixed route")
  class PrefixedRoute {

    @Test
    @WithMockUser
    @DisplayName("Addressing a tenant the caller left writes no membership in it")
    void given_tenantWithoutMembership_should_refuseTheMembershipWrite() throws Exception {
      // -- PREPARE --
      // The caller keeps a group granting user management INSIDE the addressed tenant, and loses
      // only its membership row. So the capability check passes there and the membership check is
      // the one thing left that can refuse the call.
      Tenant other = tenantIsolationHelper.createTenant("Tenant other");
      tenantIsolationHelper.grantCapabilitiesInTenant(
          other.getId(),
          Set.of(
              Capability.ACCESS_TENANT_USERS_GROUPS_AND_ROLES,
              Capability.MANAGE_TENANT_USERS_GROUPS_AND_ROLES));
      String userId = testUserHolder.get().getId();
      tenantRepository.removeUserFromTenant(userId, other.getId());
      tenantMembershipCacheManager.evict(userId, other.getId());
      entityManager.flush();
      entityManager.clear();
      assertFalse(
          tenantRepository.existsByUserIdAndTenantId(userId, other.getId()),
          "The caller must not be a member of the addressed tenant");
      long before = membershipRowsIn(other.getId());

      // -- EXECUTE --
      mvc.perform(
              post("/api/tenants/" + other.getId() + "/users")
                  .content(asJsonString(userInput()))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          // -- ASSERT --
          .andExpect(status().isForbidden());

      entityManager.flush();
      assertEquals(
          before,
          membershipRowsIn(other.getId()),
          "A membership row was written in a tenant the caller does not belong to");
    }
  }
}
