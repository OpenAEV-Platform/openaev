package io.openaev.rest.user;

import static io.openaev.rest.user.PlayerApi.PLAYER_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Organization;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tag;
import io.openaev.database.model.Team;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.OrganizationRepository;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.database.repository.TeamRepository;
import io.openaev.rest.user.form.player.PlayerBulkProcessingInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.OrganizationFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.TagFixture;
import io.openaev.utils.fixtures.TeamFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.composers.UserComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * A player is platform-level, so deleting one is addressed to a tenant: the endpoint removes the
 * player from the tenant the request writes in, as the tenant user endpoint does, and answers 404
 * for a player who is not a member of that tenant. The memberships are read from the database, on
 * the prefixed route and on the route that names the tenant in {@code X-Tenant-Ids}.
 *
 * <p>What the delete removes in the tenant: the player's team memberships, its simulation and
 * scenario team users, its tag links and its organization. What it keeps: the history of the
 * simulations (the communications the player received, the comchecks it is asked to answer), which
 * a deleted account takes with it only when no membership is left.
 */
@TestInstance(PER_CLASS)
@Transactional
@DisplayName("Player deletion in the tenant of the request")
public class PlayerDeleteWriteTenantTest extends IntegrationTest {

  /** Production's active tables: the handler reads and writes tables the inspector gates. */
  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  private static final String TENANT_HEADER = "X-Tenant-Ids";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantIsolationHelper;
  @Autowired private UserComposer userComposer;
  @Autowired private TeamRepository teamRepository;
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private ScenarioRepository scenarioRepository;
  @Autowired private TagRepository tagRepository;
  @Autowired private OrganizationRepository organizationRepository;

  private String tenantA;
  private String tenantB;

  @BeforeEach
  void callerOfTheDefaultTenantAndB() throws Exception {
    tenantIsolationHelper.attachCurrentUserToTenant(Tenant.DEFAULT_TENANT_UUID);
    tenantB = tenantIsolationHelper.createTenantWithCurrentUser("player-delete-b").getId();
    tenantA = tenantIsolationHelper.createTenant("player-delete-a").getId();
  }

  @AfterEach
  void clearAmbientTenant() {
    TenantContext.clearCurrentTenant();
  }

  private User playerOf(String... tenantIds) {
    User player = userComposer.forUser(UserFixture.getUserWithDefaultEmail()).persist().get();
    for (String tenantId : tenantIds) {
      tenantIsolationHelper.attachUserToTenant(player.getId(), tenantId);
    }
    entityManager.flush();
    entityManager.clear();
    TenantContext.clearCurrentTenant();
    return player;
  }

  @SuppressWarnings("unchecked")
  private List<String> membershipsOf(User player) {
    entityManager.flush();
    return entityManager
        .createNativeQuery(
            "SELECT tenant_id FROM users_tenants WHERE user_id = :id ORDER BY tenant_id")
        .setParameter("id", player.getId())
        .getResultList();
  }

  /** Builds and saves what the fixtures stamp with the ambient tenant, in the given tenant. */
  private <T> T inTenant(String tenantId, Supplier<T> create) {
    TenantContext.setCurrentTenant(tenantId);
    try {
      return create.get();
    } finally {
      TenantContext.clearCurrentTenant();
    }
  }

  /** What one tenant holds about a player: a team used by a simulation and a scenario, a tag. */
  private record LinksOf(
      Team team, Exercise exercise, Scenario scenario, Tag tag, Organization org) {}

  // Tags and organizations are v2 tables: nothing stamps the ambient tenant on them.
  private static Tag tagOf(String tenantId) {
    Tag tag = TagFixture.getTagWithText("tag-of-" + tenantId + UUID.randomUUID());
    tag.setTenant(new Tenant(tenantId));
    return tag;
  }

  private static Organization organizationOf(String tenantId) {
    Organization organization = OrganizationFixture.createDefaultOrganisation();
    organization.setTenant(new Tenant(tenantId));
    return organization;
  }

