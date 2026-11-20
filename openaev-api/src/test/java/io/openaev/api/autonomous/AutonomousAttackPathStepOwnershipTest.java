package io.openaev.api.autonomous;

import static io.openaev.api.autonomous.AutonomousRunApi.AUTONOMOUS_URI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.database.model.Tenant;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.StepFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.fixtures.composers.StepComposer;
import io.openaev.utils.fixtures.composers.WorkflowComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * The attack-path step endpoints of a run edit and delete only the steps of that run's own
 * workflow: the simulation template workflow of a live run, the scenario template workflow of a run
 * that authors a scenario. A step of any other workflow answers 404 and is left as it was.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Autonomous run attack-path step endpoints act on the run's own steps only")
class AutonomousAttackPathStepOwnershipTest extends IntegrationTest {

  @DynamicPropertySource
  static void activeTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  private static final String STEP_URI = AUTONOMOUS_URI + "/{runId}/attack-path/steps/{stepId}";
  private static final String UPDATED_TITLE = "updated-attack-path-step";
  private static final String UNTOUCHED_DATA = "{}";

  @Autowired private MockMvc mvc;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private WorkflowComposer workflowComposer;
  @Autowired private StepComposer stepComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private InjectorFixture injectorFixture;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  // The endpoints are EE-gated; the mock's license checks default to "active" (Mockito false).
  @MockitoBean private EnterpriseEditionService enterpriseEditionService;

  private String contractId;

  enum StepCall {
    UPDATE,
    DELETE
  }

  @BeforeEach
  void seedContractAndJoinTheDefaultTenant() {
    // The run lives in the default tenant: the caller must be a member to resolve its scope.
    tenantHelper.attachCurrentUserToTenant(Tenant.DEFAULT_TENANT_UUID);
    exerciseComposer.reset();
    scenarioComposer.reset();
    workflowComposer.reset();
    stepComposer.reset();
    injectorContractComposer.reset();
    contractId =
        injectorContractComposer
            .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
            .withInjector(injectorFixture.getWellKnownOaevImplantInjector())
            .persist()
            .get()
            .getId();
  }

  @Nested
  @DisplayName("Run on a live simulation")
  class LiveRun {

    private String runId;
    private String stepOfTheRun;
    private String stepOfAnotherSimulation;

    @BeforeEach
    void seedRunOnSimulationAndAnotherSimulation() {
      // Arrange
      String simulationId = persistSimulationWorkflowWithStep();
      stepOfTheRun = lastStepId;
      persistSimulationWorkflowWithStep();
      stepOfAnotherSimulation = lastStepId;
      runId = seedRun("autonomous_run_simulation_id", simulationId);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(StepCall.class)
    @DisplayName("given_stepOfAnotherSimulation_should_answerNotFoundAndKeepTheStep")
    void given_stepOfAnotherSimulation_should_answerNotFoundAndKeepTheStep(StepCall call)
        throws Exception {
      // Act
      mvc.perform(request(call, runId, stepOfAnotherSimulation)).andExpect(status().isNotFound());

      // Assert
      assertThat(stepData(stepOfAnotherSimulation)).isEqualTo(UNTOUCHED_DATA);
    }

    @Test
    @DisplayName("given_stepOfTheRun_should_updateIt")
    void given_stepOfTheRun_should_updateIt() throws Exception {
      // Act
      mvc.perform(request(StepCall.UPDATE, runId, stepOfTheRun)).andExpect(status().isOk());

      // Assert
      assertThat(stepData(stepOfTheRun)).contains(UPDATED_TITLE);
    }

    @Test
    @DisplayName("given_stepOfTheRun_should_deleteIt")
    void given_stepOfTheRun_should_deleteIt() throws Exception {
      // Act
      mvc.perform(request(StepCall.DELETE, runId, stepOfTheRun)).andExpect(status().isOk());

      // Assert
      assertThat(stepData(stepOfTheRun)).isNull();
    }
  }

  @Nested
  @DisplayName("Run authoring a scenario")
  class ScenarioRun {

    private String runId;
    private String stepOfAnotherScenario;

    @BeforeEach
    void seedRunOnScenarioAndAnotherScenario() {
      // Arrange
      String scenarioId = persistScenarioWorkflowWithStep();
      persistScenarioWorkflowWithStep();
      stepOfAnotherScenario = lastStepId;
      runId = seedRun("autonomous_run_scenario_id", scenarioId);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(StepCall.class)
    @DisplayName("given_stepOfAnotherScenario_should_answerNotFoundAndKeepTheStep")
    void given_stepOfAnotherScenario_should_answerNotFoundAndKeepTheStep(StepCall call)
        throws Exception {
      // Act
      mvc.perform(request(call, runId, stepOfAnotherScenario)).andExpect(status().isNotFound());

      // Assert
      assertThat(stepData(stepOfAnotherScenario)).isEqualTo(UNTOUCHED_DATA);
    }
  }

  // -- helpers --

  private String lastStepId;

  private String persistSimulationWorkflowWithStep() {
    StepComposer.Composer step = stepComposer.forStep(StepFixture.getDefaultStepTemplate());
    WorkflowComposer.Composer workflow =
        workflowComposer
            .forWorkflow(WorkflowFixture.getDefaultWorkflowTemplate())
            .withSimulation(
                exerciseComposer.forExercise(ExerciseFixture.createRunningAttackExercise()))
            .withStep(step)
            .persist();
    lastStepId = step.get().getId();
    return workflow.get().getSimulation().getId();
  }

  private String persistScenarioWorkflowWithStep() {
    StepComposer.Composer step = stepComposer.forStep(StepFixture.getDefaultStepTemplate());
    WorkflowComposer.Composer workflow =
        workflowComposer
            .forWorkflow(WorkflowFixture.getDefaultWorkflowTemplate())
            .withScenario(scenarioComposer.forScenario(ScenarioFixture.getScenario()))
            .withStep(step)
            .persist();
    lastStepId = step.get().getId();
    return workflow.get().getScenario().getId();
  }

  // No fixture builds an autonomous run: seeded natively in the default tenant, like its siblings.
  private String seedRun(String ownerColumn, String ownerId) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO autonomous_runs (autonomous_run_id, tenant_id, autonomous_run_objective,"
                + " autonomous_run_status, "
                + ownerColumn
                + ") VALUES (:id, :tenant, 'Own the domain', 'RUNNING', :owner)")
        .setParameter("id", id)
        .setParameter("tenant", Tenant.DEFAULT_TENANT_UUID)
        .setParameter("owner", ownerId)
        .executeUpdate();
    entityManager.flush();
    entityManager.clear();
    return id;
  }

  private MockHttpServletRequestBuilder request(StepCall call, String runId, String stepId) {
    if (call == StepCall.DELETE) {
      return delete(STEP_URI, runId, stepId).with(csrf());
    }
    return put(STEP_URI, runId, stepId)
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            "{\"inject\":{\"inject_title\":\""
                + UPDATED_TITLE
                + "\",\"inject_injector_contract\":\""
                + contractId
                + "\"}}")
        .with(csrf());
  }

  // Read over plain JDBC on the test's own connection: sees the uncommitted rows whatever the
  // request scope left on the transaction. Null when the step no longer exists.
  private String stepData(String stepId) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement statement =
                  connection.prepareStatement(
                      "SELECT step_data::text FROM steps WHERE step_id = ?")) {
                statement.setString(1, stepId);
                try (ResultSet rows = statement.executeQuery()) {
                  return rows.next() ? rows.getString(1) : null;
                }
              }
            });
  }
}
