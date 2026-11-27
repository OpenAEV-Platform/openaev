package io.openaev.rest.team;

import static io.openaev.rest.exercise.ExerciseApi.EXERCISE_URI;
import static io.openaev.rest.inject.InjectApi.INJECT_URI;
import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
import static io.openaev.rest.scenario.ScenarioApi.TENANT_SCENARIO_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.TagLinkJdbc.column;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Team;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.TeamRepository;
import io.openaev.rest.exercise.form.ExerciseUpdateTeamsInput;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.rest.inject.form.InjectBulkUpdateInputs;
import io.openaev.rest.inject.form.InjectBulkUpdateOperation;
import io.openaev.rest.inject.form.InjectBulkUpdateSupportedFields;
import io.openaev.rest.inject.form.InjectBulkUpdateSupportedOperations;
import io.openaev.rest.inject.form.InjectInput;
import io.openaev.rest.scenario.form.ScenarioUpdateTeamsInput;
import io.openaev.service.scenario.ScenarioService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.TeamFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * A simulation, a scenario or an inject links only the teams of its own tenant when an edit names
 * teams of several tenants.
 *
 * <p>The simulation, the scenario and the inject live in the default tenant, the one the ambient
 * thread-local falls back to. On the non-prefixed route {@code X-Tenant-Ids} can name several
 * tenants, and these edits carry no tenant selector, so the request scope holds all of them. The
 * team ids of the body resolve in the tenant of the entity written alone. The links are read
 * through {@code exercises_teams}, {@code scenarios_teams} and {@code injects_teams} on the test
 * connection, which the statement inspector does not rewrite.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("A simulation, a scenario and an inject link only the teams of their own tenant")
class SimulationScenarioInjectTeamLinkTenantTest extends IntegrationTest {

  private static final String TENANT_HEADER = "X-Tenant-Ids";
  private static final String EXERCISE_TEAMS =
      "SELECT team_id FROM exercises_teams WHERE exercise_id = ?";
  private static final String SCENARIO_TEAMS =
      "SELECT team_id FROM scenarios_teams WHERE scenario_id = ?";
  private static final String INJECT_TEAMS =
      "SELECT team_id FROM injects_teams WHERE inject_id = ?";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private ExerciseService exerciseService;
  @Autowired private ScenarioService scenarioService;
  @Autowired private TeamRepository teamRepository;
  @Autowired private InjectorContractFixture injectorContractFixture;

  private String otherTenant;
  private String exercise;
  private String scenario;
  private String teamInOther;
  private String teamInDefault;
  private String injectorContract;

  @BeforeEach
  void seedBothTenants() throws Exception {
    // The caller belongs to both tenants, so the header can name both of them.
    tenantHelper.attachCurrentUserToTenant(Tenant.DEFAULT_TENANT_UUID);
    otherTenant = tenantHelper.createTenantWithCurrentUser("sim-scenario-inject-team-o").getId();

    Exercise createdExercise = new Exercise();
    createdExercise.setName("team-link-exercise");
    createdExercise.setTenant(new Tenant(Tenant.DEFAULT_TENANT_UUID));
    exercise = exerciseService.createExercise(createdExercise).getId();

    Scenario createdScenario = new Scenario();
    createdScenario.setName("team-link-scenario");
    createdScenario.setTenant(new Tenant(Tenant.DEFAULT_TENANT_UUID));
    scenario = scenarioService.createScenario(createdScenario).getId();

    teamInOther = saveTeam(otherTenant);
    teamInDefault = saveTeam(Tenant.DEFAULT_TENANT_UUID);

    TenantContext.clearCurrentTenant();
    injectorContract = injectorContractFixture.getWellKnownSingleEmailContract().getId();
  }

