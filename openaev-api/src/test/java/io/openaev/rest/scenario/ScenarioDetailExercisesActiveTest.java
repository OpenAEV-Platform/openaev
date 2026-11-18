package io.openaev.rest.scenario;

import static io.openaev.rest.scenario.ScenarioApi.TENANT_SCENARIO_URI;
import static io.openaev.utils.fixtures.ExerciseFixture.createDefaultExercise;
import static io.openaev.utils.fixtures.ScenarioFixture.createDefaultCrisisScenario;
import static io.openaev.utils.fixtures.TeamFixture.createTeamWithName;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Team;
import io.openaev.database.model.Tenant;
import io.openaev.database.raw.RawScenario;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.database.repository.TeamRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The scenario detail query aggregates the simulations of a scenario in a common table expression.
 * The statement inspector matches table references by name, so a CTE carrying the name of a
 * tenant-isolated table is rewritten as that table and PostgreSQL refuses the statement. These
 * tests run with {@code exercises} isolated on top of the production list, so a CTE named after it
 * turns the scenario detail and its teams into server errors.
 *
 * <p>The class is not {@code @Transactional}: seeding and cleanup run in committed transactions
 * scoped to the test tenant, so the requests read the rows the way production does.
 */
@WithMockUser(isAdmin = true)
@DisplayName("Scenario detail with exercises isolated")
class ScenarioDetailExercisesActiveTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionActiveTablesWithExercises(DynamicPropertyRegistry registry) {
    Set<String> tables = new TreeSet<>(ProductionActiveTables.names());
    tables.add("exercises");
    registry.add("openaev.tenant.active-tables", () -> String.join(",", tables));
  }

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
    tenant = tenantHelper.createTenantWithCurrentUser("scenario-detail-exercises").getId();
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
  @DisplayName("Read the scenario detail")
  class ReadScenarioDetail {

    @Test
    @DisplayName(
        "given a scenario with a simulation when the detail query runs then it returns the"
            + " simulation")
    void given_scenarioWithSimulation_should_returnSimulationFromDetailQuery() {
      // -- Arrange --
      SeededScenario seeded = seedScenarioWithSimulationAndTeam();

      // -- Act --
      RawScenario raw =
          inTenantScope(() -> scenarioRepository.getScenarioByIdAndTenantId(seeded.scenarioId()));

      // -- Assert --
      assertThat(raw).isNotNull();
      assertThat(raw.getScenario_id()).isEqualTo(seeded.scenarioId());
      assertThat(raw.getScenario_exercises()).containsExactly(seeded.exerciseId());
    }

    @Test
    @DisplayName(
        "given a scenario with a simulation when its detail is read then the simulation is"
            + " returned")
    void given_scenarioWithSimulation_should_returnSimulationInDetail() throws Exception {
      // -- Arrange --
      SeededScenario seeded = seedScenarioWithSimulationAndTeam();

      // -- Act & Assert --
      mvc.perform(
              get(TENANT_SCENARIO_URI + "/{scenarioId}", tenant, seeded.scenarioId())
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.scenario_id").value(seeded.scenarioId()))
          .andExpect(jsonPath("$.scenario_exercises", contains(seeded.exerciseId())));
    }
  }

  @Nested
  @DisplayName("Read the scenario teams")
  class ReadScenarioTeams {

    @Test
    @DisplayName("given a scenario with a team when its teams are read then the team is returned")
    void given_scenarioWithTeam_should_returnTeam() throws Exception {
      // -- Arrange --
      SeededScenario seeded = seedScenarioWithSimulationAndTeam();

      // -- Act & Assert --
      mvc.perform(
              get(TENANT_SCENARIO_URI + "/{scenarioId}/teams", tenant, seeded.scenarioId())
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[*].team_id", contains(seeded.teamId())));
    }
  }

  // -- SEEDING --

  private record SeededScenario(String scenarioId, String exerciseId, String teamId) {}

  private SeededScenario seedScenarioWithSimulationAndTeam() {
    return inTenantScope(
        () -> {
          Team team = createTeamWithName("scenario-detail-team-" + UUID.randomUUID());
          team.setTenant(new Tenant(tenant));
          String teamId = teamRepository.save(team).getId();
          seededTeams.add(teamId);

          Scenario scenario = createDefaultCrisisScenario();
          scenario.setTenant(new Tenant(tenant));
          scenario.setTeams(new ArrayList<>(List.of(team)));
          Scenario savedScenario = scenarioRepository.save(scenario);
          seededScenarios.add(savedScenario.getId());

          Exercise exercise = createDefaultExercise();
          exercise.setTenant(new Tenant(tenant));
          exercise.setScenario(savedScenario);
          String exerciseId = exerciseRepository.save(exercise).getId();
          seededExercises.add(exerciseId);

          return new SeededScenario(savedScenario.getId(), exerciseId, teamId);
        });
  }

  /**
   * Runs the work in a committed transaction scoped to the test tenant. The native queries still
   * read the v1 thread-local tenant in their predicates, so it is set too.
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