  private LinksOf linkTo(User player, String tenantId) {
    LinksOf links =
        inTenant(
            tenantId,
            () ->
                new LinksOf(
                    teamRepository.save(TeamFixture.getEmptyTeam()),
                    exerciseRepository.save(ExerciseFixture.createDefaultExercise()),
                    scenarioRepository.save(ScenarioFixture.createDefaultCrisisScenario()),
                    tagRepository.save(tagOf(tenantId)),
                    organizationRepository.save(organizationOf(tenantId))));
    entityManager.flush();
    execute(
        "INSERT INTO users_teams (user_id, team_id) VALUES (:user, :team)",
        player,
        "team",
        links.team().getId());
    execute(
        "INSERT INTO exercises_teams_users (exercise_id, team_id, user_id)"
            + " VALUES (:exercise, :team, :user)",
        player,
        "exercise",
        links.exercise().getId(),
        "team",
        links.team().getId());
    execute(
        "INSERT INTO scenarios_teams_users (scenario_id, team_id, user_id)"
            + " VALUES (:scenario, :team, :user)",
        player,
        "scenario",
        links.scenario().getId(),
        "team",
        links.team().getId());
    execute(
        "INSERT INTO users_tags (user_id, tag_id) VALUES (:user, :tag)",
        player,
        "tag",
        links.tag().getId());
    execute(
        "INSERT INTO users_organizations (user_id, organization_id) VALUES (:user, :org)",
        player,
        "org",
        links.org().getId());
    return links;
  }

  /** The history of a simulation about a player: who received a communication, who answered. */
  private void historyOf(User player, LinksOf links) {
    String inject = UUID.randomUUID().toString();
    String communication = UUID.randomUUID().toString();
    String comcheck = UUID.randomUUID().toString();
    String tenantId = links.exercise().getTenant().getId();
    execute(
        "INSERT INTO injects (inject_id, inject_title, inject_all_teams, inject_enabled,"
            + " inject_depends_duration, inject_created_at, inject_updated_at, tenant_id,"
            + " inject_expectations_drift_dismissed, inject_exercise)"
            + " VALUES (:inject, 'history', false, true, 0, now(), now(), :tenant, false,"
            + " :exercise)",
        player,
        "inject",
        inject,
        "tenant",
        tenantId,
        "exercise",
        links.exercise().getId());
    execute(
        "INSERT INTO communications (communication_id, communication_received_at,"
            + " communication_sent_at, communication_message_id, communication_from,"
            + " communication_to, communication_inject)"
            + " VALUES (:communication, now(), now(), :communication, 'from', 'to', :inject)",
        player,
        "communication",
        communication,
        "inject",
        inject);
    execute(
        "INSERT INTO communications_users (communication_id, user_id)"
            + " VALUES (:communication, :user)",
        player,
        "communication",
        communication);
    execute(
        "INSERT INTO comchecks (comcheck_id, comcheck_exercise, comcheck_start_date,"
            + " comcheck_end_date) VALUES (:comcheck, :exercise, now(), now())",
        player,
        "comcheck",
        comcheck,
        "exercise",
        links.exercise().getId());
    execute(
        "INSERT INTO comchecks_statuses (status_id, status_user, status_comcheck)"
            + " VALUES (:status, :user, :comcheck)",
        player,
        "status",
        UUID.randomUUID().toString(),
        "comcheck",
        comcheck);
  }

  private void execute(String sql, User player, Object... namesAndValues) {
    var query = entityManager.createNativeQuery(sql);
    if (sql.contains(":user")) {
      query.setParameter("user", player.getId());
    }
    for (int i = 0; i < namesAndValues.length; i += 2) {
      query.setParameter((String) namesAndValues[i], namesAndValues[i + 1]);
    }
    query.executeUpdate();
  }

  /** The number of rows the player holds in a link table, restricted to the given ids. */
  private int rowsOf(User player, String table, String column, String id) {
    entityManager.flush();
    return ((Number)
            entityManager
                .createNativeQuery(
                    "SELECT count(*) FROM "
                        + table
                        + " WHERE user_id = :id AND "
                        + column
                        + " = :linked")
                .setParameter("id", player.getId())
                .setParameter("linked", id)
                .getSingleResult())
        .intValue();
  }

