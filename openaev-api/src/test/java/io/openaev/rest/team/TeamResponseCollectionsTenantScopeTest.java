package io.openaev.rest.team;

import static io.openaev.rest.team.TeamApi.TENANT_TEAM_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.InjectFixture.getDefaultInject;
import static io.openaev.utils.fixtures.UserFixture.getUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.ProductionTablesIntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Team;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.database.repository.TeamRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.rest.team.form.TeamCreateInput;
import io.openaev.rest.team.form.TeamUpdateInput;
import io.openaev.rest.team.form.UpdateUsersTeamInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The team endpoints that answer a raw {@code Team}: create, upsert, update and update players. The
 * JSON carries the simulations, scenarios and tags of the team and the injects of those simulations
 * and scenarios, lazy collections serialized open-in-view after the commit, once the
 * transaction-local tenant scope is gone.
 *
 * <p>The MockMvc cases pin the JSON of each endpoint on the tenant-prefixed route, as a regression
 * guard: this JSON is right even without an explicit load, since the stream event serializes the
 * saved team at flush, inside the scope. The {@link LoadState} cases pin the load inside the
 * transaction: each endpoint runs as a bean in a read-only transaction that never flushes, so no
 * stream event is built and the answered collections are initialized by the endpoint or not at all.
 *
 * <p>The class runs with the production list of isolated tables and is deliberately NOT {@code
 * Transactional}: a rolled-back test transaction never commits, so its scope stays alive through
 * serialization. Rows are seeded through an auto-committing {@link JdbcTemplate}, injects and
 * players through a committed transaction scoped to their tenant.
 */
