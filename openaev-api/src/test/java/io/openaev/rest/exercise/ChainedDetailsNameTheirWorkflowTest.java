package io.openaev.rest.exercise;

import static io.openaev.rest.exercise.ExerciseApi.EXERCISE_URI;
import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Workflow;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.StepFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.fixtures.composers.StepComposer;
import io.openaev.utils.fixtures.composers.WorkflowComposer;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The detail of a chained simulation or scenario names its workflow. Both details are native
 * queries that aggregate their rows and join {@code workflows}, and the scenario one also joins
 * {@code steps}: with the chaining tables tenant-active, the inspector rewrites those joins, and
 * the statement must still be accepted and still find the workflow of a member's simulation or
 * scenario.
 *
 * <p>The production active tables are armed, so the class follows what production enforces.
 */
@TestInstance(PER_CLASS)
@Transactional
@DisplayName("The detail of a chained simulation or scenario names its workflow")
class ChainedDetailsNameTheirWorkflowTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private MockMvc mvc;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private WorkflowComposer workflowComposer;
  @Autowired private StepComposer stepComposer;
  @Autowired private EntityManager manager;

  @BeforeEach
  void before() {
    exerciseComposer.reset();
    scenarioComposer.reset();
    workflowComposer.reset();
    stepComposer.reset();
  }

  private String detail(String uri) throws Exception {
    TenantContext.clearCurrentTenant();
    return mvc.perform(get(uri).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  @Test
  @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
  @DisplayName("given a chained simulation should name its workflow in its detail")
  void given_chainedSimulation_should_nameItsWorkflowInItsDetail() throws Exception {
    // Arrange
    ExerciseComposer.Composer simulation =
        exerciseComposer.forExercise(ExerciseFixture.createDefaultCrisisExercise());
    Workflow template =
        workflowComposer
            .forWorkflow(WorkflowFixture.getDefaultWorkflowTemplate())
            .withStep(stepComposer.forStep(StepFixture.getDefaultStepTemplate()))
            .withSimulation(simulation)
            .persist()
            .get();
    Exercise saved = simulation.get();
    manager.flush();
    manager.clear();

    // Act
    String json = detail(EXERCISE_URI + "/" + saved.getId());

    // Assert
    assertThatJson(json).node("exercise_workflow_id").isEqualTo(template.getId());
  }

  @Test
  @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
  @DisplayName("given a chained scenario should name its workflow in its detail")
  void given_chainedScenario_should_nameItsWorkflowInItsDetail() throws Exception {
    // Arrange
    ScenarioComposer.Composer scenario =
        scenarioComposer.forScenario(ScenarioFixture.createDefaultCrisisScenario());
    Workflow template =
        workflowComposer
            .forWorkflow(WorkflowFixture.getDefaultWorkflowTemplate())
            .withStep(stepComposer.forStep(StepFixture.getDefaultStepTemplate()))
            .withScenario(scenario)
            .persist()
            .get();
    Scenario saved = scenario.get();
    manager.flush();
    manager.clear();

    // Act
    String json = detail(SCENARIO_URI + "/" + saved.getId());

    // Assert
    assertThatJson(json).node("scenario_workflow_id").isEqualTo(template.getId());
  }
}
