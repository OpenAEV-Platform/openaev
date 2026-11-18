package io.openaev.rest.user;

import static io.openaev.rest.user.PlayerApi.PLAYER_URI;
import static io.openaev.rest.user.TenantUserApi.USER_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.api.users.dto.UserInput;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.rest.user.form.player.PlayerInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.PlayerFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.composers.UserComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * The four handlers that write a membership row ({@code users_tenants}) on the route with no tenant
 * in the URL carry {@code @RequireTenantSelector}: player create, player upsert, user create and
 * user detach. With no {@code X-Tenant-Ids} header the annotation lets {@code
 * TxCtxArgumentResolver} pick the tenant from the caller's memberships, and what the membership row
 * then holds depends on who calls. This pins every caller shape, reading the row's {@code
 * tenant_id} from the database, the same way {@code DocumentNoSelectorWriteScopeTest} does for
 * documents:
 *
 * <ul>
 *   <li>a member of one tenant, not the default one, writes in that tenant;
 *   <li>a member of the default tenant and of another writes in the default tenant;
 *   <li>a member of two tenants without the default one is refused with {@code
 *       TENANT_SELECTOR_REQUIRED}, nothing written;
 *   <li>a caller with no membership is refused with {@code TENANT_WRITE_SCOPE}, nothing written.
 * </ul>
 *
 * <p>Dropping the annotation from one handler turns the first two shapes of that handler into a
 * different outcome (a single-tenant caller keeps working, a two-tenant one gets a 400), so each
 * handler is pinned on its own.
 */
@TestInstance(PER_CLASS)
@Transactional
@DisplayName("Membership writes with no tenant selector")
public class TenantMembershipNoSelectorWriteScopeTest extends IntegrationTest {

  /** Production's active tables: the handlers read and write tables the inspector gates. */
  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  private static final String DEFAULT_TENANT = Tenant.DEFAULT_TENANT_UUID;

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantIsolationHelper;
  @Autowired private UserComposer userComposer;

  /** The three handlers that create a membership, each with its own request body. */
  private enum Creator {
    PLAYER_CREATE(PLAYER_URI),
    PLAYER_UPSERT(PLAYER_URI + "/upsert"),
    USER_CREATE(USER_URI);

    private final String uri;

    Creator(String uri) {
      this.uri = uri;
    }

    MockHttpServletRequestBuilder request(String email) {
      Object body = this == USER_CREATE ? userInput(email) : playerInput(email);
      return post(uri)
          .content(asJsonString(body))
          .contentType(MediaType.APPLICATION_JSON)
          .accept(MediaType.APPLICATION_JSON)
          .with(csrf());
    }
  }

