package io.openaev.rest.exercise;

import static io.openaev.rest.exercise.ExerciseApi.EXERCISE_URI;
import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.ExerciseStatus;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.Workflow;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.WorkflowRepository;
import io.openaev.rest.exercise.form.ExerciseUpdateStatusInput;
import io.openaev.utils.ZipUtils;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.fixtures.composers.WorkflowComposer;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * An empty tenant scope reads no workflow. Once the chaining tables are tenant-active, the workflow
 * of a simulation or a scenario is read only inside the caller's scope. On the non-prefixed route,
 * with no tenant selector, that scope is the caller's tenant membership, so a platform admin who
 * belongs to no tenant reads no workflow: the export and the reset of a chained simulation or
 * scenario treat it as not chained, and still succeed.
 *
 * <p>The simulation, the scenario and their injects are not tenant-active yet, so that caller still
 * reads them: the export carries the injects and the reset keeps them. Once {@code exercises} and
 * {@code scenarios} activate, the same caller reads none of them either. Each group also runs the
 * same call as a member of the tenant the rows live in, which reads the workflow: the difference
 * between the two outcomes is the scope alone.
 *
 * <p>The production active tables are armed, so the class follows what production enforces. The
 * prefixed route is not covered here: {@code TenantInterceptor} refuses a caller who is not a
 * member of the tenant in the path.
 */