  /** The link rows the player holds to what one tenant owns, by link table. */
  private Map<String, Integer> linksHeldBy(User player, LinksOf links) {
    Map<String, Integer> rows = new LinkedHashMap<>();
    rows.put("users_teams", rowsOf(player, "users_teams", "team_id", links.team().getId()));
    rows.put(
        "exercises_teams_users",
        rowsOf(player, "exercises_teams_users", "exercise_id", links.exercise().getId()));
    rows.put(
        "scenarios_teams_users",
        rowsOf(player, "scenarios_teams_users", "scenario_id", links.scenario().getId()));
    rows.put("users_tags", rowsOf(player, "users_tags", "tag_id", links.tag().getId()));
    rows.put(
        "users_organizations",
        rowsOf(player, "users_organizations", "organization_id", links.org().getId()));
    return rows;
  }

  /** The history rows the player holds, by table, over every tenant. */
  private Map<String, Integer> historyHeldBy(User player) {
    entityManager.flush();
    Map<String, Integer> rows = new LinkedHashMap<>();
    rows.put(
        "communications_users",
        count("SELECT count(*) FROM communications_users WHERE user_id = :id", player));
    rows.put(
        "comchecks_statuses",
        count("SELECT count(*) FROM comchecks_statuses WHERE status_user = :id", player));
    return rows;
  }

  private int count(String sql, User player) {
    return ((Number)
            entityManager
                .createNativeQuery(sql)
                .setParameter("id", player.getId())
                .getSingleResult())
        .intValue();
  }

  /** What the delete leaves of the player's links in the tenant: the five link tables are empty. */
  private static final Map<String, Integer> NO_LINKS =
      Map.of(
          "users_teams", 0,
          "exercises_teams_users", 0,
          "scenarios_teams_users", 0,
          "users_tags", 0,
          "users_organizations", 0);

  private static final Map<String, Integer> HISTORY_OF_TWO_TENANTS =
      Map.of("communications_users", 2, "comchecks_statuses", 2);

  private static final Map<String, Integer> NO_HISTORY =
      Map.of("communications_users", 0, "comchecks_statuses", 0);

  private static final Map<String, Integer> ALL_LINKS =
      Map.of(
          "users_teams", 1,
          "exercises_teams_users", 1,
          "scenarios_teams_users", 1,
          "users_tags", 1,
          "users_organizations", 1);

  private boolean exists(User player) {
    entityManager.flush();
    return ((Number)
                entityManager
                    .createNativeQuery("SELECT count(*) FROM users WHERE user_id = :id")
                    .setParameter("id", player.getId())
                    .getSingleResult())
            .intValue()
        == 1;
  }

  private MockHttpServletRequestBuilder deleteOne(User player, boolean prefixedRoute) {
    MockHttpServletRequestBuilder request =
        prefixedRoute
            ? delete("/api/tenants/" + tenantB + "/players/" + player.getId())
            : delete(PLAYER_URI + "/" + player.getId()).header(TENANT_HEADER, tenantB);
    return request.contentType(MediaType.APPLICATION_JSON).with(csrf());
  }

  private MockHttpServletRequestBuilder deleteMany(boolean prefixedRoute, User... players) {
    PlayerBulkProcessingInput input = new PlayerBulkProcessingInput();
    input.setUserIdsToProcess(List.of(players).stream().map(User::getId).toList());
    MockHttpServletRequestBuilder request =
        prefixedRoute
            ? delete("/api/tenants/" + tenantB + "/players")
            : delete(PLAYER_URI).header(TENANT_HEADER, tenantB);
    return request
        .content(asJsonString(input))
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .with(csrf());
  }

