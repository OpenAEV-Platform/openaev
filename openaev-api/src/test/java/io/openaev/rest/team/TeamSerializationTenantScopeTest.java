package io.openaev.rest.team;

import static io.openaev.rest.exercise.ExerciseApi.TENANT_EXERCISE_URI;
import static io.openaev.rest.lessons.ExerciseLessonsApi.EXERCISE_URL;
import static io.openaev.rest.scenario.ScenarioApi.TENANT_SCENARIO_URI;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.TestUserHolder;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The simulation and scenario team-player endpoints return the raw {@code Exercise} and {@code
 * Scenario} entities, whose lazy {@code exercise_teams} and {@code scenario_teams} are serialized
 * through {@code MultiIdListSerializer} open-in-view, after the controller transaction has
 * committed. The tenant scope is transaction-local, so a lazy load at that point runs unscoped and
 * fails closed: with {@code teams} active the array comes back empty although the links exist, on
 * the very endpoints that manage those links. The collections must be initialized inside the scoped
 * transaction.
 *
 * <p>The class is deliberately NOT {@code @Transactional}: a rolled-back test transaction never
 * commits, so the scope would still be set during serialization and mask the failure. Rows are
 * seeded through an auto-committing {@link JdbcTemplate} and removed on teardown.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=teams")
