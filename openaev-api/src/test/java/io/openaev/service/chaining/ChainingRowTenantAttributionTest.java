package io.openaev.service.chaining;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openaev.IntegrationTest;
import io.openaev.api.chaining.dto.ConditionCreateInput;
import io.openaev.api.chaining.dto.EventInput;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Condition;
import io.openaev.database.model.ConditionType;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.PrimitiveType;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.ScopeRuleSelectedMode;
import io.openaev.database.model.ScopeRuleSource;
import io.openaev.database.model.ScopeVariable;
import io.openaev.database.model.Step;
import io.openaev.database.model.StepDelayQueue;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.Workflow;
import io.openaev.database.model.WorkflowScopeRule;
import io.openaev.database.model.WorkflowStatus;
import io.openaev.database.repository.ConditionRepository;
import io.openaev.database.repository.StepDelayQueueRepository;
import io.openaev.database.repository.StepRepository;
import io.openaev.database.repository.TenantRepository;
import io.openaev.database.repository.WorkflowRepository;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.StepFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every chaining row takes its tenant from its parent row, never from the tenant thread-local. The
 * thread-local is set to a third tenant throughout, so neither it nor the default-tenant fallback
 * can pass for the right answer.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Chaining rows: tenant taken from the parent row")
class ChainingRowTenantAttributionTest extends IntegrationTest {

  @Autowired private TenantRepository tenantRepository;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private WorkflowRepository workflowRepository;
  @Autowired private StepRepository stepRepository;
  @Autowired private ConditionRepository conditionRepository;
  @Autowired private StepDelayQueueRepository stepDelayQueueRepository;
  @Autowired private WorkflowService workflowService;
  @Autowired private StepService stepService;
  @Autowired private ConditionService conditionService;
  @Autowired private WorkflowStateStore workflowStateStore;
  @Autowired private StepDelayQueueService stepDelayQueueService;

  private Tenant simulationTenant;
  private Tenant scenarioTenant;
  private Exercise simulation;
  private Scenario scenario;

  @BeforeEach
  void setUp() {
    exerciseComposer.reset();
    scenarioComposer.reset();
    simulationTenant = persistTenant("chaining-simulation");
    scenarioTenant = persistTenant("chaining-scenario");
    Tenant threadTenant = persistTenant("chaining-thread");
    simulation = ExerciseFixture.createDefaultExercise();
    simulation.setTenant(simulationTenant);
    exerciseComposer.forExercise(simulation).persist();
    scenario = ScenarioFixture.getScenario();
    scenario.setTenant(scenarioTenant);
    scenarioComposer.forScenario(scenario).persist();
    TenantContext.setCurrentTenant(threadTenant.getId());
  }

