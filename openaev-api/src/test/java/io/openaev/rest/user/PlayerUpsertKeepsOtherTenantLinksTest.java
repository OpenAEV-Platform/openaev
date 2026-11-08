package io.openaev.rest.user;

import static io.openaev.rest.user.PlayerApi.PLAYER_URI;
import static io.openaev.rest.user.TenantUserApi.USER_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.api.users.dto.UserInput;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Tag;
import io.openaev.database.model.Team;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.TagRepository;
import io.openaev.database.repository.TeamRepository;
import io.openaev.rest.user.form.player.PlayerInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.PlayerFixture;
import io.openaev.utils.fixtures.TagFixture;
import io.openaev.utils.fixtures.TeamFixture;
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
 * A player upsert adds the tags and teams of the request to the ones the player already has. The
 * player is platform-level, so it may hold tags and teams of tenants the request does not work on,
 * and tags and teams are gated: the request only sees the ones of its own scope. The upsert must
 * therefore add its links without rewriting the whole association, which would delete the links the
 * request cannot see.
 *
 * <p>The caller belongs to the default tenant and to B, and the player holds a tag and a team in
 * each. With no selector the request is narrowed to the default tenant
 * ({@code @RequireTenantSelector}), and with {@code X-Tenant-Ids: B} to B: either way the links of
 * the other tenant have to survive.
 *
 * <p>The same holds for the updates of a user and of a player that replace the tags from the
 * request: the tags of the tenant the request works on are replaced, the ones of the other tenant
 * stay.
 */