@WithMockUser(isAdmin = true)
@DisplayName("Team responses carry the simulations, scenarios and tags of the team")
class TeamResponseCollectionsTenantScopeTest extends ProductionTablesIntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private DataSource dataSource;
  @Autowired private TeamRepository teamRepository;
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private ScenarioRepository scenarioRepository;
  @Autowired private InjectRepository injectRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private TeamApi teamApi;

  private JdbcTemplate jdbc;
  private String tenant;
  private String foreignTenant;
  private final List<String[]> seededRows = new ArrayList<>();
  private final List<String[]> seededInjects = new ArrayList<>();
  private final List<String> seededUsers = new ArrayList<>();

  /** A team, its name, and the simulations, scenario, tag and injects it is linked to. */
  private record SeededTeam(
      String teamId,
      String teamName,
      List<String> exerciseIds,
      String scenarioId,
      String tagId,
      String exerciseInjectId,
      String scenarioInjectId) {}

  @BeforeEach
  void seedTenant() throws Exception {
    jdbc = new JdbcTemplate(dataSource);
    tenant = tenantHelper.createTenantWithCurrentUser("team-response-" + UUID.randomUUID()).getId();
  }

  @AfterEach
  void cleanup() {
    for (String[] inject : seededInjects) {
      inTenantScope(inject[0], () -> deleteInject(inject[1]));
    }
    inTenantScope(tenant, this::deletePlayers);
    // Reverse insertion order: links before the rows they reference.
    for (int i = seededRows.size() - 1; i >= 0; i--) {
      String[] row = seededRows.get(i);
      jdbc.update("DELETE FROM " + row[0] + " WHERE " + row[1] + " = ?", row[2]);
    }
    tenantHelper.deleteCommittedTenants(tenant, foreignTenant);
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("JSON of each endpoint on the tenant-prefixed route")
  class ResponseJson {

    @Test
    @DisplayName("given a linked team, should answer its links on update")
    void given_linkedTeam_should_answerItsLinksOnUpdate() throws Exception {
      // -- ARRANGE --
      SeededTeam seeded = seedTeam(true);

      // -- ACT --
      ResultActions response =
          call(put(TENANT_TEAM_URI + "/{teamId}", tenant, seeded.teamId()), updateInput(seeded));

      // -- ASSERT --
      expectLinks(response, seeded);
    }

    @Test
    @DisplayName("given a team of two players, should answer its links when one is removed")
    void given_teamOfTwoPlayers_should_answerItsLinksOnRemoval() throws Exception {
      // -- ARRANGE --
      SeededTeam seeded = seedTeam(true);
      String remainingPlayer = seedPlayerOf(seeded.teamId());
      seedPlayerOf(seeded.teamId());
      UpdateUsersTeamInput input = new UpdateUsersTeamInput();
      input.setUserIds(List.of(remainingPlayer));

      // -- ACT --
      ResultActions response =
          call(put(TENANT_TEAM_URI + "/{teamId}/players", tenant, seeded.teamId()), input);

      // -- ASSERT --
      // Removing a player reloads the team after the persistence context is cleared.
      expectLinks(response, seeded);
      response.andExpect(jsonPath("$.team_users", contains(remainingPlayer)));
    }

    @ParameterizedTest(name = "POST {0}")
    @ValueSource(strings = {"", "/upsert"})
    @DisplayName("given two simulations, a scenario and a tag, should answer them on creation")
    void given_linksToCreate_should_answerThem(String suffix) throws Exception {
      // -- ARRANGE --
      SeededTeam seeded = seedTeam(false);

      // -- ACT --
      ResultActions response =
          call(
              post(TENANT_TEAM_URI + suffix, tenant),
              createInput(seeded, "new-" + UUID.randomUUID()));

      // -- ASSERT --
      // The injects target all teams, so they belong to the new team.
      expectLinks(response, seeded);
    }
  }

  @Nested
  @DisplayName("Links to the rows of another tenant")
  class ForeignLinks {

    @Test
    @DisplayName("given links to a foreign simulation and scenario, should answer none of them")
    void given_foreignLinks_should_answerNoneOfThem() throws Exception {
      // -- ARRANGE --
      SeededTeam seeded = seedTeam(true);
      foreignTenant = tenantHelper.createTenant("team-response-foreign").getId();
      String foreignExercise = insert("exercises", "exercise", "", "", foreignTenant);
      String foreignScenario = insert("scenarios", "scenario", "", "", foreignTenant);
      link("exercises_teams", "exercise_id", foreignExercise, "team_id", seeded.teamId());
      link("scenarios_teams", "scenario_id", foreignScenario, "team_id", seeded.teamId());
      String foreignExerciseInject = seedInject(foreignTenant, null, foreignExercise, null);
      String foreignScenarioInject = seedInject(foreignTenant, null, null, foreignScenario);

      // -- ACT --
      ResultActions response =
          call(put(TENANT_TEAM_URI + "/{teamId}", tenant, seeded.teamId()), updateInput(seeded));

      // -- ASSERT --
      expectLinks(response, seeded);
      response
          .andExpect(jsonPath("$.team_exercises", not(hasItem(foreignExercise))))
          .andExpect(jsonPath("$.team_scenarios", not(hasItem(foreignScenario))))
          .andExpect(jsonPath("$.team_exercise_injects", not(hasItem(foreignExerciseInject))))
          .andExpect(jsonPath("$.team_scenario_injects", not(hasItem(foreignScenarioInject))));
    }
  }

  @Nested
  @DisplayName("Load state of the team an endpoint answers")
  class LoadState {

    @ParameterizedTest(name = "{0}")
    @EnumSource(Answer.class)
    @DisplayName("given a linked team, should load its collections before the commit")
    void given_linkedTeam_should_loadItsCollectionsBeforeTheCommit(Answer answer) {
      // -- ARRANGE --
      SeededTeam seeded = seedTeam(true);
      String player = seedPlayerOf(seeded.teamId());
      TxCtx scope = TxCtx.forTenant(tenant);

      // -- ACT --
      // The session never flushes here, so the stream event that serializes the saved team in the
      // scope is not built: the collections are loaded by the endpoint or not at all.
      Team answered = inReadOnlyTenantScope(() -> answer.call.call(teamApi, scope, seeded, player));

      // -- ASSERT --
      assertThat(Hibernate.isInitialized(answered.getTags())).as("tags").isTrue();
      assertThat(Hibernate.isInitialized(answered.getUsers())).as("users").isTrue();
      assertThat(Hibernate.isInitialized(answered.getExercises())).as("simulations").isTrue();
      assertThat(Hibernate.isInitialized(answered.getScenarios())).as("scenarios").isTrue();
      // Mapped to booleans first: a failure message printing the entities would load them.
      assertThat(answered.getExercises().stream().map(e -> Hibernate.isInitialized(e.getInjects())))
          .as("injects of each simulation")
          .containsOnly(true);
      assertThat(answered.getScenarios().stream().map(s -> Hibernate.isInitialized(s.getInjects())))
          .as("injects of each scenario")
          .containsOnly(true);
    }
  }

  /** How a load-state case calls the controller bean. */
  private interface AnswerCall {
    Team call(TeamApi api, TxCtx ctx, SeededTeam seeded, String player);
  }

  /** The endpoints that answer a raw team; the upsert has one branch per team name. */
  enum Answer {
    UPDATE((api, ctx, seeded, player) -> api.updateTeam(ctx, seeded.teamId(), updateInput(seeded))),
    UPDATE_PLAYERS(
        (api, ctx, seeded, player) -> {
          UpdateUsersTeamInput input = new UpdateUsersTeamInput();
          input.setUserIds(List.of(player));
          return api.updateTeamUsers(ctx, seeded.teamId(), input);
        }),
    CREATE((api, ctx, seeded, player) -> api.createTeam(ctx, createInput(seeded, "new-team"))),
    UPSERT_NEW((api, ctx, seeded, player) -> api.upsertTeam(ctx, createInput(seeded, "new-team"))),
    UPSERT_EXISTING(
        (api, ctx, seeded, player) -> api.upsertTeam(ctx, createInput(seeded, seeded.teamName())));

    private final AnswerCall call;

    Answer(AnswerCall call) {
      this.call = call;
    }
  }

  // -- INPUTS, CALLS AND ASSERTIONS --

  private static TeamUpdateInput updateInput(SeededTeam seeded) {
    TeamUpdateInput input = new TeamUpdateInput();
    input.setName("renamed-" + UUID.randomUUID());
    input.setTagIds(List.of(seeded.tagId()));
    return input;
  }

  private static TeamCreateInput createInput(SeededTeam seeded, String name) {
    TeamCreateInput input = new TeamCreateInput();
    input.setName(name);
    input.setTagIds(List.of(seeded.tagId()));
    input.setExerciseIds(seeded.exerciseIds());
    input.setScenarioIds(List.of(seeded.scenarioId()));
    return input;
  }

  private ResultActions call(MockHttpServletRequestBuilder request, Object input) throws Exception {
    return mvc.perform(
            request
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(input))
                .with(csrf()))
        .andExpect(status().isOk());
  }

  private static void expectLinks(ResultActions response, SeededTeam seeded) throws Exception {
    response
        .andExpect(jsonPath("$.team_tags", contains(seeded.tagId())))
        .andExpect(jsonPath("$.team_exercises", containsInAnyOrder(seeded.exerciseIds().toArray())))
        .andExpect(jsonPath("$.team_scenarios", contains(seeded.scenarioId())))
        .andExpect(jsonPath("$.team_exercise_injects", contains(seeded.exerciseInjectId())))
        .andExpect(jsonPath("$.team_exercise_injects_number").value(1))
        .andExpect(jsonPath("$.team_scenario_injects", contains(seeded.scenarioInjectId())))
        .andExpect(jsonPath("$.team_scenario_injects_number").value(1));
  }

  // -- SEEDING --

  /**
   * A tag, two simulations and a scenario of the test tenant, the first simulation and the scenario
   * carrying one inject each. With {@code linked}, a team holds them all and the injects target it;
   * otherwise the team is left to the request to create and the injects target all teams.
   */
  private SeededTeam seedTeam(boolean linked) {
    String tagId = insert("tags", "tag", ", tag_color", ", '#ffffff'", tenant);
    String firstExercise = insert("exercises", "exercise", "", "", tenant);
    String secondExercise = insert("exercises", "exercise", "", "", tenant);
    String scenarioId = insert("scenarios", "scenario", "", "", tenant);
    String teamId = null;
    if (linked) {
      teamId =
          insert(
              "teams",
              "team",
              ", team_contextual, team_created_at, team_updated_at",
              ", false, now(), now()",
              tenant);
      link("teams_tags", "team_id", teamId, "tag_id", tagId);
      link("exercises_teams", "exercise_id", firstExercise, "team_id", teamId);
      link("exercises_teams", "exercise_id", secondExercise, "team_id", teamId);
      link("scenarios_teams", "scenario_id", scenarioId, "team_id", teamId);
    }
    return new SeededTeam(
        teamId,
        linked ? nameOf(teamId) : null,
        List.of(firstExercise, secondExercise),
        scenarioId,
        tagId,
        seedInject(tenant, teamId, firstExercise, null),
        seedInject(tenant, teamId, null, scenarioId));
  }

  /** An inject of a simulation or a scenario, aimed at the team, or at all teams when null. */
  private String seedInject(String tenantId, String teamId, String exerciseId, String scenarioId) {
    return inTenantScope(
        tenantId,
        () -> {
          Inject inject = getDefaultInject();
          inject.setTenant(new Tenant(tenantId));
          if (exerciseId != null) {
            inject.setExercise(exerciseRepository.findById(exerciseId).orElseThrow());
          }
          if (scenarioId != null) {
            inject.setScenario(scenarioRepository.findById(scenarioId).orElseThrow());
          }
          inject.setAllTeams(teamId == null);
          if (teamId != null) {
            inject.setTeams(
                new ArrayList<>(List.of(teamRepository.findById(teamId).orElseThrow())));
          }
          String id = injectRepository.save(inject).getId();
          seededInjects.add(new String[] {tenantId, id});
          return id;
        });
  }

  private String seedPlayerOf(String teamId) {
    String email = "team-response-" + UUID.randomUUID() + "@example.com";
    String userId =
        inTenantScope(tenant, () -> userRepository.save(getUser("Team", "Player", email)).getId());
    seededUsers.add(userId);
    // A player of a team is a member of the team's tenant; the link goes with the user.
    jdbc.update("INSERT INTO users_tenants (user_id, tenant_id) VALUES (?, ?)", userId, tenant);
    link("users_teams", "team_id", teamId, "user_id", userId);
    return userId;
  }

  private static String nameOf(String id) {
    return "team-response-" + id;
  }

  /** Inserts a row named after its id in the tenant, and records it for cleanup. */
  private String insert(
      String table, String prefix, String extraColumns, String extraValues, String tenantId) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO %s (%s_id, %s_name%s, tenant_id) VALUES (?, ?%s, ?)"
            .formatted(table, prefix, prefix, extraColumns, extraValues),
        id,
        nameOf(id),
        tenantId);
    seededRows.add(new String[] {table, prefix + "_id", id});
    return id;
  }

  private void link(
      String table, String leftColumn, String leftId, String rightColumn, String rightId) {
    jdbc.update(
        "INSERT INTO %s (%s, %s) VALUES (?, ?)".formatted(table, leftColumn, rightColumn),
        leftId,
        rightId);
    seededRows.add(new String[] {table, leftColumn, leftId});
  }

  /**
   * Runs the work in a read-only transaction scoped to the test tenant: its flush mode is manual,
   * so nothing is written and no stream event is built. The v1 thread-local tenant is set too, for
   * the code that reads it.
   */
  private <T> T inReadOnlyTenantScope(Supplier<T> work) {
    TenantContext.setCurrentTenant(tenant);
    try {
      return tenantTx.executeReadOnly(TxCtx.forTenant(tenant), work);
    } finally {
      TenantContext.clearCurrentTenant();
    }
  }

  /** Runs the work in a committed transaction scoped to the tenant, the v1 thread-local too. */
  private <T> T inTenantScope(String tenantId, Supplier<T> work) {
    TenantContext.setCurrentTenant(tenantId);
    try {
      return tenantTx.execute(TxCtx.forTenant(tenantId), work);
    } finally {
      TenantContext.clearCurrentTenant();
    }
  }

  private Void deleteInject(String injectId) {
    injectRepository.deleteById(injectId);
    return null;
  }

  private Void deletePlayers() {
    userRepository.deleteAllById(seededUsers);
    return null;
  }
}
