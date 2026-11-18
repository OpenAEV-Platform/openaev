package io.openaev.rest.user;

import static io.openaev.rest.user.PlayerApi.PLAYER_URI;
import static io.openaev.rest.user.TenantUserApi.USER_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.api.users.dto.UserInput;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Organization;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.OrganizationRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.rest.user.form.player.PlayerInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.OrganizationFixture;
import io.openaev.utils.fixtures.PlayerFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.composers.UserComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
 * A user holds one organization per tenant, and a caller of several tenants picks the one a request
 * works on with {@code X-Tenant-Ids}. On that header route the ambient tenant is the default one,
 * so everything these endpoints do with the user's organization (assign it, read it back, show it
 * in the response) has to follow the request's tenant instead. The caller here belongs to the
 * default tenant and to B, and the user holds an organization in each, so reading the wrong tenant
 * is visible as the other organization.
 *
 * <p>The class declares the production active tables: {@code organizations} is gated, and the
 * response of a raw user is serialized after the transaction, where nothing but the request's
 * tenant says which organization to show.
 */
@TestInstance(PER_CLASS)
@Transactional
@DisplayName("Organization of a user on the X-Tenant-Ids route")
public class TenantMembershipOrganizationScopeTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  private static final String TENANT_HEADER = "X-Tenant-Ids";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantIsolationHelper;
  @Autowired private UserComposer userComposer;
  @Autowired private UserRepository userRepository;
  @Autowired private OrganizationRepository organizationRepository;

  private String tenantB;
  private Organization organizationOfDefault;
  private Organization organizationOfB;

  @BeforeEach
  void callerOfTwoTenantsWithAnOrganizationInEach() throws Exception {
    tenantIsolationHelper.attachCurrentUserToTenant(Tenant.DEFAULT_TENANT_UUID);
    tenantB = tenantIsolationHelper.createTenantWithCurrentUser("organization-b").getId();
    organizationOfDefault = organizationIn(Tenant.DEFAULT_TENANT_UUID);
    organizationOfB = organizationIn(tenantB);
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

  private Organization organizationIn(String tenantId) {
    Organization organization = OrganizationFixture.createDefaultOrganisation();
    organization.setTenant(new Tenant(tenantId));
    return organizationRepository.save(organization);
  }

  /** A member of both tenants, holding an organization in each. */
  private User memberWithAnOrganizationInEachTenant() {
    User member = userComposer.forUser(UserFixture.getUserWithDefaultEmail()).persist().get();
    tenantIsolationHelper.attachUserToTenant(member.getId(), Tenant.DEFAULT_TENANT_UUID);
    tenantIsolationHelper.attachUserToTenant(member.getId(), tenantB);
    User managed = userRepository.findById(member.getId()).orElseThrow();
    userRepository.assignOrganization(managed, Tenant.DEFAULT_TENANT_UUID, organizationOfDefault);
    userRepository.assignOrganization(managed, tenantB, organizationOfB);
    entityManager.flush();
    entityManager.clear();
    TenantContext.clearCurrentTenant();
    return managed;
  }

  private List<String> organizationsOf(String userId) {
    entityManager.flush();
    @SuppressWarnings("unchecked")
    List<String> organizations =
        entityManager
            .createNativeQuery(
                "SELECT organization_id FROM users_organizations WHERE user_id = :userId"
                    + " ORDER BY organization_id")
            .setParameter("userId", userId)
            .getResultList();
    return organizations;
  }

  private static List<String> sorted(String... ids) {
    return List.of(ids).stream().sorted().toList();
  }

  private static UserInput userInput(String email, String organizationId) {
    return new UserInput(
        email,
        "Organization",
        "Scope",
        UserFixture.RAW_PASSWORD,
        null,
        null,
        null,
        organizationId,
        List.of(),
        false,
        List.of());
  }

  private static PlayerInput playerInput(String email, String organizationId) {
    PlayerInput input = PlayerFixture.createPlayerInput();
    input.setEmail(email);
    input.setOrganizationId(organizationId);
    return input;
  }

  private static String newEmail() {
    return "organization-" + UUID.randomUUID() + "@filigran.io";
  }

  @Nested
  @DisplayName("Users API")
  class UsersApi {

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("An update assigns the organization in the tenant the header names")
    void given_headerNamingTenantB_should_assignTheOrganizationInB() throws Exception {
      // -- PREPARE --
      User member = memberWithAnOrganizationInEachTenant();
      String newOrganizationOfB = organizationIn(tenantB).getId();

      // -- EXECUTE --
      mvc.perform(
              put(USER_URI + "/" + member.getId())
                  .header(TENANT_HEADER, tenantB)
                  .content(asJsonString(userInput(member.getEmail(), newOrganizationOfB)))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          // -- ASSERT --
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.user_organization_id").value(newOrganizationOfB));
      assertEquals(
          sorted(organizationOfDefault.getId(), newOrganizationOfB),
          organizationsOf(member.getId()),
          "The organization of the default tenant must stay, B's must be replaced");
    }

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("A read shows the organization of the tenant the header names")
    void given_headerNamingTenantB_should_readTheOrganizationOfB() throws Exception {
      // -- PREPARE --
      User member = memberWithAnOrganizationInEachTenant();

      // -- EXECUTE --
      mvc.perform(
              get(USER_URI + "/" + member.getId())
                  .header(TENANT_HEADER, tenantB)
                  .accept(MediaType.APPLICATION_JSON))
          // -- ASSERT --
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.user_organization_id").value(organizationOfB.getId()))
          .andExpect(jsonPath("$.user_organization_name").value(organizationOfB.getName()));
    }

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("A create returns the organization it set in the tenant the header names")
    void given_headerNamingTenantB_should_returnTheOrganizationOfB() throws Exception {
      // -- EXECUTE --
      mvc.perform(
              post(USER_URI)
                  .header(TENANT_HEADER, tenantB)
                  .content(asJsonString(userInput(newEmail(), organizationOfB.getId())))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          // -- ASSERT --
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.user_organization_id").value(organizationOfB.getId()))
          .andExpect(jsonPath("$.user_organization_name").value(organizationOfB.getName()));
    }
  }

  @Nested
  @DisplayName("Players API")
  class PlayersApi {

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("A create returns the organization it set in the tenant the header names")
    void given_headerNamingTenantB_should_returnTheOrganizationOfBOnCreate() throws Exception {
      // -- EXECUTE --
      mvc.perform(
              post(PLAYER_URI)
                  .header(TENANT_HEADER, tenantB)
                  .content(asJsonString(playerInput(newEmail(), organizationOfB.getId())))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          // -- ASSERT --
          .andExpect(status().is2xxSuccessful())
          .andExpect(jsonPath("$.user_organization").value(organizationOfB.getId()));
    }

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("An upsert of a new player returns the organization it set in the header's tenant")
    void given_headerNamingTenantB_should_returnTheOrganizationOfBOnUpsertOfNewPlayer()
        throws Exception {
      // -- EXECUTE --
      mvc.perform(
              post(PLAYER_URI + "/upsert")
                  .header(TENANT_HEADER, tenantB)
                  .content(asJsonString(playerInput(newEmail(), organizationOfB.getId())))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          // -- ASSERT --
          .andExpect(status().is2xxSuccessful())
          .andExpect(jsonPath("$.user_organization").value(organizationOfB.getId()));
    }

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("An upsert of a known player returns the organization of the header's tenant")
    void given_headerNamingTenantB_should_returnTheOrganizationOfBOnUpsertOfKnownPlayer()
        throws Exception {
      // -- PREPARE --
      User member = memberWithAnOrganizationInEachTenant();
      String newOrganizationOfB = organizationIn(tenantB).getId();

      // -- EXECUTE --
      mvc.perform(
              post(PLAYER_URI + "/upsert")
                  .header(TENANT_HEADER, tenantB)
                  .content(asJsonString(playerInput(member.getEmail(), newOrganizationOfB)))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          // -- ASSERT --
          .andExpect(status().is2xxSuccessful())
          .andExpect(jsonPath("$.user_organization").value(newOrganizationOfB));
      assertEquals(
          sorted(organizationOfDefault.getId(), newOrganizationOfB),
          organizationsOf(member.getId()));
    }
  }
}
