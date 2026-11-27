package io.openaev.rest.lessons;

import static io.openaev.rest.exercise.ExerciseApi.TENANT_EXERCISE_URI;
import static io.openaev.rest.lessons.ExerciseLessonsApi.EXERCISE_URL;
import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
import static io.openaev.rest.scenario.ScenarioApi.TENANT_SCENARIO_URI;
import static io.openaev.utils.TagLinkJdbc.column;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.LessonsCategory;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Team;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.LessonsCategoryRepository;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.database.repository.TeamRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.LessonsCategoryFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.TeamFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * The teams of a lessons category are the teams of the category's own tenant, the tenant of its
 * simulation or scenario.
 *
 * <p>On the non-prefixed route {@code X-Tenant-Ids} can name several tenants. The team ids of the
 * body resolve in the tenant of the simulation or scenario alone. The links are read from {@code
 * lessons_categories_teams} through JDBC on the test connection, which the statement inspector does
 * not rewrite.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Lessons category links only the teams of its simulation or scenario tenant")
class LessonsCategoryTeamLinkTenantTest extends IntegrationTest {

  private static final String TENANT_HEADER = "X-Tenant-Ids";
  private static final String CATEGORY_TEAMS =
      "SELECT team_id FROM lessons_categories_teams WHERE lessons_category_id = ?";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private ScenarioRepository scenarioRepository;
  @Autowired private LessonsCategoryRepository lessonsCategoryRepository;
  @Autowired private TeamRepository teamRepository;
  @Autowired private ObjectMapper objectMapper;

  private String tenantB;
  private String teamInDefault;
  private String teamInB;

  @BeforeEach
  void seedBothTenants() throws Exception {
    // The caller belongs to both tenants, so the header can name both of them.
    tenantHelper.attachCurrentUserToTenant(Tenant.DEFAULT_TENANT_UUID);
    tenantB = tenantHelper.createTenantWithCurrentUser("lessons-team-link-b").getId();
    teamInDefault = saveTeam(Tenant.DEFAULT_TENANT_UUID);
    teamInB = saveTeam(tenantB);
  }

