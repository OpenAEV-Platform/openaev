package io.openaev.rest.organization;

import static io.openaev.rest.user.TenantUserApi.USER_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.api.users.dto.UserInput;
import io.openaev.database.model.Organization;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.OrganizationFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.composers.OrganizationComposer;
import io.openaev.utils.fixtures.composers.UserComposer;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * A user is platform-level (member of several tenants) but points at ONE organization, owned by one
 * tenant. Mapping a user to its output must never initialize that reference when the organization
 * is out of the caller's reach (the lookup is fail-closed and throws), and must never expose it:
 * the output only carries an organization the caller is entitled to see.
 */
@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = "openaev.tenant.active-tables=organizations")
@DisplayName("Organization tenant isolation in user outputs")
class OrganizationUserOutputIsolationTest extends IntegrationTest {

  private static final String PLATFORM_USERS_URI = "/api/platform-users";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private OrganizationComposer organizationComposer;
  @Autowired private UserComposer userComposer;
  @Resource private ObjectMapper mapper;

  @MockitoBean private EnterpriseEditionService enterpriseEditionService;

  private Tenant tenantA;
  private Tenant tenantB;
  private String prefix;

  @BeforeEach
  void setUp() throws Exception {
    when(enterpriseEditionService.isEnterpriseLicenseInactive(any())).thenReturn(false);
    organizationComposer.reset();
    userComposer.reset();
    tenantA = tenantHelper.createTenant("organization-user-a");
    tenantB = tenantHelper.createTenant("organization-user-b");
    // Onboarding enrolls its creator; each test grants only the memberships it exercises.
    String userId = testUserHolder.get().getId();
    for (Tenant tenant : List.of(tenantA, tenantB)) {
      tenantRepository.removeUserFromTenant(userId, tenant.getId());
      tenantMembershipCacheManager.evict(userId, tenant.getId());
    }
    prefix = "organization-user-" + UUID.randomUUID();
  }

  private Organization seedOrganization(Tenant tenant) {
    Organization organization = OrganizationFixture.createDefaultOrganisation();
    organization.setName(prefix + "-" + tenant.getId());
    organization.setTenant(tenant);
    return organizationComposer.forOrganization(organization).persist().get();
  }

  /** Seeds a player belonging to {@code organization}, member of every given tenant. */
  private User seedPlayer(Organization organization, Tenant... memberships) {
    User player = UserFixture.getUser("Player", "Shared", prefix + "@filigran.io");
    player.setOrganization(organization);
    player = userComposer.forUser(player).persist().get();
    for (Tenant tenant : memberships) {
      tenantRepository.addUserToTenant(player.getId(), tenant.getId());
    }
    // Detach everything so the mapper meets the organization as a lazy reference, as in production.
    entityManager.flush();
    entityManager.clear();
    return player;
  }

