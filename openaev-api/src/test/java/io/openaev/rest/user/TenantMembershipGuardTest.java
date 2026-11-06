package io.openaev.rest.user;

import static io.openaev.rest.user.PlayerApi.PLAYER_URI;
import static io.openaev.rest.user.TenantUserApi.USER_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.api.users.dto.UserInput;
import io.openaev.config.ProductionActiveTables;
import io.openaev.config.TenantInterceptor;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.rest.exception.TenantAccessDeniedException;
import io.openaev.rest.user.form.player.PlayerInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.PlayerFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.composers.UserComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * What keeps a tenant membership row ({@code users_tenants}) out of reach of an operator of another
 * tenant, and which tenant a permitted membership write lands in. The statement inspector never
 * gates the table: the membership check that computes a request's scope runs before any scope
 * exists. Three things stand in its place. The endpoints that write it resolve the tenant from the
 * request's write scope (pinned here for the player and user endpoints; {@code
 * WriteAttrMembershipTableTest} pins that the write-attribution detector watches every writer of
 * the table, background ones included). In front of those endpoints sits the membership check of
 * the addressed tenant. And on the route with no tenant in the URL the capability resolution runs
 * in the default tenant.
 *
 * <p>On {@code /api/tenants/{tenantId}/users} the caller's membership in the addressed tenant is
 * checked twice, independently, and each check has its own case below: {@code TenantInterceptor} is
 * driven directly, with the real membership data, and {@code TenantScopeResolver} is reached on the
 * route where the interceptor has no path tenant to look at (the {@code X-Tenant-Ids} header), so
 * it is the only one that can refuse. One more case drives the prefixed URL end to end. On {@code
 * /api/users} with no selector the service falls back to the default tenant, and what refuses the
 * call is the capability resolution, which reads the caller's groups in that same default tenant.
 * Cross-tenant read isolation on the prefixed route is covered by {@code TenantUserApiTest}.
 *
 * <p>The class declares the production active tables: the endpoints under test read and write
 * tables the inspector gates (tags, organizations), and the attribution of a membership has to hold
 * in the configuration production runs. The list is the one several other classes declare, so the
 * class shares their Spring context instead of adding one.
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
  @Autowired private UserComposer userComposer;
  @Autowired private TenantInterceptor tenantInterceptor;

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

  private static PlayerInput playerInput() {
    PlayerInput input = PlayerFixture.createPlayerInput();
    input.setEmail("membership-player-" + UUID.randomUUID() + "@filigran.io");
    return input;
  }

  private List<String> membershipTenantsOf(String email) {
    entityManager.flush();
    return entityManager
        .createNativeQuery(
            "SELECT ut.tenant_id FROM users_tenants ut JOIN users u ON u.user_id = ut.user_id"
                + " WHERE lower(u.user_email) = lower(:email)")
        .setParameter("email", email)
        .getResultList();
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
  @DisplayName("Tenant of a permitted membership write")
  class AttributionOfAPermittedWrite {

    private static final String TENANT_HEADER = "X-Tenant-Ids";

    private String tenantB;

    /**
     * The caller belongs to the default tenant and to B, so nothing but the request's write scope
     * tells the membership which of the two it joins.
     */
    @BeforeEach
    void callerOfTwoTenants() throws Exception {
      tenantRepository.addUserToTenant(testUserHolder.get().getId(), Tenant.DEFAULT_TENANT_UUID);
      tenantB = tenantIsolationHelper.createTenantWithCurrentUser("membership-b").getId();
      entityManager.flush();
      entityManager.clear();
      // The fixtures leave B on the test thread; the header route reaches the handler with none.
      TenantContext.clearCurrentTenant();
      assertEquals(
          Tenant.DEFAULT_TENANT_UUID,
          TenantContext.getCurrentTenant(),
          "The ambient tenant must be the default one for the header route to prove anything");
    }

    @AfterEach
    void clearAmbientTenant() {
      TenantContext.clearCurrentTenant();
    }

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("A player created with X-Tenant-Ids joins the tenant the header names")
    void given_headerNamingTenantB_should_attachThePlayerToB() throws Exception {
      // -- PREPARE --
      PlayerInput input = playerInput();

      // -- EXECUTE --
      mvc.perform(
              post(PLAYER_URI)
                  .header(TENANT_HEADER, tenantB)
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());

      // -- ASSERT --
      assertEquals(List.of(tenantB), membershipTenantsOf(input.getEmail()));
    }

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("A player upserted with X-Tenant-Ids joins the tenant the header names")
    void given_headerNamingTenantB_should_attachTheUpsertedPlayerToB() throws Exception {
      // -- PREPARE --
      PlayerInput input = playerInput();

      // -- EXECUTE --
      mvc.perform(
              post(PLAYER_URI + "/upsert")
                  .header(TENANT_HEADER, tenantB)
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());

      // -- ASSERT --
      assertEquals(List.of(tenantB), membershipTenantsOf(input.getEmail()));
    }

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("A user created with X-Tenant-Ids joins the tenant the header names")
    void given_headerNamingTenantB_should_attachTheUserToB() throws Exception {
      // -- PREPARE --
      UserInput input = userInput();

      // -- EXECUTE --
      mvc.perform(
              post(USER_URI)
                  .header(TENANT_HEADER, tenantB)
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isCreated());

      // -- ASSERT --
      assertEquals(List.of(tenantB), membershipTenantsOf(input.email()));
    }

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("A user detached with X-Tenant-Ids leaves the tenant the header names only")
    void given_headerNamingTenantB_should_detachTheUserFromBOnly() throws Exception {
      // -- PREPARE --
      User member = userComposer.forUser(UserFixture.getUserWithDefaultEmail()).persist().get();
      tenantRepository.addUserToTenant(member.getId(), Tenant.DEFAULT_TENANT_UUID);
      tenantRepository.addUserToTenant(member.getId(), tenantB);
      entityManager.flush();
      entityManager.clear();
      TenantContext.clearCurrentTenant();

      // -- EXECUTE --
      mvc.perform(
              delete(USER_URI + "/" + member.getId())
                  .header(TENANT_HEADER, tenantB)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isNoContent());

      // -- ASSERT --
      assertEquals(List.of(Tenant.DEFAULT_TENANT_UUID), membershipTenantsOf(member.getEmail()));
    }

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("A player created under the tenant prefix joins the addressed tenant")
    void given_tenantPrefix_should_attachThePlayerToTheAddressedTenant() throws Exception {
      // -- PREPARE --
      PlayerInput input = playerInput();

      // -- EXECUTE --
      mvc.perform(
              post("/api/tenants/" + tenantB + "/players")
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());

      // -- ASSERT --
      assertEquals(List.of(tenantB), membershipTenantsOf(input.getEmail()));
    }
  }

  @Nested
  @DisplayName("Prefixed route")
  class PrefixedRoute {

    @Test
    @WithMockUser
    @DisplayName(
        "TenantInterceptor alone refuses a caller who is not a member of the addressed tenant")
    void given_tenantWithoutMembership_should_beRefusedByTheInterceptor() throws Exception {
      // -- PREPARE --
      Tenant other = tenantIsolationHelper.createTenant("Tenant interceptor");
      String userId = testUserHolder.get().getId();
      tenantRepository.removeUserFromTenant(userId, other.getId());
      tenantMembershipCacheManager.evict(userId, other.getId());
      entityManager.flush();
      entityManager.clear();
      MockHttpServletRequest request =
          new MockHttpServletRequest("POST", "/api/tenants/" + other.getId() + "/users");

      // -- EXECUTE & ASSERT --
      // Called directly, so neither the argument resolver nor any capability check can be the one
      // that refuses.
      assertThrows(
          TenantAccessDeniedException.class,
          () -> tenantInterceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
    }

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("TenantScopeResolver alone refuses a selector naming a tenant the caller left")
    void given_selectorWithoutMembership_should_beRefusedByTheScopeResolver() throws Exception {
      // -- PREPARE --
      // The header route carries no path tenant, so the interceptor has nothing to check and the
      // scope resolver is the only guard in front of the write. An administrator passes every
      // capability check, so nothing else can refuse either.
      Tenant other = tenantIsolationHelper.createTenant("Tenant resolver");
      String userId = testUserHolder.get().getId();
      tenantRepository.removeUserFromTenant(userId, other.getId());
      tenantMembershipCacheManager.evict(userId, other.getId());
      entityManager.flush();
      entityManager.clear();
      TenantContext.clearCurrentTenant();
      long before = membershipRowsIn(other.getId());

      // -- EXECUTE --
      mvc.perform(
              post(USER_URI)
                  .header("X-Tenant-Ids", other.getId())
                  .content(asJsonString(userInput()))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          // -- ASSERT --
          .andExpect(status().isForbidden())
          .andExpect(content().string(containsString("TENANT_ACCESS_DENIED")));

      entityManager.flush();
      assertEquals(
          before,
          membershipRowsIn(other.getId()),
          "A membership row was written in a tenant the caller does not belong to");
    }

    @Test
    @WithMockUser
    @DisplayName("Addressing a tenant the caller left writes no membership in it, end to end")
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