  @AfterEach
  void clearTenant() {
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("Lessons category of a simulation")
  class OfASimulation {

    @Test
    @DisplayName(
        "given a category of a simulation of the default tenant when the body on the header route"
            + " names a team of each tenant then only the team of the default tenant is linked")
    void given_categoryOfSimulation_should_linkOnlyTheTeamOfItsTenant() throws Exception {
      // -- Arrange --
      String exercise = saveSimulation();
      String category = saveCategory(exerciseRepository.findById(exercise).orElseThrow(), null);
      armRequest();

      // -- Act --
      mvc.perform(
              withBody(
                  put(EXERCISE_URL + exercise + "/lessons_categories/" + category + "/teams")
                      .header(TENANT_HEADER, Tenant.DEFAULT_TENANT_UUID + "," + tenantB),
                  teamInDefault,
                  teamInB))
          .andExpect(status().isOk());

      // -- Assert --
      assertThat(column(entityManager, CATEGORY_TEAMS, category))
          .as("teams linked to the category: kept %s, dropped %s", teamInDefault, teamInB)
          .containsExactly(teamInDefault);
    }

    @Test
    @DisplayName(
        "given a category of a simulation of the default tenant when the body under its path names"
            + " its team then the team is linked")
    void given_categoryOfSimulation_should_linkTheTeamOnThePrefixedRoute() throws Exception {
      // -- Arrange --
      String exercise = saveSimulation();
      String category = saveCategory(exerciseRepository.findById(exercise).orElseThrow(), null);
      armRequest();

      // -- Act --
      mvc.perform(
              withBody(
                  put(
                      TENANT_EXERCISE_URI + "/{exerciseId}/lessons_categories/{categoryId}/teams",
                      Tenant.DEFAULT_TENANT_UUID,
                      exercise,
                      category),
                  teamInDefault))
          .andExpect(status().isOk());

      // -- Assert --
      assertThat(column(entityManager, CATEGORY_TEAMS, category)).containsExactly(teamInDefault);
    }
  }

  @Nested
  @DisplayName("Lessons category of a scenario")
  class OfAScenario {

    @Test
    @DisplayName(
        "given a category of a scenario of the default tenant when the body on the header route"
            + " names a team of each tenant then only the team of the default tenant is linked")
    void given_categoryOfScenario_should_linkOnlyTheTeamOfItsTenant() throws Exception {
      // -- Arrange --
      String scenario = saveScenario();
      String category = saveCategory(null, scenarioRepository.findById(scenario).orElseThrow());
      armRequest();

      // -- Act --
      mvc.perform(
              withBody(
                  put(SCENARIO_URI + "/" + scenario + "/lessons_categories/" + category + "/teams")
                      .header(TENANT_HEADER, Tenant.DEFAULT_TENANT_UUID + "," + tenantB),
                  teamInDefault,
                  teamInB))
          .andExpect(status().isOk());

      // -- Assert --
      assertThat(column(entityManager, CATEGORY_TEAMS, category))
          .as("teams linked to the category: kept %s, dropped %s", teamInDefault, teamInB)
          .containsExactly(teamInDefault);
    }

    @Test
    @DisplayName(
        "given a category of a scenario of the default tenant when the body under its path names"
            + " its team then the team is linked")
    void given_categoryOfScenario_should_linkTheTeamOnThePrefixedRoute() throws Exception {
      // -- Arrange --
      String scenario = saveScenario();
      String category = saveCategory(null, scenarioRepository.findById(scenario).orElseThrow());
      armRequest();

      // -- Act --
      mvc.perform(
              withBody(
                  put(
                      TENANT_SCENARIO_URI + "/{scenarioId}/lessons_categories/{categoryId}/teams",
                      Tenant.DEFAULT_TENANT_UUID,
                      scenario,
                      category),
                  teamInDefault))
          .andExpect(status().isOk());

      // -- Assert --
      assertThat(column(entityManager, CATEGORY_TEAMS, category)).containsExactly(teamInDefault);
    }
  }

  // -- Helpers --

  private MockHttpServletRequestBuilder withBody(
      MockHttpServletRequestBuilder request, String... teams) throws Exception {
    return request
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(Map.of("lessons_category_teams", List.of(teams))))
        .with(csrf());
  }

  private String saveTeam(String tenantId) {
    Team team = TeamFixture.createTeamWithName("lessons-team-link-" + UUID.randomUUID());
    team.setTenant(new Tenant(tenantId));
    return teamRepository.save(team).getId();
  }

  private String saveSimulation() {
    Exercise exercise = ExerciseFixture.createDefaultExercise();
    exercise.setTenant(new Tenant(Tenant.DEFAULT_TENANT_UUID));
    return exerciseRepository.save(exercise).getId();
  }

  private String saveScenario() {
    Scenario scenario = ScenarioFixture.getScenario();
    scenario.setTenant(new Tenant(Tenant.DEFAULT_TENANT_UUID));
    return scenarioRepository.save(scenario).getId();
  }

  private String saveCategory(Exercise exercise, Scenario scenario) {
    LessonsCategory category = LessonsCategoryFixture.createDefaultLessonsCategory();
    category.setExercise(exercise);
    category.setScenario(scenario);
    String id = lessonsCategoryRepository.save(category).getId();
    entityManager.flush();
    entityManager.clear();
    return id;
  }

  /**
   * Each team carries the tenant the scenario relies on, then the ambient tenant is reset to the
   * default one the request thread of the header route carries.
   */
  private void armRequest() {
    assertThat(column(entityManager, "SELECT tenant_id FROM teams WHERE team_id = ?", teamInB))
        .containsExactly(tenantB);
    assertThat(
            column(entityManager, "SELECT tenant_id FROM teams WHERE team_id = ?", teamInDefault))
        .containsExactly(Tenant.DEFAULT_TENANT_UUID);
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
  }
}