  @AfterEach
  void clearTenant() {
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("Header route naming two tenants")
  class HeaderRouteNamingTwoTenants {

    @Test
    @DisplayName(
        "given a simulation of the default tenant when its teams are replaced with teams of each"
            + " tenant then only the team of the default tenant is linked")
    void given_bodyNamingBothTenants_should_linkOnlyTheTeamOfDefaultTenant_onReplaceExerciseTeams()
        throws Exception {
      // -- Arrange --
      armRequest();
      ExerciseUpdateTeamsInput input = new ExerciseUpdateTeamsInput();
      input.setTeamIds(List.of(teamInOther, teamInDefault));

      // -- Act --
      mvc.perform(
              put(EXERCISE_URI + "/{exerciseId}/teams/replace", exercise)
                  .header(TENANT_HEADER, Tenant.DEFAULT_TENANT_UUID + "," + otherTenant)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input))
                  .with(csrf()))
          .andExpect(status().isOk());

      // -- Assert --
      assertThat(column(entityManager, EXERCISE_TEAMS, exercise))
          .as("teams linked to the simulation: kept %s, dropped %s", teamInDefault, teamInOther)
          .containsExactly(teamInDefault);
    }

    @Test
    @DisplayName(
        "given a scenario of the default tenant when its teams are replaced with teams of each"
            + " tenant then only the team of the default tenant is linked")
    void given_bodyNamingBothTenants_should_linkOnlyTheTeamOfDefaultTenant_onReplaceScenarioTeams()
        throws Exception {
      // -- Arrange --
      armRequest();
      ScenarioUpdateTeamsInput input = new ScenarioUpdateTeamsInput();
      input.setTeamIds(List.of(teamInOther, teamInDefault));

      // -- Act --
      mvc.perform(
              put(SCENARIO_URI + "/{scenarioId}/teams/replace", scenario)
                  .header(TENANT_HEADER, Tenant.DEFAULT_TENANT_UUID + "," + otherTenant)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input))
                  .with(csrf()))
          .andExpect(status().isOk());

      // -- Assert --
      assertThat(column(entityManager, SCENARIO_TEAMS, scenario))
          .as("teams linked to the scenario: kept %s, dropped %s", teamInDefault, teamInOther)
          .containsExactly(teamInDefault);
    }

    @Test
    @DisplayName(
        "given a simulation of the default tenant when an inject is created with teams of each"
            + " tenant then only the team of the default tenant is linked")
    void given_bodyNamingBothTenants_should_linkOnlyTheTeamOfDefaultTenant_onCreateInject()
        throws Exception {
      // -- Arrange --
      armRequest();
      InjectInput input = injectInput(List.of(teamInOther, teamInDefault));

      // -- Act --
      String response =
          mvc.perform(
                  post(EXERCISE_URI + "/{exerciseId}/injects", exercise)
                      .header(TENANT_HEADER, Tenant.DEFAULT_TENANT_UUID + "," + otherTenant)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input))
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- Assert --
      String injectId = JsonPath.read(response, "$.inject_id");
      assertThat(column(entityManager, INJECT_TEAMS, injectId))
          .as("teams linked to the inject: kept %s, dropped %s", teamInDefault, teamInOther)
          .containsExactly(teamInDefault);
    }

    @Test
    @DisplayName(
        "given a simulation inject of the default tenant when it is edited with teams of each"
            + " tenant then only the team of the default tenant is linked")
    void given_bodyNamingBothTenants_should_linkOnlyTheTeamOfDefaultTenant_onUpdateInject()
        throws Exception {
      // -- Arrange --
      armRequest();
      String injectId = createInject(List.of());

      // -- Act --
      mvc.perform(
              put(INJECT_URI + "/{exerciseId}/{injectId}", exercise, injectId)
                  .header(TENANT_HEADER, Tenant.DEFAULT_TENANT_UUID + "," + otherTenant)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(injectInput(List.of(teamInOther, teamInDefault))))
                  .with(csrf()))
          .andExpect(status().isOk());

      // -- Assert --
      assertThat(column(entityManager, INJECT_TEAMS, injectId))
          .as("teams linked to the inject: kept %s, dropped %s", teamInDefault, teamInOther)
          .containsExactly(teamInDefault);
    }

    @Test
    @DisplayName(
        "given a scenario inject of the default tenant when it is bulk-updated with teams of"
            + " each tenant then only the team of the default tenant is linked")
    void given_bodyNamingBothTenants_should_linkOnlyTheTeamOfDefaultTenant_onBulkUpdateInject()
        throws Exception {
      // -- Arrange --
      armRequest();
      String injectId = createScenarioInject(List.of());
      InjectBulkUpdateInputs input = new InjectBulkUpdateInputs();
      input.setInjectIDsToProcess(List.of(injectId));
      InjectBulkUpdateOperation operation = new InjectBulkUpdateOperation();
      operation.setOperation(InjectBulkUpdateSupportedOperations.REPLACE);
      operation.setField(InjectBulkUpdateSupportedFields.TEAMS);
      operation.setValues(List.of(teamInOther, teamInDefault));
      input.setUpdateOperations(List.of(operation));

      // -- Act --
      mvc.perform(
              put(SCENARIO_URI + "/{scenarioId}/injects", scenario)
                  .header(TENANT_HEADER, Tenant.DEFAULT_TENANT_UUID + "," + otherTenant)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input))
                  .with(csrf()))
          .andExpect(status().isOk());

      // -- Assert --
      assertThat(column(entityManager, INJECT_TEAMS, injectId))
          .as("teams linked to the inject: kept %s, dropped %s", teamInDefault, teamInOther)
          .containsExactly(teamInDefault);
    }
  }

  @Nested
  @DisplayName("Prefixed route")
  class PrefixedRoute {

    @Test
    @DisplayName(
        "given a scenario of the default tenant when the body under its path names the team of"
            + " the other tenant then no team is linked")
    void given_bodyNamingTheOtherTenant_should_linkNothing() throws Exception {
      // -- Arrange --
      armRequest();
      ScenarioUpdateTeamsInput input = new ScenarioUpdateTeamsInput();
      input.setTeamIds(List.of(teamInOther));

      // -- Act --
      mvc.perform(
              put(
                      TENANT_SCENARIO_URI + "/{scenarioId}/teams/replace",
                      Tenant.DEFAULT_TENANT_UUID,
                      scenario)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input))
                  .with(csrf()))
          .andExpect(status().isOk());

      // -- Assert --
      assertThat(column(entityManager, SCENARIO_TEAMS, scenario)).isEmpty();
    }

    @Test
    @DisplayName(
        "given a scenario of the default tenant when the body under its path names its own team"
            + " then the team is linked")
    void given_bodyNamingItsOwnTeam_should_linkIt() throws Exception {
      // -- Arrange --
      armRequest();
      ScenarioUpdateTeamsInput input = new ScenarioUpdateTeamsInput();
      input.setTeamIds(List.of(teamInDefault));

      // -- Act --
      mvc.perform(
              put(
                      TENANT_SCENARIO_URI + "/{scenarioId}/teams/replace",
                      Tenant.DEFAULT_TENANT_UUID,
                      scenario)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input))
                  .with(csrf()))
          .andExpect(status().isOk());

      // -- Assert --
      assertThat(column(entityManager, SCENARIO_TEAMS, scenario)).containsExactly(teamInDefault);
    }
  }

  // -- Helpers --

  private InjectInput injectInput(List<String> teamIds) {
    InjectInput input = new InjectInput();
    input.setTitle("team-link-inject");
    input.setInjectorContract(injectorContract);
    input.setDependsDuration(0L);
    input.setTeams(teamIds);
    return input;
  }

  private String createInject(List<String> teamIds) throws Exception {
    String response =
        mvc.perform(
                post(EXERCISE_URI + "/{exerciseId}/injects", exercise)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(injectInput(teamIds)))
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(response, "$.inject_id");
  }

  private String createScenarioInject(List<String> teamIds) throws Exception {
    String response =
        mvc.perform(
                post(SCENARIO_URI + "/{scenarioId}/injects", scenario)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(injectInput(teamIds)))
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(response, "$.inject_id");
  }

  private String saveTeam(String tenantId) {
    Team team = TeamFixture.getEmptyTeam();
    team.setTenant(new Tenant(tenantId));
    return teamRepository.save(team).getId();
  }

  /**
   * Each team carries the tenant the simulation and scenario rely on, then the ambient tenant is
   * reset to the default one the request thread of the header route carries.
   */
  private void armRequest() {
    assertThat(column(entityManager, "SELECT tenant_id FROM teams WHERE team_id = ?", teamInOther))
        .containsExactly(otherTenant);
    assertThat(
            column(
                entityManager, "SELECT tenant_id FROM exercises WHERE exercise_id = ?", exercise))
        .containsExactly(Tenant.DEFAULT_TENANT_UUID);
    assertThat(
            column(
                entityManager, "SELECT tenant_id FROM scenarios WHERE scenario_id = ?", scenario))
        .containsExactly(Tenant.DEFAULT_TENANT_UUID);
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
  }
}