@TestInstance(PER_CLASS)
@Transactional
@DisplayName("Player and user writes keep the links of the tenants they do not work on")
public class PlayerUpsertKeepsOtherTenantLinksTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  private static final String TENANT_HEADER = "X-Tenant-Ids";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantIsolationHelper;
  @Autowired private UserComposer userComposer;
  @Autowired private TagRepository tagRepository;
  @Autowired private TeamRepository teamRepository;

  private String tenantB;
  private User player;
  private Tag tagOfDefault;
  private Tag tagOfB;
  private Team teamOfDefault;
  private Team teamOfB;

  @BeforeEach
  void playerHoldingALinkInEachTenant() throws Exception {
    tenantIsolationHelper.attachCurrentUserToTenant(Tenant.DEFAULT_TENANT_UUID);
    tenantB = tenantIsolationHelper.createTenantWithCurrentUser("links-b").getId();
    tagOfDefault = tagIn(Tenant.DEFAULT_TENANT_UUID);
    tagOfB = tagIn(tenantB);
    teamOfDefault = teamIn(Tenant.DEFAULT_TENANT_UUID);
    teamOfB = teamIn(tenantB);
    player = userComposer.forUser(UserFixture.getUserWithDefaultEmail()).persist().get();
    entityManager.flush();
    tenantIsolationHelper.attachUserToTenant(player.getId(), Tenant.DEFAULT_TENANT_UUID);
    tenantIsolationHelper.attachUserToTenant(player.getId(), tenantB);
    for (Tag tag : List.of(tagOfDefault, tagOfB)) {
      link("users_tags", "tag_id", tag.getId());
    }
    for (Team team : List.of(teamOfDefault, teamOfB)) {
      link("users_teams", "team_id", team.getId());
    }
    entityManager.flush();
    entityManager.clear();
    // The fixtures leave a tenant on the test thread; the request reaches the handler with none.
    TenantContext.clearCurrentTenant();
    assertEquals(
        Tenant.DEFAULT_TENANT_UUID,
        TenantContext.getCurrentTenant(),
        "The ambient tenant must be the default one for the request to prove anything");
  }

  @AfterEach
  void clearAmbientTenant() {
    TenantContext.clearCurrentTenant();
  }

  private Tag tagIn(String tenantId) {
    Tag tag = TagFixture.getTagWithText("links-" + UUID.randomUUID());
    tag.setTenant(new Tenant(tenantId));
    return tagRepository.save(tag);
  }

  private Team teamIn(String tenantId) {
    TenantContext.setCurrentTenant(tenantId);
    try {
      Team team = TeamFixture.createTeamWithName("links-" + UUID.randomUUID());
      team.setTenant(new Tenant(tenantId));
      return teamRepository.save(team);
    } finally {
      TenantContext.clearCurrentTenant();
    }
  }

  private void link(String table, String column, String id) {
    entityManager
        .createNativeQuery(
            "INSERT INTO " + table + " (user_id, " + column + ") VALUES (:userId, :id)")
        .setParameter("userId", player.getId())
        .setParameter("id", id)
        .executeUpdate();
  }

  private List<String> linksOf(String table, String column) {
    entityManager.flush();
    @SuppressWarnings("unchecked")
    List<String> ids =
        entityManager
            .createNativeQuery(
                "SELECT " + column + " FROM " + table + " WHERE user_id = :userId ORDER BY 1")
            .setParameter("userId", player.getId())
            .getResultList();
    return ids;
  }

  private static List<String> sorted(String... ids) {
    return List.of(ids).stream().sorted().toList();
  }

  private PlayerInput changedPlayerAdding(Tag tag, Team team) {
    PlayerInput input = PlayerFixture.createPlayerInput();
    input.setEmail(player.getEmail());
    input.setFirstname("Changed-" + UUID.randomUUID());
    input.setTagIds(List.of(tag.getId()));
    input.setTeamIds(List.of(team.getId()));
    return input;
  }

  @Test
  @WithMockUser(isAdmin = true)
  @DisplayName("With no selector, an upsert keeps the tags and teams of the other tenant")
  void given_noSelectorOnDefaultTenant_should_keepTheLinksOfB() throws Exception {
    // -- PREPARE --
    Tag addedTag = tagIn(Tenant.DEFAULT_TENANT_UUID);
    Team addedTeam = teamIn(Tenant.DEFAULT_TENANT_UUID);
    entityManager.flush();
    entityManager.clear();
    TenantContext.clearCurrentTenant();

    // -- EXECUTE --
    mvc.perform(
            post(PLAYER_URI + "/upsert")
                .content(asJsonString(changedPlayerAdding(addedTag, addedTeam)))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        // -- ASSERT --
        .andExpect(status().is2xxSuccessful())
        .andExpect(
            jsonPath("$.user_tags")
                .value(containsInAnyOrder(tagOfDefault.getId(), addedTag.getId())))
        .andExpect(
            jsonPath("$.user_teams")
                .value(containsInAnyOrder(teamOfDefault.getId(), addedTeam.getId())));
    assertEquals(
        sorted(tagOfDefault.getId(), tagOfB.getId(), addedTag.getId()),
        linksOf("users_tags", "tag_id"));
    assertEquals(
        sorted(teamOfDefault.getId(), teamOfB.getId(), addedTeam.getId()),
        linksOf("users_teams", "team_id"));
  }

  @Test
  @WithMockUser(isAdmin = true)
  @DisplayName(
      "With X-Tenant-Ids naming B, an upsert keeps the tags and teams of the default tenant")
  void given_headerNamingTenantB_should_keepTheLinksOfTheDefaultTenant() throws Exception {
    // -- PREPARE --
    Tag addedTag = tagIn(tenantB);
    Team addedTeam = teamIn(tenantB);
    entityManager.flush();
    entityManager.clear();
    TenantContext.clearCurrentTenant();

    // -- EXECUTE --
    mvc.perform(
            post(PLAYER_URI + "/upsert")
                .header(TENANT_HEADER, tenantB)
                .content(asJsonString(changedPlayerAdding(addedTag, addedTeam)))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        // -- ASSERT --
        .andExpect(status().is2xxSuccessful())
        .andExpect(
            jsonPath("$.user_tags").value(containsInAnyOrder(tagOfB.getId(), addedTag.getId())))
        .andExpect(
            jsonPath("$.user_teams").value(containsInAnyOrder(teamOfB.getId(), addedTeam.getId())));
    assertEquals(
        sorted(tagOfDefault.getId(), tagOfB.getId(), addedTag.getId()),
        linksOf("users_tags", "tag_id"));
    assertEquals(
        sorted(teamOfDefault.getId(), teamOfB.getId(), addedTeam.getId()),
        linksOf("users_teams", "team_id"));
  }

  private UserInput userInputReplacingTagsWith(Tag tag) {
    return new UserInput(
        player.getEmail(),
        "Changed",
        "User",
        UserFixture.RAW_PASSWORD,
        null,
        null,
        null,
        null,
        List.of(tag.getId()),
        false,
        List.of());
  }

  private PlayerInput playerInputReplacingTagsWith(Tag tag) {
    PlayerInput input = PlayerFixture.createPlayerInput();
    input.setEmail(player.getEmail());
    input.setTagIds(List.of(tag.getId()));
    input.setTeamIds(List.of());
    return input;
  }

  private Tag addedTagIn(String tenantId) {
    Tag tag = tagIn(tenantId);
    entityManager.flush();
    entityManager.clear();
    TenantContext.clearCurrentTenant();
    return tag;
  }

  @Nested
  @DisplayName("Updating the tags of a user")
  class TagReplacement {

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("With no selector, a user update replaces the tags of the default tenant only")
    void given_noSelectorOnDefaultTenant_should_keepTheTagsOfBOnUserUpdate() throws Exception {
      // -- PREPARE --
      Tag replacement = addedTagIn(Tenant.DEFAULT_TENANT_UUID);

      // -- EXECUTE --
      mvc.perform(
              put(USER_URI + "/" + player.getId())
                  .content(asJsonString(userInputReplacingTagsWith(replacement)))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          // -- ASSERT --
          .andExpect(status().isOk());
      assertEquals(sorted(tagOfB.getId(), replacement.getId()), linksOf("users_tags", "tag_id"));
    }

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("With X-Tenant-Ids naming B, a user update replaces the tags of B only")
    void given_headerNamingTenantB_should_keepTheTagsOfTheDefaultTenantOnUserUpdate()
        throws Exception {
      // -- PREPARE --
      Tag replacement = addedTagIn(tenantB);

      // -- EXECUTE --
      mvc.perform(
              put(USER_URI + "/" + player.getId())
                  .header(TENANT_HEADER, tenantB)
                  .content(asJsonString(userInputReplacingTagsWith(replacement)))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          // -- ASSERT --
          .andExpect(status().isOk());
      assertEquals(
          sorted(tagOfDefault.getId(), replacement.getId()), linksOf("users_tags", "tag_id"));
    }

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("With X-Tenant-Ids naming B, a player update replaces the tags of B only")
    void given_headerNamingTenantB_should_keepTheTagsOfTheDefaultTenantOnPlayerUpdate()
        throws Exception {
      // -- PREPARE --
      Tag replacement = addedTagIn(tenantB);

      // -- EXECUTE --
      mvc.perform(
              put(PLAYER_URI + "/" + player.getId())
                  .header(TENANT_HEADER, tenantB)
                  .content(asJsonString(playerInputReplacingTagsWith(replacement)))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          // -- ASSERT --
          .andExpect(status().isOk());
      assertEquals(
          sorted(tagOfDefault.getId(), replacement.getId()), linksOf("users_tags", "tag_id"));
    }
  }
}
