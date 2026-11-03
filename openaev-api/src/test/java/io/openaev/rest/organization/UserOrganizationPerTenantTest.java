package io.openaev.rest.organization;

import static io.openaev.rest.user.PlayerApi.PLAYER_URI;
import static io.openaev.rest.user.TenantUserApi.USER_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.annotation.Resource;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * A user is platform-level while an organization belongs to one tenant: linking the user to an
 * organization in a tenant must never unlink it in another one (issue #8187). The API keeps a
 * single {@code user_organization} value, the one of the tenant the request is made in.
 */
@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WithMockUser(isAdmin = true)
@DisplayName("User organization per tenant")
class UserOrganizationPerTenantTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private OrganizationComposer organizationComposer;
  @Autowired private UserComposer userComposer;
  @Autowired private EntityManager entityManager;
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
    tenantA = tenantHelper.createTenant("user-organization-a");
    tenantB = tenantHelper.createTenant("user-organization-b");
    tenantHelper.attachCurrentUserToTenant(tenantA.getId());
    tenantHelper.attachCurrentUserToTenant(tenantB.getId());
    prefix = "user-organization-" + UUID.randomUUID();
  }

  private Organization seedOrganization(Tenant tenant) {
    Organization organization = OrganizationFixture.createDefaultOrganisation();
    organization.setName(prefix + "-" + tenant.getId());
    organization.setTenant(tenant);
    return organizationComposer.forOrganization(organization).persist().get();
  }

  /** Seeds a player, member of tenants A and B, with no organization yet. */
  private User seedPlayer() {
    User player = UserFixture.getUser("Player", "Shared", prefix + "@filigran.io");
    player = userComposer.forUser(player).persist().get();
    tenantRepository.addUserToTenant(player.getId(), tenantA.getId());
    tenantRepository.addUserToTenant(player.getId(), tenantB.getId());
    entityManager.flush();
    entityManager.clear();
    return player;
  }

  private static String tenantUri(Tenant tenant, String uri) {
    return uri.replace("/api/", "/api/tenants/" + tenant.getId() + "/");
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

  private JsonNode link(Tenant tenant, User player, String organizationId) throws Exception {
    UserInput input =
        new UserInput(
            player.getEmail(),
            player.getFirstname(),
            player.getLastname(),
            null,
            null,
            null,
            null,
            organizationId,
            List.of(),
            false,
            List.of());
    return response(
        put(tenantUri(tenant, USER_URI) + "/" + player.getId())
            .contentType(MediaType.APPLICATION_JSON)
            .content(asJsonString(input))
            .with(csrf()));
  }

  private String organizationIn(Tenant tenant, User player) throws Exception {
    JsonNode user = response(get(tenantUri(tenant, USER_URI) + "/" + player.getId()));
    JsonNode value = user.get("user_organization_id");
    return value == null || value.isNull() ? null : value.asText();
  }

  private static List<JsonNode> entriesOf(JsonNode array, String userId) {
    List<JsonNode> entries = new java.util.ArrayList<>();
    array.forEach(
        node -> {
          if (userId.equals(node.get("user_id").asText())) {
            entries.add(node);
          }
        });
    return entries;
  }

  private static String organizationOf(JsonNode entry) {
    JsonNode value = entry.get("user_organization");
    return value == null || value.isNull() ? null : value.asText();
  }

  @Test
  @DisplayName("The users of tenant A expose the organization of A for a user also linked in B")
  void given_organizationsInAAndB_when_listUsersInA_should_exposeTheOneOfA() throws Exception {
    // Arrange
    Organization organizationA = seedOrganization(tenantA);
    Organization organizationB = seedOrganization(tenantB);
    User player = seedPlayer();
    link(tenantA, player, organizationA.getId());
    link(tenantB, player, organizationB.getId());

    // Act
    JsonNode users = response(get(tenantUri(tenantA, USER_URI)));

    // Assert
    List<JsonNode> entries = entriesOf(users, player.getId());
    assertThat(entries).hasSize(1);
    assertThat(organizationOf(entries.get(0))).isEqualTo(organizationA.getId());
  }

  @Test
  @DisplayName("The players of tenant A list a user once, with the organization of A")
  void given_organizationsInAAndB_when_listPlayersInA_should_listTheUserOnce() throws Exception {
    // Arrange
    Organization organizationA = seedOrganization(tenantA);
    Organization organizationB = seedOrganization(tenantB);
    User player = seedPlayer();
    link(tenantA, player, organizationA.getId());
    link(tenantB, player, organizationB.getId());

    // Act
    JsonNode players = response(get(tenantUri(tenantA, PLAYER_URI)));

    // Assert
    List<JsonNode> entries = entriesOf(players, player.getId());
    assertThat(entries).hasSize(1);
    assertThat(organizationOf(entries.get(0))).isEqualTo(organizationA.getId());
  }

  @Test
  @DisplayName("The players of tenant A list a user linked only in B once, without organization")
  void given_organizationInBOnly_when_listPlayersInA_should_listTheUserOnceWithoutOrganization()
      throws Exception {
    // Arrange
    Organization organizationB = seedOrganization(tenantB);
    User player = seedPlayer();
    link(tenantB, player, organizationB.getId());

    // Act
    JsonNode players = response(get(tenantUri(tenantA, PLAYER_URI)));

    // Assert
    List<JsonNode> entries = entriesOf(players, player.getId());
    assertThat(entries).hasSize(1);
    assertThat(organizationOf(entries.get(0))).isNull();
  }

  @Test
  @DisplayName("The player search of tenant A returns a user once, with the organization of A")
  void given_organizationsInAAndB_when_searchPlayersInA_should_returnTheUserOnce()
      throws Exception {
    // Arrange
    Organization organizationA = seedOrganization(tenantA);
    Organization organizationB = seedOrganization(tenantB);
    User player = seedPlayer();
    link(tenantA, player, organizationA.getId());
    link(tenantB, player, organizationB.getId());
    SearchPaginationInput search = new SearchPaginationInput();
    search.setPage(0);
    search.setSize(100);
    search.setTextSearch(player.getEmail());

    // Act
    JsonNode page =
        response(
            post(tenantUri(tenantA, PLAYER_URI) + "/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(search))
                .with(csrf()));

    // Assert
    List<JsonNode> entries = entriesOf(page.get("content"), player.getId());
    assertThat(entries).hasSize(1);
    assertThat(organizationOf(entries.get(0))).isEqualTo(organizationA.getId());
  }

  @Test
  @DisplayName("Linking the user in B keeps its organization in A")
  void given_organizationLinkedInA_when_linkedInB_should_keepBothOrganizations() throws Exception {
    // Arrange
    Organization organizationA = seedOrganization(tenantA);
    Organization organizationB = seedOrganization(tenantB);
    User player = seedPlayer();
    link(tenantA, player, organizationA.getId());

    // Act
    link(tenantB, player, organizationB.getId());

    // Assert
    assertThat(organizationIn(tenantA, player)).isEqualTo(organizationA.getId());
    assertThat(organizationIn(tenantB, player)).isEqualTo(organizationB.getId());
  }

  @Test
  @DisplayName("Clearing the organization in B keeps the one in A")
  void given_organizationsInAAndB_when_clearedInB_should_keepOrganizationInA() throws Exception {
    // Arrange
    Organization organizationA = seedOrganization(tenantA);
    Organization organizationB = seedOrganization(tenantB);
    User player = seedPlayer();
    link(tenantA, player, organizationA.getId());
    link(tenantB, player, organizationB.getId());

    // Act
    link(tenantB, player, null);

    // Assert
    assertThat(organizationIn(tenantA, player)).isEqualTo(organizationA.getId());
    assertThat(organizationIn(tenantB, player)).isNull();
  }

  /** Ids of the organizations the user is linked to, whatever their tenant, read from the table. */
  private List<String> storedOrganizationIds(User player) {
    entityManager.flush();
    @SuppressWarnings("unchecked")
    List<String> ids =
        entityManager
            .createNativeQuery(
                "SELECT organization_id FROM users_organizations WHERE user_id = :id")
            .setParameter("id", player.getId())
            .getResultList();
    return ids;
  }

  private void restrictCallerToTenantB() {
    String callerId = testUserHolder.get().getId();
    tenantRepository.removeUserFromTenant(callerId, tenantA.getId());
    tenantMembershipCacheManager.evict(callerId, tenantA.getId());
  }

  @Test
  @DisplayName("A caller who only sees tenant B clearing the organization keeps the one of A")
  void given_callerOnlyInB_when_organizationClearedInB_should_keepOrganizationOfA()
      throws Exception {
    // Arrange
    Organization organizationA = seedOrganization(tenantA);
    Organization organizationB = seedOrganization(tenantB);
    User player = seedPlayer();
    link(tenantA, player, organizationA.getId());
    link(tenantB, player, organizationB.getId());
    restrictCallerToTenantB();

    // Act
    link(tenantB, player, null);

    // Assert
    assertThat(storedOrganizationIds(player)).containsExactly(organizationA.getId());
  }

  @Test
  @DisplayName("A caller who only sees tenant B replacing the organization keeps the one of A")
  void given_callerOnlyInB_when_organizationReplacedInB_should_keepOrganizationOfA()
      throws Exception {
    // Arrange
    Organization organizationA = seedOrganization(tenantA);
    Organization firstB = seedOrganization(tenantB);
    Organization secondB = seedOrganization(tenantB);
    User player = seedPlayer();
    link(tenantA, player, organizationA.getId());
    link(tenantB, player, firstB.getId());
    restrictCallerToTenantB();

    // Act
    link(tenantB, player, secondB.getId());

    // Assert
    assertThat(storedOrganizationIds(player))
        .containsExactlyInAnyOrder(organizationA.getId(), secondB.getId());
  }

  @Test
  @DisplayName("Linking the user to another organization of the same tenant replaces the previous")
  void given_organizationInA_when_linkedToAnotherInA_should_holdOnlyTheNewOne() throws Exception {
    // Arrange
    Organization first = seedOrganization(tenantA);
    Organization second = seedOrganization(tenantA);
    User player = seedPlayer();
    link(tenantA, player, first.getId());

    // Act
    link(tenantA, player, second.getId());

    // Assert
    assertThat(organizationIn(tenantA, player)).isEqualTo(second.getId());
  }
}