  private static UserInput userInput(String email) {
    return new UserInput(
        email,
        "NoSelector",
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

  private static PlayerInput playerInput(String email) {
    PlayerInput input = PlayerFixture.createPlayerInput();
    input.setEmail(email);
    return input;
  }

  private static String newEmail() {
    return "noselector-" + UUID.randomUUID() + "@filigran.io";
  }

  @AfterEach
  void clearAmbientTenant() {
    TenantContext.clearCurrentTenant();
  }

  /** Production reaches the route with no thread-local tenant; the fixtures leave one behind. */
  private void arriveWithNoAmbientTenant() {
    entityManager.flush();
    entityManager.clear();
    TenantContext.clearCurrentTenant();
    assertEquals(
        DEFAULT_TENANT,
        TenantContext.getCurrentTenant(),
        "The ambient tenant must be the default one for a no-selector request to prove anything");
  }

  private List<String> membershipTenantsOf(String email) {
    entityManager.flush();
    @SuppressWarnings("unchecked")
    List<String> tenants =
        entityManager
            .createNativeQuery(
                "SELECT ut.tenant_id FROM users_tenants ut JOIN users u ON u.user_id = ut.user_id"
                    + " WHERE lower(u.user_email) = lower(:email) ORDER BY ut.tenant_id")
            .setParameter("email", email)
            .getResultList();
    return tenants;
  }

  /** A user the caller may detach, already a member of the given tenants. */
  private User memberOf(String... tenantIds) throws Exception {
    User member = userComposer.forUser(UserFixture.getUserWithDefaultEmail()).persist().get();
    for (String tenantId : tenantIds) {
      tenantIsolationHelper.attachUserToTenant(member.getId(), tenantId);
    }
    return member;
  }

  private static List<String> sorted(String... tenantIds) {
    return List.of(tenantIds).stream().sorted().toList();
  }

  @Nested
  @DisplayName("Caller member of one tenant, not the default one")
  class SingleNonDefaultTenant {

    @ParameterizedTest(name = "{0}")
    @EnumSource(Creator.class)
    @WithMockUser(isAdmin = true)
    @DisplayName("A created membership lands in the caller's own tenant")
    void given_singleTenantCaller_should_attachToItsOwnTenant(Creator creator) throws Exception {
      // -- PREPARE --
      String tenant = tenantIsolationHelper.createTenantWithCurrentUser("noselector-solo").getId();
      arriveWithNoAmbientTenant();
      String email = newEmail();

      // -- EXECUTE --
      mvc.perform(creator.request(email)).andExpect(status().is2xxSuccessful());

      // -- ASSERT --
      assertEquals(List.of(tenant), membershipTenantsOf(email));
    }

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("A detach removes the membership of the caller's own tenant only")
    void given_singleTenantCaller_should_detachFromItsOwnTenant() throws Exception {
      // -- PREPARE --
      String tenant = tenantIsolationHelper.createTenantWithCurrentUser("noselector-solo").getId();
      User member = memberOf(tenant, DEFAULT_TENANT);
      arriveWithNoAmbientTenant();

      // -- EXECUTE --
      mvc.perform(
              delete(USER_URI + "/" + member.getId())
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isNoContent());

      // -- ASSERT --
      assertEquals(List.of(DEFAULT_TENANT), membershipTenantsOf(member.getEmail()));
    }
  }

  @Nested
  @DisplayName("Caller member of the default tenant and of another")
  class DefaultAndAnotherTenant {

    @ParameterizedTest(name = "{0}")
    @EnumSource(Creator.class)
    @WithMockUser(isAdmin = true)
    @DisplayName("A created membership lands in the default tenant")
    void given_callerOfDefaultAndB_should_attachToTheDefaultTenant(Creator creator)
        throws Exception {
      // -- PREPARE --
      tenantIsolationHelper.attachCurrentUserToTenant(DEFAULT_TENANT);
      tenantIsolationHelper.createTenantWithCurrentUser("noselector-default-and-b");
      arriveWithNoAmbientTenant();
      String email = newEmail();

      // -- EXECUTE --
      mvc.perform(creator.request(email)).andExpect(status().is2xxSuccessful());

      // -- ASSERT --
      assertEquals(List.of(DEFAULT_TENANT), membershipTenantsOf(email));
    }

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("A detach removes the membership of the default tenant only")
    void given_callerOfDefaultAndB_should_detachFromTheDefaultTenant() throws Exception {
      // -- PREPARE --
      tenantIsolationHelper.attachCurrentUserToTenant(DEFAULT_TENANT);
      String tenantB =
          tenantIsolationHelper.createTenantWithCurrentUser("noselector-default-and-b").getId();
      User member = memberOf(DEFAULT_TENANT, tenantB);
      arriveWithNoAmbientTenant();

      // -- EXECUTE --
      mvc.perform(
              delete(USER_URI + "/" + member.getId())
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isNoContent());

      // -- ASSERT --
      assertEquals(List.of(tenantB), membershipTenantsOf(member.getEmail()));
    }
  }

  @Nested
  @DisplayName("Caller member of two tenants, neither of them the default one")
  class TwoTenantsWithoutDefault {

    @ParameterizedTest(name = "{0}")
    @EnumSource(Creator.class)
    @WithMockUser(isAdmin = true)
    @DisplayName("A create is refused with TENANT_SELECTOR_REQUIRED and writes nothing")
    void given_callerOfBAndC_should_refuseTheCreate(Creator creator) throws Exception {
      // -- PREPARE --
      tenantIsolationHelper.createTenantWithCurrentUser("noselector-b");
      tenantIsolationHelper.createTenantWithCurrentUser("noselector-c");
      arriveWithNoAmbientTenant();
      String email = newEmail();

      // -- EXECUTE --
      mvc.perform(creator.request(email))
          // -- ASSERT --
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value("TENANT_SELECTOR_REQUIRED"));
      assertEquals(List.of(), membershipTenantsOf(email));
    }

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("A detach is refused with TENANT_SELECTOR_REQUIRED and removes nothing")
    void given_callerOfBAndC_should_refuseTheDetach() throws Exception {
      // -- PREPARE --
      String tenantB = tenantIsolationHelper.createTenantWithCurrentUser("noselector-b").getId();
      String tenantC = tenantIsolationHelper.createTenantWithCurrentUser("noselector-c").getId();
      User member = memberOf(tenantB, tenantC);
      arriveWithNoAmbientTenant();

      // -- EXECUTE --
      mvc.perform(
              delete(USER_URI + "/" + member.getId())
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          // -- ASSERT --
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value("TENANT_SELECTOR_REQUIRED"));
      assertEquals(sorted(tenantB, tenantC), membershipTenantsOf(member.getEmail()));
    }
  }

  @Nested
  @DisplayName("Caller with no tenant membership")
  class NoMembership {

    @ParameterizedTest(name = "{0}")
    @EnumSource(Creator.class)
    @WithMockUser(isAdmin = true)
    @DisplayName("A create is refused with TENANT_WRITE_SCOPE and writes nothing")
    void given_callerWithoutMembership_should_refuseTheCreate(Creator creator) throws Exception {
      // -- PREPARE --
      arriveWithNoAmbientTenant();
      String email = newEmail();

      // -- EXECUTE --
      mvc.perform(creator.request(email))
          // -- ASSERT --
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value("TENANT_WRITE_SCOPE"));
      assertEquals(List.of(), membershipTenantsOf(email));
    }

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("A detach is refused with TENANT_WRITE_SCOPE and removes nothing")
    void given_callerWithoutMembership_should_refuseTheDetach() throws Exception {
      // -- PREPARE --
      User member = memberOf(DEFAULT_TENANT);
      arriveWithNoAmbientTenant();

      // -- EXECUTE --
      mvc.perform(
              delete(USER_URI + "/" + member.getId())
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          // -- ASSERT --
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value("TENANT_WRITE_SCOPE"));
      assertEquals(List.of(DEFAULT_TENANT), membershipTenantsOf(member.getEmail()));
    }
  }
}