@WithMockUser(isAdmin = true)
@DisplayName("Team-player endpoints serialize their team links with teams active")
class TeamSerializationTenantScopeTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private DataSource dataSource;
  @Autowired private TestUserHolder testUserHolder;

  private JdbcTemplate jdbc;
  private String tenant;
  private final List<String[]> seededRows = new ArrayList<>();

  @BeforeEach
  void seedTenant() throws Exception {
    jdbc = new JdbcTemplate(dataSource);
    tenant = tenantHelper.createTenantWithCurrentUser("team-serialization").getId();
  }

  @AfterEach
  void cleanup() {
    // Reverse insertion order: links before the rows they reference.
    for (int i = seededRows.size() - 1; i >= 0; i--) {
      String[] row = seededRows.get(i);
      jdbc.update("DELETE FROM " + row[0] + " WHERE " + row[1] + " = ?", row[2]);
    }
    seededRows.clear();
    tenantHelper.deleteCommittedTenants(tenant);
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("Simulation team players")
  class SimulationTeamPlayers {

    @Test
    @DisplayName(
        "given a simulation with a team when players are added then the response lists the team")
    void given_simulationWithTeam_should_serializeTeamsOnPlayerAdd() throws Exception {
      // -- Arrange --
      String exerciseId = seedExercise(tenant);
      String teamId = seedTeam(tenant);
      link("exercises_teams", "exercise_id", exerciseId, "team_id", teamId);

      // -- Act & Assert --
      mvc.perform(
              put(
                      TENANT_EXERCISE_URI + "/{exerciseId}/teams/{teamId}/players/add",
                      tenant,
                      exerciseId,
                      teamId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"exercise_team_players\":[]}")
                  .with(csrf()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.exercise_teams[*]", hasItem(teamId)));
    }

    @Test
    @DisplayName(
        "given a simulation with a team when players are removed then the response lists the team")
    void given_simulationWithTeam_should_serializeTeamsOnPlayerRemove() throws Exception {
      // -- Arrange --
      String exerciseId = seedExercise(tenant);
      String teamId = seedTeam(tenant);
      link("exercises_teams", "exercise_id", exerciseId, "team_id", teamId);

      // -- Act & Assert --
      mvc.perform(
              put(
                      TENANT_EXERCISE_URI + "/{exerciseId}/teams/{teamId}/players/remove",
                      tenant,
                      exerciseId,
                      teamId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"exercise_team_players\":[]}")
                  .with(csrf()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.exercise_teams[*]", hasItem(teamId)));
    }
  }

  @Nested
  @DisplayName("Scenario team players")
  class ScenarioTeamPlayers {

    @Test
    @DisplayName(
        "given a scenario with a team when players are added then the response lists the team")
    void given_scenarioWithTeam_should_serializeTeamsOnPlayerAdd() throws Exception {
      // -- Arrange --
      String scenarioId = seedScenario(tenant);
      String teamId = seedTeam(tenant);
      link("scenarios_teams", "scenario_id", scenarioId, "team_id", teamId);

      // -- Act & Assert --
      mvc.perform(
              put(
                      TENANT_SCENARIO_URI + "/{scenarioId}/teams/{teamId}/players/add",
                      tenant,
                      scenarioId,
                      teamId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"scenario_team_players\":[]}")
                  .with(csrf()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.scenario_teams[*]", hasItem(teamId)));
    }

    @Test
    @DisplayName(
        "given a scenario with a team when players are removed then the response lists the team")
    void given_scenarioWithTeam_should_serializeTeamsOnPlayerRemove() throws Exception {
      // -- Arrange --
      String scenarioId = seedScenario(tenant);
      String teamId = seedTeam(tenant);
      link("scenarios_teams", "scenario_id", scenarioId, "team_id", teamId);

      // -- Act & Assert --
      mvc.perform(
              put(
                      TENANT_SCENARIO_URI + "/{scenarioId}/teams/{teamId}/players/remove",
                      tenant,
                      scenarioId,
                      teamId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"scenario_team_players\":[]}")
                  .with(csrf()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.scenario_teams[*]", hasItem(teamId)));
    }
  }

  @Nested
  @DisplayName("Other aggregates exposing a lazy teams collection")
  class OtherAggregates {

    @Test
    @DisplayName("given the caller in a team when reading me then the response lists the team")
    void given_callerInTeam_should_serializeUserTeams() throws Exception {
      // -- Arrange --
      String teamId = seedTeam(tenant);
      String userId = testUserHolder.get().getId();
      link("users_teams", "user_id", userId, "team_id", teamId);

      // -- Act & Assert --
      mvc.perform(get("/api/tenants/{tenantId}/me", tenant))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.user_teams[*]", hasItem(teamId)));
    }

    @Test
    @DisplayName(
        "given a lessons category with a team when listing categories then the response lists the"
            + " team")
    void given_lessonsCategoryWithTeam_should_serializeCategoryTeams() throws Exception {
      // -- Arrange --
      String exerciseId = seedExercise(tenant);
      String teamId = seedTeam(tenant);
      String categoryId = seedLessonsCategory(exerciseId);
      link("lessons_categories_teams", "lessons_category_id", categoryId, "team_id", teamId);

      // -- Act & Assert --
      mvc.perform(get(EXERCISE_URL + "{exerciseId}/lessons_categories", exerciseId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[0].lessons_category_teams[*]", hasItem(teamId)));
    }
  }

  private String seedExercise(String tenantId) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO exercises (exercise_id, exercise_name, exercise_mail_from, tenant_id)"
            + " VALUES (?, ?, 'noreply@openaev.io', ?)",
        id,
        "team-serialization-" + id,
        tenantId);
    seededRows.add(new String[] {"exercises", "exercise_id", id});
    return id;
  }

  private String seedScenario(String tenantId) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO scenarios (scenario_id, scenario_name, scenario_mail_from, tenant_id)"
            + " VALUES (?, ?, 'noreply@openaev.io', ?)",
        id,
        "team-serialization-" + id,
        tenantId);
    seededRows.add(new String[] {"scenarios", "scenario_id", id});
    return id;
  }

  private String seedTeam(String tenantId) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO teams (team_id, team_name, team_contextual, team_created_at,"
            + " team_updated_at, tenant_id) VALUES (?, ?, false, now(), now(), ?)",
        id,
        "team-serialization-" + id,
        tenantId);
    seededRows.add(new String[] {"teams", "team_id", id});
    return id;
  }

  private String seedLessonsCategory(String exerciseId) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO lessons_categories (lessons_category_id, lessons_category_name,"
            + " lessons_category_order, lessons_category_exercise) VALUES (?, ?, 0, ?)",
        id,
        "team-serialization-" + id,
        exerciseId);
    seededRows.add(new String[] {"lessons_categories", "lessons_category_id", id});
    return id;
  }

  private void link(
      String table, String leftColumn, String leftId, String rightColumn, String rightId) {
    jdbc.update(
        "INSERT INTO " + table + " (" + leftColumn + ", " + rightColumn + ") VALUES (?, ?)",
        leftId,
        rightId);
    seededRows.add(new String[] {table, leftColumn, leftId});
  }
}