  @Nested
  @DisplayName("Delete of one player")
  class DeleteOne {

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("given_playerOfAnotherTenantOnly_should_answerNotFoundOnThePrefixedRoute")
    void given_playerOfAnotherTenantOnly_should_answerNotFoundOnThePrefixedRoute()
        throws Exception {
      // Arrange
      User player = playerOf(tenantA);

      // Act
      mvc.perform(deleteOne(player, true))
          // Assert
          .andExpect(status().isNotFound());
      assertThat(exists(player)).isTrue();
      assertThat(membershipsOf(player)).containsExactly(tenantA);
    }

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("given_playerOfTheDefaultTenantOnly_should_answerNotFoundWhenTheHeaderNamesB")
    void given_playerOfTheDefaultTenantOnly_should_answerNotFoundWhenTheHeaderNamesB()
        throws Exception {
      // Arrange
      User player = playerOf(Tenant.DEFAULT_TENANT_UUID);

      // Act
      mvc.perform(deleteOne(player, false))
          // Assert
          .andExpect(status().isNotFound());
      assertThat(exists(player)).isTrue();
      assertThat(membershipsOf(player)).containsExactly(Tenant.DEFAULT_TENANT_UUID);
    }

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("given_memberOfBAndA_should_removeItFromBOnlyOnThePrefixedRoute")
    void given_memberOfBAndA_should_removeItFromBOnlyOnThePrefixedRoute() throws Exception {
      // Arrange
      User player = playerOf(tenantA, tenantB);

      // Act
      mvc.perform(deleteOne(player, true)).andExpect(status().is2xxSuccessful());

      // Assert
      assertThat(exists(player)).isTrue();
      assertThat(membershipsOf(player)).containsExactly(tenantA);
    }

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("given_memberOfBAndA_should_removeItFromBOnlyWhenTheHeaderNamesB")
    void given_memberOfBAndA_should_removeItFromBOnlyWhenTheHeaderNamesB() throws Exception {
      // Arrange
      User player = playerOf(tenantA, tenantB);

      // Act
      mvc.perform(deleteOne(player, false)).andExpect(status().is2xxSuccessful());

      // Assert
      assertThat(exists(player)).isTrue();
      assertThat(membershipsOf(player)).containsExactly(tenantA);
    }

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("given_memberOfBOnly_should_deleteTheAccount")
    void given_memberOfBOnly_should_deleteTheAccount() throws Exception {
      // Arrange
      User player = playerOf(tenantB);

      // Act
      mvc.perform(deleteOne(player, true)).andExpect(status().is2xxSuccessful());

      // Assert
      assertThat(exists(player)).isFalse();
    }

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("given_memberOfBOnlyWithLinksOfB_should_removeEveryLinkAndTheAccount")
    void given_memberOfBOnlyWithLinksOfB_should_removeEveryLinkAndTheAccount() throws Exception {
      // Arrange
      User player = playerOf(tenantB);
      LinksOf linksOfB = linkTo(player, tenantB);
      historyOf(player, linksOfB);
      assertThat(linksHeldBy(player, linksOfB)).isEqualTo(ALL_LINKS);

      // Act
      mvc.perform(deleteOne(player, true)).andExpect(status().is2xxSuccessful());

      // Assert
      assertThat(linksHeldBy(player, linksOfB)).isEqualTo(NO_LINKS);
      assertThat(historyHeldBy(player))
          .as("history goes with the deleted account")
          .isEqualTo(NO_HISTORY);
      assertThat(exists(player)).isFalse();
    }

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("given_memberOfAAndBWithHistoryOfBoth_should_keepTheHistoryOfTheSimulations")
    void given_memberOfAAndBWithHistoryOfBoth_should_keepTheHistoryOfTheSimulations()
        throws Exception {
      // Arrange
      User player = playerOf(tenantA, tenantB);
      LinksOf linksOfA = linkTo(player, tenantA);
      LinksOf linksOfB = linkTo(player, tenantB);
      historyOf(player, linksOfA);
      historyOf(player, linksOfB);
      assertThat(historyHeldBy(player)).isEqualTo(HISTORY_OF_TWO_TENANTS);

      // Act
      mvc.perform(deleteOne(player, true)).andExpect(status().is2xxSuccessful());

      // Assert
      assertThat(linksHeldBy(player, linksOfB)).isEqualTo(NO_LINKS);
      assertThat(historyHeldBy(player))
          .as("who received a communication and who answered a comcheck stay")
          .isEqualTo(HISTORY_OF_TWO_TENANTS);
      assertThat(exists(player)).isTrue();
    }

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("given_memberOfAAndBWithLinksOfBoth_should_removeTheLinksOfBOnly")
    void given_memberOfAAndBWithLinksOfBoth_should_removeTheLinksOfBOnly() throws Exception {
      // Arrange
      User player = playerOf(tenantA, tenantB);
      LinksOf linksOfA = linkTo(player, tenantA);
      LinksOf linksOfB = linkTo(player, tenantB);

      // Act
      mvc.perform(deleteOne(player, false)).andExpect(status().is2xxSuccessful());

      // Assert
      assertThat(linksHeldBy(player, linksOfB)).isEqualTo(NO_LINKS);
      assertThat(linksHeldBy(player, linksOfA)).isEqualTo(ALL_LINKS);
      assertThat(exists(player)).isTrue();
      assertThat(membershipsOf(player)).containsExactly(tenantA);
    }
  }

