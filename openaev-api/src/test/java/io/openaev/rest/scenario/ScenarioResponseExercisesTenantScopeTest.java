package io.openaev.rest.scenario;

import static io.openaev.rest.scenario.ScenarioApi.TENANT_SCENARIO_URI;
import static io.openaev.utils.fixtures.ExerciseFixture.createDefaultExercise;
import static io.openaev.utils.fixtures.ScenarioFixture.createDefaultCrisisScenario;
import static io.openaev.utils.fixtures.TeamFixture.createTeamWithName;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Team;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.database.repository.TeamRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The scenario team-player endpoints answer the raw {@code Scenario}, whose lazy {@code
 * scenario_exercises} is serialized through {@code MultiIdSetSerializer} open-in-view, after the
 * controller transaction has committed. The tenant scope is transaction-local, so a load at that
 * point runs with no scope. The collection must be initialized inside the scoped transaction, with
 * the other collections of the response.
 *
 * <p>The class is not {@code @Transactional}: seeding and cleanup run in committed transactions
 * scoped to the test tenant, so the requests read the rows the way production does.
 */
@WithMockUser(isAdmin = true)
@DisplayName("Scenario response with its simulations")
class ScenarioResponseExercisesTenantScopeTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private ScenarioRepository scenarioRepository;
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private TeamRepository teamRepository;

  private String tenant;
  private final List<String> seededScenarios = new ArrayList<>();
  private final List<String> seededExercises = new ArrayList<>();
  private final List<String> seededTeams = new ArrayList<>();

  @BeforeEach
  void setUp() throws Exception {
    seededScenarios.clear();
    seededExercises.clear();
    seededTeams.clear();
    tenant = tenantHelper.createTenantWithCurrentUser("scenario-response-exercises").getId();
  }

  @AfterEach
  void cleanup() {
    inTenantScope(
        () -> {
          exerciseRepository.deleteAllById(seededExercises);
          scenarioRepository.deleteAllById(seededScenarios);
          teamRepository.deleteAllById(seededTeams);
          tenantHelper.deleteCommittedTenants(tenant);
          return null;
        });
  }

  @Nested
  @DisplayName("Players of a scenario team")
  class ScenarioTeamPlayers {

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"add", "remove"})
    @DisplayName(
        "given a scenario with two simulations when players are changed then the response lists"
            + " both simulations")
    void given_a_scenario_with_two_simulations_should_list_them_when_players_are_changed(
        String action) throws Exception {
      // -- Arrange --
      SeededScenario seeded = seedScenarioWithTwoSimulations();

      // -- Act & Assert --
      mvc.perform(
              put(
                      TENANT_SCENARIO_URI + "/{scenarioId}/teams/{teamId}/players/" + action,
                      tenant,
                      seeded.scenarioId(),
                      seeded.teamId())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"scenario_team_players\":[]}")
                  .with(csrf()))
          .andExpect(status().isOk())
          .andExpect(
              jsonPath("$.scenario_exercises", containsInAnyOrder(seeded.exerciseIds().toArray())));
    }
  }

  @Nested
  @DisplayName("Hydration of a scenario response")
  class HydrateForResponse {

    @Test
    @DisplayName(
        "given a scenario with two simulations when it is hydrated in scope then its simulations"
            + " are loaded after the transaction")
    void given_a_scenario_with_two_simulations_should_load_them_in_the_scoped_transaction() {
      // -- Arrange --
      SeededScenario seeded = seedScenarioWithTwoSimulations();

      // -- Act --
      Scenario scenario =
          inTenantScope(
              () ->
                  ScenarioResponseHydrator.hydrateForResponse(
                      scenarioRepository.findById(seeded.scenarioId()).orElseThrow()));

      // -- Assert --
      // The transaction is over: a collection lazy at this point is loaded unscoped by the
      // serializer.
      assertThat(Hibernate.isInitialized(scenario.getExercises())).isTrue();
      assertThat(scenario.getExercises())
          .extracting(Exercise::getId)
          .containsAll(seeded.exerciseIds());
    }
  }

  // -- SEEDING --

  private record SeededScenario(String scenarioId, List<String> exerciseIds, String teamId) {}

  private SeededScenario seedScenarioWithTwoSimulations() {
    return inTenantScope(
        () -> {
          Team team = createTeamWithName("scenario-response-team-" + UUID.randomUUID());
          team.setTenant(new Tenant(tenant));
          String teamId = teamRepository.save(team).getId();
          seededTeams.add(teamId);

          Scenario scenario = createDefaultCrisisScenario();
          scenario.setTenant(new Tenant(tenant));
          scenario.setTeams(new ArrayList<>(List.of(team)));
          Scenario savedScenario = scenarioRepository.save(scenario);
          seededScenarios.add(savedScenario.getId());

          List<String> exerciseIds = new ArrayList<>();
          for (int i = 0; i < 2; i++) {
            Exercise exercise = createDefaultExercise();
            exercise.setTenant(new Tenant(tenant));
            exercise.setScenario(savedScenario);
            String exerciseId = exerciseRepository.save(exercise).getId();
            seededExercises.add(exerciseId);
            exerciseIds.add(exerciseId);
          }
          return new SeededScenario(savedScenario.getId(), exerciseIds, teamId);
        });
  }

  /**
   * Runs the work in a committed transaction scoped to the test tenant. The native queries read the
   * v1 thread-local tenant in their predicates, so it is set too.
   */
  private <T> T inTenantScope(Supplier<T> work) {
    TenantContext.setCurrentTenant(tenant);
    try {
      return tenantTx.execute(TxCtx.forTenant(tenant), work);
    } finally {
      TenantContext.clearCurrentTenant();
    }
  }
}
