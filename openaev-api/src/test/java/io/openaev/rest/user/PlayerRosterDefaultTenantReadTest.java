package io.openaev.rest.user;

import static io.openaev.rest.user.PlayerApi.PLAYER_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Organization;
import io.openaev.database.model.Tag;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.raw.RawPlayer;
import io.openaev.database.repository.OrganizationRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.OrganizationFixture;
import io.openaev.utils.fixtures.TagFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.composers.UserComposer;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The player roster, search and options reads must stay inside the caller's tenant scope. A caller
 * who is a member of one tenant only, calling a route with no tenant in the URL (which falls back
 * to the default tenant), must not read the players of the default tenant it does not belong to,
 * and must still read the players of the tenant it does belong to.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@DisplayName("Player roster, search and options on the default-tenant fallback")
public class PlayerRosterDefaultTenantReadTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantIsolationHelper;
  @Autowired private UserComposer userComposer;
  @Autowired private UserRepository userRepository;
  @Autowired private TagRepository tagRepository;
  @Autowired private OrganizationRepository organizationRepository;

  @AfterEach
  void clearAmbientTenant() {
    TenantContext.clearCurrentTenant();
  }

  /** Makes the mock caller a member of a non-default tenant and returns that tenant id. */
  private String callerTenant() throws Exception {
    return tenantIsolationHelper
        .createTenantWithCurrentUser("roster-caller-" + UUID.randomUUID())
        .getId();
  }

  private User seedPlayerInTenant(String tenantId) {
    String email = "roster-player-" + UUID.randomUUID() + "@filigran.io";
    User seed = UserFixture.getUserWithDefaultEmail();
    seed.setEmail(email);
    User player = userComposer.forUser(seed).persist().get();
    tenantIsolationHelper.attachUserToTenant(player.getId(), tenantId);
    entityManager.flush();
    entityManager.clear();
    return player;
  }

  private SearchPaginationInput searchAll() {
    SearchPaginationInput input = new SearchPaginationInput();
    input.setPage(0);
    input.setSize(50);
    input.setTextSearch("");
    return input;
  }

  @Test
  @WithMockUser
  @DisplayName(
      "A member of another tenant reads nothing of the default tenant (list, search, options)")
  void given_callerOfAnotherTenant_should_notReadDefaultTenant() throws Exception {
    callerTenant();
    User def = seedPlayerInTenant(Tenant.DEFAULT_TENANT_UUID);
    TenantContext.clearCurrentTenant();
    assertEquals(Tenant.DEFAULT_TENANT_UUID, TenantContext.getCurrentTenant());

    // list
    mvc.perform(get(PLAYER_URI).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().string(Matchers.not(Matchers.containsString(def.getEmail()))));
    // search
    mvc.perform(
            post(PLAYER_URI + "/search")
                .content(asJsonString(searchAll()))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(content().string(Matchers.not(Matchers.containsString(def.getEmail()))));
    // options by id
    mvc.perform(
            post(PLAYER_URI + "/options")
                .content(asJsonString(List.of(def.getId())))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", Matchers.hasSize(0)));
    // options by name (the default user's unique email fragment matches only it)
    String fragment = def.getEmail().substring(0, def.getEmail().indexOf('@'));
    mvc.perform(get(PLAYER_URI + "/options").param("searchText", fragment))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", Matchers.hasSize(0)));
  }

  @Test
  @WithMockUser
  @DisplayName("A member of a tenant does read that tenant's players (list, search, options)")
  void given_callerOfATenant_should_readThatTenantsPlayers() throws Exception {
    String tenant = callerTenant();
    User mine = seedPlayerInTenant(tenant);
    TenantContext.clearCurrentTenant();

    // list
    mvc.perform(get(PLAYER_URI).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().string(Matchers.containsString(mine.getEmail())));
    // search
    mvc.perform(
            post(PLAYER_URI + "/search")
                .content(asJsonString(searchAll()))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(content().string(Matchers.containsString(mine.getEmail())));
    // options by id
    mvc.perform(
            post(PLAYER_URI + "/options")
                .content(asJsonString(List.of(mine.getId())))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].id", Matchers.hasItem(mine.getId())));
    // options by name
    String fragment = mine.getEmail().substring(0, mine.getEmail().indexOf('@'));
    mvc.perform(get(PLAYER_URI + "/options").param("searchText", fragment))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].id", Matchers.hasItem(mine.getId())));
  }

  @Test
  @WithMockUser
  @DisplayName("A player of several in-scope tenants is returned once, with each tag once")
  void given_playerInSeveralScopeTenants_should_notDuplicateTags() throws Exception {
    String tenantB =
        tenantIsolationHelper.createTenantWithCurrentUser("roster-b-" + UUID.randomUUID()).getId();
    String tenantC =
        tenantIsolationHelper.createTenantWithCurrentUser("roster-c-" + UUID.randomUUID()).getId();
    Tag tag = tagRepository.save(TagFixture.getTagWithText("roster-tag-" + UUID.randomUUID()));
    String email = "roster-multi-" + UUID.randomUUID() + "@filigran.io";
    User seed = UserFixture.getUserWithDefaultEmail();
    seed.setEmail(email);
    seed.getTags().add(tag);
    User player = userComposer.forUser(seed).persist().get();
    tenantIsolationHelper.attachUserToTenant(player.getId(), tenantB);
    tenantIsolationHelper.attachUserToTenant(player.getId(), tenantC);
    entityManager.flush();
    entityManager.clear();

    List<RawPlayer> rows = userRepository.rawPlayersInScope(Set.of(tenantB, tenantC));
    List<RawPlayer> mine =
        rows.stream().filter(r -> email.equalsIgnoreCase(r.getUser_email())).toList();
    assertEquals(1, mine.size(), "the player must appear once across the two in-scope tenants");
    assertEquals(
        List.of(tag.getId()),
        mine.get(0).getUser_tags(),
        "the tag must not be repeated once per tenant membership");
  }

  @Test
  @WithMockUser
  @DisplayName("Search projects the organization of the caller's tenant, not the ambient tenant")
  void given_callerOfATenant_search_should_projectThatTenantsOrganization() throws Exception {
    String tenant = callerTenant();
    Organization organization = OrganizationFixture.createOrganization();
    organization.setTenant(new Tenant(tenant));
    Organization saved = organizationRepository.save(organization);
    String email = "roster-org-" + UUID.randomUUID() + "@filigran.io";
    User seed = UserFixture.getUserWithDefaultEmail();
    seed.setEmail(email);
    User player = userComposer.forUser(seed).withOrganization(saved).persist().get();
    tenantIsolationHelper.attachUserToTenant(player.getId(), tenant);
    entityManager.flush();
    entityManager.clear();
    TenantContext.clearCurrentTenant();

    // The player's organization in the caller's tenant must be projected; it would be null if the
    // join stayed on the ambient (default) tenant.
    mvc.perform(
            post(PLAYER_URI + "/search")
                .content(asJsonString(searchAll()))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(content().string(Matchers.containsString(email)))
        .andExpect(content().string(Matchers.containsString(saved.getId())));
  }

  @Test
  @WithMockUser
  @DisplayName("A membership-less caller reads nothing on any endpoint (fail closed)")
  void given_membershipLessCaller_should_readNothingOnAnyEndpoint() throws Exception {
    User def = seedPlayerInTenant(Tenant.DEFAULT_TENANT_UUID);
    TenantContext.clearCurrentTenant();

    mvc.perform(get(PLAYER_URI).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", Matchers.hasSize(0)));
    mvc.perform(
            post(PLAYER_URI + "/search")
                .content(asJsonString(searchAll()))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", Matchers.hasSize(0)));
    mvc.perform(get(PLAYER_URI + "/options").param("searchText", ""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", Matchers.hasSize(0)));
    mvc.perform(
            post(PLAYER_URI + "/options")
                .content(asJsonString(List.of(def.getId())))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", Matchers.hasSize(0)));
  }
}