@TestInstance(PER_CLASS)
@Transactional
@DisplayName("An empty tenant scope reads no workflow")
class EmptyScopeWorkflowReadTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private MockMvc mvc;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private WorkflowComposer workflowComposer;
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private InjectRepository injectRepository;
  @Autowired private WorkflowRepository workflowRepository;
  @Autowired private EntityManager manager;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private DataSource dataSource;

  @BeforeEach
  void before() {
    exerciseComposer.reset();
    scenarioComposer.reset();
    injectComposer.reset();
  }

  /**
   * The request thread of the non-prefixed route carries no ambient tenant, while the fixtures
   * leave one on the test thread: give the test thread the request's state before the call.
   */
  private static void clearAmbientTenant() {
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
  }

  private String exportJson(String uri, String entryName) throws Exception {
    byte[] zip =
        mvc.perform(get(uri).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    return ZipUtils.getZipEntry(zip, "%s.json".formatted(entryName), ZipUtils::streamToString);
  }

  @Nested
  @DisplayName("Simulation export")
  class SimulationExport {

    private Exercise chainedSimulation() {
      ExerciseComposer.Composer simulation =
          exerciseComposer
              .forExercise(ExerciseFixture.createDefaultCrisisExercise())
              .withInject(injectComposer.forInject(InjectFixture.getDefaultInject()));
      workflowComposer
          .forWorkflow(WorkflowFixture.getDefaultWorkflowTemplate())
          .withSimulation(simulation)
          .persist();
      manager.flush();
      manager.clear();
      return simulation.get();
    }

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("given no tenant membership should export the simulation as not chained")
    void given_noTenantMembership_should_exportTheSimulationAsNotChained() throws Exception {
      // Arrange
      Exercise simulation = chainedSimulation();
      clearAmbientTenant();

      // Act
      String json =
          exportJson(EXERCISE_URI + "/" + simulation.getId() + "/export", simulation.getName());

      // Assert
      assertThatJson(json).node("exercise_workflow").isAbsent();
      assertThatJson(json).node("exercise_injects").isArray().hasSize(1);
    }

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName("given a membership of its tenant should export the simulation as chained")
    void given_membershipOfItsTenant_should_exportTheSimulationAsChained() throws Exception {
      // Arrange
      Exercise simulation = chainedSimulation();
      clearAmbientTenant();

      // Act
      String json =
          exportJson(EXERCISE_URI + "/" + simulation.getId() + "/export", simulation.getName());

      // Assert
      assertThatJson(json).node("exercise_workflow").isPresent();
      assertThatJson(json).node("exercise_injects").isArray().isEmpty();
    }
  }

  @Nested
  @DisplayName("Scenario export")
  class ScenarioExport {

    private Scenario chainedScenario() {
      ScenarioComposer.Composer scenario =
          scenarioComposer
              .forScenario(ScenarioFixture.createDefaultCrisisScenario())
              .withInject(injectComposer.forInject(InjectFixture.getDefaultInject()));
      workflowComposer
          .forWorkflow(WorkflowFixture.getDefaultWorkflowTemplate())
          .withScenario(scenario)
          .persist();
      manager.flush();
      manager.clear();
      return scenario.get();
    }

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("given no tenant membership should export the scenario as not chained")
    void given_noTenantMembership_should_exportTheScenarioAsNotChained() throws Exception {
      // Arrange
      Scenario scenario = chainedScenario();
      clearAmbientTenant();

      // Act
      String json =
          exportJson(SCENARIO_URI + "/" + scenario.getId() + "/export", scenario.getName());

      // Assert
      assertThatJson(json).node("scenario_workflow").isAbsent();
      assertThatJson(json).node("scenario_injects").isArray().hasSize(1);
    }

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName("given a membership of its tenant should export the scenario as chained")
    void given_membershipOfItsTenant_should_exportTheScenarioAsChained() throws Exception {
      // Arrange
      Scenario scenario = chainedScenario();
      clearAmbientTenant();

      // Act
      String json =
          exportJson(SCENARIO_URI + "/" + scenario.getId() + "/export", scenario.getName());

      // Assert
      assertThatJson(json).node("scenario_workflow").isPresent();
      assertThatJson(json).node("scenario_injects").isArray().isEmpty();
    }
  }

  /**
   * The reset clears the persistence context, so these tests commit their rows and remove them
   * afterwards, as the reset tests of {@code ExerciseApiStatusTest} do.
   */
  @Nested
  @DisplayName("Simulation reset")
  class SimulationReset {

    private record ChainedSimulation(String simulationId, String injectId, String workflowId) {}

    private ChainedSimulation commitChainedClosedSimulation() {
      return new TransactionTemplate(transactionManager)
          .execute(
              status -> {
                Exercise simulation = ExerciseFixture.createCanceledAttackExercise(Instant.now());
                simulation.setName("reset-empty-scope-" + UUID.randomUUID());
                Exercise savedSimulation = exerciseRepository.save(simulation);

                Workflow template = WorkflowFixture.getDefaultWorkflowTemplate();
                template.setSimulation(savedSimulation);
                Workflow savedTemplate = workflowRepository.save(template);

                Inject inject = InjectFixture.getDefaultInject();
                inject.setExercise(savedSimulation);
                Inject savedInject = injectRepository.save(inject);

                return new ChainedSimulation(
                    savedSimulation.getId(), savedInject.getId(), savedTemplate.getId());
              });
    }

    private void reset(String simulationId) throws Exception {
      ExerciseUpdateStatusInput input = new ExerciseUpdateStatusInput();
      input.setStatus(ExerciseStatus.SCHEDULED);
      mvc.perform(
              put(EXERCISE_URI + "/" + simulationId + "/status")
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());
    }

    // Read outside the tenant inspector: the assertion is about the stored rows, not about what a
    // scope lets the test see.
    private int countRows(String table, String idColumn, String id) {
      return new JdbcTemplate(dataSource)
          .queryForObject(
              "SELECT count(*) FROM " + table + " WHERE " + idColumn + " = ?", Integer.class, id);
    }

    private void remove(ChainedSimulation rows) {
      new JdbcTemplate(dataSource)
          .update("DELETE FROM exercises WHERE exercise_id = ?", rows.simulationId());
    }

    @Test
    @WithMockUser(isAdmin = true)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("given no tenant membership should reset the simulation as not chained")
    void given_noTenantMembership_should_resetTheSimulationAsNotChained() throws Exception {
      // Arrange
      ChainedSimulation rows = commitChainedClosedSimulation();
      try {
        clearAmbientTenant();

        // Act
        reset(rows.simulationId());

        // Assert
        assertThat(exerciseRepository.findById(rows.simulationId()))
            .get()
            .extracting(Exercise::getStatus)
            .isEqualTo(ExerciseStatus.SCHEDULED);
        assertThat(countRows("injects", "inject_id", rows.injectId()))
            .as("a reset that reads no workflow keeps the injects")
            .isEqualTo(1);
        assertThat(countRows("workflows", "workflow_id", rows.workflowId())).isEqualTo(1);
      } finally {
        remove(rows);
      }
    }

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("given a membership of its tenant should reset the simulation as chained")
    void given_membershipOfItsTenant_should_resetTheSimulationAsChained() throws Exception {
      // Arrange
      ChainedSimulation rows = commitChainedClosedSimulation();
      try {
        clearAmbientTenant();

        // Act
        reset(rows.simulationId());

        // Assert
        assertThat(countRows("injects", "inject_id", rows.injectId()))
            .as("a chained reset deletes the injects")
            .isZero();
        assertThat(countRows("workflows", "workflow_id", rows.workflowId())).isEqualTo(1);
      } finally {
        remove(rows);
      }
    }
  }
}