  private JsonNode response(MockHttpServletRequestBuilder request) throws Exception {
    String body =
        mvc.perform(request.accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return mapper.readTree(body);
  }

  private static String textOrNull(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value == null || value.isNull() ? null : value.asText();
  }

  private static void assertNoOrganization(JsonNode user, Organization hidden) {
    assertThat(textOrNull(user, "user_organization_id")).isNull();
    assertThat(textOrNull(user, "user_organization_name")).isNull();
    assertThat(user.toString()).doesNotContain(hidden.getId(), hidden.getName());
  }

  private static void assertOrganization(JsonNode user, Organization expected) {
    assertThat(textOrNull(user, "user_organization_id")).isEqualTo(expected.getId());
    assertThat(textOrNull(user, "user_organization_name")).isEqualTo(expected.getName());
  }

  private static String tenantUri(Tenant tenant, String uri) {
    return uri.replace("/api/", "/api/tenants/" + tenant.getId() + "/");
  }

  @Nested
  @WithMockUser(isAdmin = true)
  @DisplayName("Tenant user API")
  class TenantUsers {

    @Test
    @DisplayName("Read under A: the player's organization of B is neither resolved nor exposed")
    void given_foreignOrganization_should_readUserWithoutOrganization() throws Exception {
      // Arrange
      tenantHelper.attachCurrentUserToTenant(tenantA.getId());
      Organization organizationB = seedOrganization(tenantB);
      User player = seedPlayer(organizationB, tenantA, tenantB);

      // Act
      JsonNode user = response(get(tenantUri(tenantA, USER_URI) + "/" + player.getId()));

      // Assert
      assertNoOrganization(user, organizationB);
    }

    @Test
    @DisplayName("Read under A: the player's organization of A is exposed")
    void given_ownOrganization_should_readUserWithOrganization() throws Exception {
      // Arrange
      tenantHelper.attachCurrentUserToTenant(tenantA.getId());
      Organization organizationA = seedOrganization(tenantA);
      User player = seedPlayer(organizationA, tenantA, tenantB);

      // Act
      JsonNode user = response(get(tenantUri(tenantA, USER_URI) + "/" + player.getId()));

      // Assert
      assertOrganization(user, organizationA);
    }

    @Test
    @DisplayName("Read under A with a scope over A and B: only the organization of A is exposed")
    void given_callerInBothTenants_should_exposeOnlyContextTenantOrganization() throws Exception {
      // Arrange: the caller can see B's organization, but the request is made in A's context.
      tenantHelper.attachCurrentUserToTenant(tenantA.getId());
      tenantHelper.attachCurrentUserToTenant(tenantB.getId());
      Organization organizationB = seedOrganization(tenantB);
      User player = seedPlayer(organizationB, tenantA, tenantB);

      // Act
      JsonNode users =
          response(
              post(tenantUri(tenantA, USER_URI) + "/find")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(List.of(player.getId())))
                  .with(csrf()));

      // Assert
      assertThat(users).hasSize(1);
      assertNoOrganization(users.get(0), organizationB);
    }

    @Test
    @DisplayName("Attaching to A an existing user of B does not leak nor break on its organization")
    void given_existingUserOfOtherTenant_should_attachWithoutOrganization() throws Exception {
      // Arrange
      tenantHelper.attachCurrentUserToTenant(tenantA.getId());
      Organization organizationB = seedOrganization(tenantB);
      User player = seedPlayer(organizationB, tenantB);
      UserInput input =
          new UserInput(
              player.getEmail(),
              player.getFirstname(),
              player.getLastname(),
              null,
              null,
              null,
              null,
              null,
              List.of(),
              false,
              List.of());

      // Act
      JsonNode user =
          response(
              post(tenantUri(tenantA, USER_URI))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input))
                  .with(csrf()));

      // Assert
      assertThat(textOrNull(user, "user_id")).isEqualTo(player.getId());
      assertNoOrganization(user, organizationB);
    }
  }

  @Nested
  @WithMockUser(isAdmin = true)
  @DisplayName("Platform user API")
  class PlatformUsers {

    @Test
    @DisplayName("Caller outside B: the player's organization of B is neither resolved nor exposed")
    void given_organizationOutsideCallerScope_should_readUserWithoutOrganization()
        throws Exception {
      // Arrange
      tenantHelper.attachCurrentUserToTenant(tenantA.getId());
      Organization organizationB = seedOrganization(tenantB);
      User player = seedPlayer(organizationB, tenantA, tenantB);

      // Act
      JsonNode user = response(get(PLATFORM_USERS_URI + "/" + player.getId()));

      // Assert
      assertNoOrganization(user, organizationB);
    }

    @Test
    @DisplayName("Caller member of B: the player's organization of B is exposed")
    void given_organizationInCallerScope_should_readUserWithOrganization() throws Exception {
      // Arrange
      tenantHelper.attachCurrentUserToTenant(tenantB.getId());
      Organization organizationB = seedOrganization(tenantB);
      User player = seedPlayer(organizationB, tenantA, tenantB);

      // Act
      JsonNode user = response(get(PLATFORM_USERS_URI + "/" + player.getId()));

      // Assert
      assertOrganization(user, organizationB);
    }
  }
}