  @Nested
  @DisplayName("Bulk delete of players")
  class DeleteMany {

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("given_playersOfOtherTenants_should_leaveThemWhenTheHeaderNamesB")
    void given_playersOfOtherTenants_should_leaveThemWhenTheHeaderNamesB() throws Exception {
      // Arrange
      User ofDefault = playerOf(Tenant.DEFAULT_TENANT_UUID);
      User ofA = playerOf(tenantA);
      User ofBAndA = playerOf(tenantA, tenantB);

      // Act
      mvc.perform(deleteMany(false, ofDefault, ofA, ofBAndA)).andExpect(status().isOk());

      // Assert
      assertThat(exists(ofDefault)).isTrue();
      assertThat(membershipsOf(ofDefault)).containsExactly(Tenant.DEFAULT_TENANT_UUID);
      assertThat(exists(ofA)).isTrue();
      assertThat(membershipsOf(ofA)).containsExactly(tenantA);
      assertThat(exists(ofBAndA)).isTrue();
      assertThat(membershipsOf(ofBAndA)).containsExactly(tenantA);
    }

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("given_playersOfOtherTenants_should_leaveThemOnThePrefixedRoute")
    void given_playersOfOtherTenants_should_leaveThemOnThePrefixedRoute() throws Exception {
      // Arrange
      User ofA = playerOf(tenantA);
      User ofBAndA = playerOf(tenantA, tenantB);

      // Act
      mvc.perform(deleteMany(true, ofA, ofBAndA)).andExpect(status().isOk());

      // Assert
      assertThat(exists(ofA)).isTrue();
      assertThat(membershipsOf(ofA)).containsExactly(tenantA);
      assertThat(exists(ofBAndA)).isTrue();
      assertThat(membershipsOf(ofBAndA)).containsExactly(tenantA);
    }

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("given_playersWithLinksOfB_should_removeThemAndDeleteTheOnesWithoutMembership")
    void given_playersWithLinksOfB_should_removeThemAndDeleteTheOnesWithoutMembership()
        throws Exception {
      // Arrange
      User ofBOnly = playerOf(tenantB);
      LinksOf linksOfBOnly = linkTo(ofBOnly, tenantB);
      User ofBAndA = playerOf(tenantA, tenantB);
      LinksOf linksOfA = linkTo(ofBAndA, tenantA);
      LinksOf linksOfB = linkTo(ofBAndA, tenantB);

      // Act
      mvc.perform(deleteMany(true, ofBOnly, ofBAndA)).andExpect(status().isOk());

      // Assert
      assertThat(linksHeldBy(ofBOnly, linksOfBOnly)).isEqualTo(NO_LINKS);
      assertThat(exists(ofBOnly)).isFalse();
      assertThat(linksHeldBy(ofBAndA, linksOfB)).isEqualTo(NO_LINKS);
      assertThat(linksHeldBy(ofBAndA, linksOfA)).isEqualTo(ALL_LINKS);
      assertThat(membershipsOf(ofBAndA)).containsExactly(tenantA);
    }
  }
}