  @AfterEach
  void clearThreadLocal() {
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("Workflow")
  class WorkflowRows {

    @Test
    @DisplayName("A simulation workflow takes the simulation's tenant")
    void given_simulation_should_attributeWorkflowToSimulationTenant() {
      // Act
      workflowService.creationWorkflow(simulation);

      // Assert
      assertThat(tenantOf(templateOf(simulation))).isEqualTo(simulationTenant.getId());
    }

    @Test
    @DisplayName("A scenario workflow takes the scenario's tenant")
    void given_scenario_should_attributeWorkflowToScenarioTenant() {
      // Act
      workflowService.creationWorkflow(scenario);

      // Assert
      Workflow template =
          workflowRepository.findAll().stream()
              .filter(
                  w -> w.getScenario() != null && scenario.getId().equals(w.getScenario().getId()))
              .findFirst()
              .orElseThrow();
      assertThat(tenantOf(template)).isEqualTo(scenarioTenant.getId());
    }

    @Test
    @DisplayName("A run, its scope rules and its scope variables take the simulation's tenant")
    void given_launchedTemplate_should_attributeRunAndItsCopiesToSimulationTenant() {
      // Arrange
      Workflow template = simulationTemplate();
      template
          .getWorkflowScopeRules()
          .add(
              WorkflowScopeRule.builder()
                  .selectedMode(ScopeRuleSelectedMode.ALLOWLIST)
                  .ruleSource(ScopeRuleSource.MANUAL)
                  .ruleValue("10.0.0.1")
                  .workflow(template)
                  .build());
      template
          .getWorkflowScopeVariables()
          .add(
              ScopeVariable.builder()
                  .key("key")
                  .type(PrimitiveType.Text)
                  .value("value")
                  .workflow(template)
                  .build());
      workflowRepository.saveAndFlush(template);

      // Act
      Workflow run = workflowService.launchWorkflowSimulation(template);
      entityManager.flush();

      // Assert
      assertThat(tenantOf(run)).isEqualTo(simulationTenant.getId());
      assertThat(run.getWorkflowScopeRules())
          .isNotEmpty()
          .allSatisfy(
              rule -> assertThat(rule.getTenant().getId()).isEqualTo(simulationTenant.getId()));
      assertThat(run.getWorkflowScopeVariables())
          .isNotEmpty()
          .allSatisfy(v -> assertThat(v.getTenant().getId()).isEqualTo(simulationTenant.getId()));
    }
  }

  @Nested
  @DisplayName("Steps and conditions")
  class StepAndConditionRows {

    @Test
    @DisplayName("A step takes its workflow's tenant")
    void given_stepOfScenarioWorkflow_should_attributeStepToScenarioTenant() {
      // Arrange
      Workflow template = scenarioTemplate();

      // Act
      Step step = persistStep(template);

      // Assert
      assertThat(step.getTenant().getId()).isEqualTo(scenarioTenant.getId());
    }

    @Test
    @DisplayName(
        "A condition takes its tenant from a linked step, from its parent, or from its step")
    void given_conditionsLinkedEachWay_should_attributeThemToTheWorkflowTenant() {
      // Arrange
      Step step = persistStep(scenarioTemplate());
      Condition linked = Condition.builder().type(ConditionType.AND).build();
      conditionService.linkToStep(linked, step, true);
      linked = conditionRepository.save(linked);
      Condition child = Condition.builder().type(ConditionType.EQ).conditionParent(linked).build();
      Condition fromStep = Condition.builder().type(ConditionType.EQ).stepFrom(step).build();

      // Act
      child = conditionRepository.save(child);
      fromStep = conditionRepository.save(fromStep);

      // Assert
      assertThat(List.of(linked, child, fromStep))
          .allSatisfy(c -> assertThat(c.getTenant().getId()).isEqualTo(scenarioTenant.getId()));
    }

    @Test
    @DisplayName("A standalone event takes its workflow's tenant")
    void given_eventLinkedToNoStep_should_attributeItToTheWorkflowTenant() {
      // Arrange
      Workflow template = scenarioTemplate();
      EventInput input =
          EventInput.builder()
              .name("standalone event")
              .workflowId(template.getId())
              .conditions(
                  List.of(
                      ConditionCreateInput.builder()
                          .temporaryId("root")
                          .type(ConditionType.AND)
                          .build(),
                      ConditionCreateInput.builder()
                          .temporaryId("leaf")
                          .temporaryIdConditionParent("root")
                          .type(ConditionType.IS_NOT_NULL)
                          .key("IPv4")
                          .build()))
              .build();

      // Act
      Condition root = conditionService.createConditionTree(input);

      // Assert
      assertThat(root.getTenant().getId()).isEqualTo(scenarioTenant.getId());
      assertThat(root.getConditionChildren())
          .isNotEmpty()
          .allSatisfy(c -> assertThat(c.getTenant().getId()).isEqualTo(scenarioTenant.getId()));
    }

    @Test
    @DisplayName("A copied workflow's steps and conditions take the target workflow's tenant")
    void given_copiedTemplate_should_attributeCopiesToTheTargetTenant() {
      // Arrange
      Workflow source = scenarioTemplate();
      Step sourceStep = persistStep(source);
      Condition sourceRoot =
          Condition.builder().type(ConditionType.AND).workflowId(source.getId()).build();
      conditionService.linkToStep(sourceRoot, sourceStep, true);
      conditionRepository.save(sourceRoot);
      conditionRepository.save(
          Condition.builder()
              .type(ConditionType.AND)
              .workflowId(source.getId())
              .tenant(source.getTenant())
              .build());
      entityManager.flush();
      entityManager.clear();
      Workflow target = simulationTemplate();

      // Act
      stepService.copyStepTemplate(
          workflowRepository.findById(source.getId()).orElseThrow(), target);
      entityManager.flush();

      // Assert
      assertThat(nativeTenants("SELECT tenant_id FROM steps WHERE step_workflow_id = ?", target))
          .containsOnly(simulationTenant.getId())
          .isNotEmpty();
      assertThat(
              nativeTenants(
                  "SELECT tenant_id FROM conditions WHERE condition_workflow_id = ?", target))
          .containsOnly(simulationTenant.getId())
          .hasSize(2);
    }
  }

  @Nested
  @DisplayName("Native inserts")
  class NativeInserts {

    @Test
    @DisplayName("Global and local states take their run's tenant")
    void given_scenarioRun_should_attributeStatesToScenarioTenant() {
      // Arrange
      Workflow run = scenarioRun();
      Step stepTemplate = persistStep(run);

      // Act
      String globalState = workflowStateStore.getOrCreateGlobalStateId(run);
      String localState = workflowStateStore.getOrCreateLocalStateId(stepTemplate, run);

      // Assert
      assertThat(nativeTenant("workflow_states", "workflow_state_id", globalState))
          .isEqualTo(scenarioTenant.getId());
      assertThat(nativeTenant("workflow_states", "workflow_state_id", localState))
          .isEqualTo(scenarioTenant.getId());
    }

    @Test
    @DisplayName("A delayed step takes its run's tenant")
    void given_scenarioRun_should_attributeDelayedStepToScenarioTenant() {
      // Arrange
      Workflow run = scenarioRun();
      Step stepTemplate = persistStep(run);

      // Act
      stepDelayQueueService.pushStepTemplateIntoStepDelayQueue(
          stepTemplate, Instant.now(), "{}", 0L, run, Instant.now());

      // Assert
      List<StepDelayQueue> entries = stepDelayQueueRepository.findAllByWorkflowRun(run);
      assertThat(entries).hasSize(1);
      assertThat(
              nativeTenant("steps_delay_queue", "steps_delay_queue_id", entries.getFirst().getId()))
          .isEqualTo(scenarioTenant.getId());
    }
  }

  @Nested
  @DisplayName("Refusals")
  class Refusals {

    @Test
    @DisplayName("A step with no workflow is refused instead of landing in a default tenant")
    void given_stepWithoutWorkflow_should_refuseTheWrite() {
      // Arrange
      Step orphan = StepFixture.getDefaultStepTemplate();

      // Act + Assert
      assertThatThrownBy(() -> stepRepository.saveAndFlush(orphan))
          .hasStackTraceContaining("unattributed tenant write: Step");
    }

    @Test
    @DisplayName("A condition carrying another tenant than its step's is refused")
    void given_conditionWithForeignTenant_should_refuseTheWrite() {
      // Arrange
      Step step = persistStep(scenarioTemplate());
      Condition foreign =
          Condition.builder().type(ConditionType.AND).tenant(simulationTenant).build();
      conditionService.linkToStep(foreign, step, true);

      // Act + Assert
      assertThatThrownBy(() -> conditionRepository.saveAndFlush(foreign))
          .hasStackTraceContaining("cross-tenant write: Condition");
    }
  }

  // -- helpers --

  private Tenant persistTenant(String name) {
    return tenantRepository.save(
        TenantFixture.getTenant(name + "-" + UUID.randomUUID().toString().substring(0, 8)));
  }

  private Workflow simulationTemplate() {
    Workflow template = WorkflowFixture.getDefaultWorkflowTemplate();
    template.setSimulation(simulation);
    return workflowRepository.saveAndFlush(template);
  }

  private Workflow scenarioTemplate() {
    Workflow template = WorkflowFixture.getDefaultWorkflowTemplate();
    template.setScenario(scenario);
    return workflowRepository.saveAndFlush(template);
  }

  private Workflow scenarioRun() {
    Workflow run = WorkflowFixture.getDefaultWorkflowExecution(WorkflowStatus.RUN);
    run.setScenario(scenario);
    return workflowRepository.saveAndFlush(run);
  }

  private Step persistStep(Workflow workflow) {
    Step step = StepFixture.getDefaultStepTemplate();
    step.setWorkflow(workflow);
    return stepRepository.saveAndFlush(step);
  }

  private Workflow templateOf(Exercise exercise) {
    return workflowRepository.findAll().stream()
        .filter(
            w -> w.getSimulation() != null && exercise.getId().equals(w.getSimulation().getId()))
        .findFirst()
        .orElseThrow();
  }

  private String tenantOf(Workflow workflow) {
    entityManager.flush();
    return nativeTenant("workflows", "workflow_id", workflow.getId());
  }

  private String nativeTenant(String table, String idColumn, String id) {
    return (String)
        entityManager
            .createNativeQuery("SELECT tenant_id FROM " + table + " WHERE " + idColumn + " = ?1")
            .setParameter(1, id)
            .getSingleResult();
  }

  @SuppressWarnings("unchecked")
  private List<String> nativeTenants(String sql, Workflow workflow) {
    return entityManager
        .createNativeQuery(sql.replace("?", "?1"))
        .setParameter(1, workflow.getId())
        .getResultList();
  }
}
