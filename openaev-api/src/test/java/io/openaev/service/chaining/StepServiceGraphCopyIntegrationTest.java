package io.openaev.service.chaining;

import static io.openaev.database.model.ConditionType.DEPEND_ON;
import static org.junit.jupiter.api.Assertions.*;

import io.openaev.database.model.*;
import io.openaev.database.repository.WorkflowRepository;
import io.openaev.rest.exception.ChainingException;
import io.openaev.utils.fixtures.ConditionFixture;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.StepFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.fixtures.composers.ConditionComposer;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.fixtures.composers.StepComposer;
import io.openaev.utils.fixtures.composers.WorkflowComposer;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Graph-copy correctness of {@link StepService#copyStepTemplate(Workflow, Workflow)}, the single
 * cloning primitive shared by launch, autonomous plan provisioning, convert-to-manual and
 * duplication (ADR-007).
 */
@SpringBootTest
@Transactional
@DisplayName("StepService - workflow graph copy")
class StepServiceGraphCopyIntegrationTest {

  @Autowired private StepService stepService;
  @Autowired private ConditionService conditionService;
  @Autowired private WorkflowService workflowService;
  @Autowired private WorkflowRepository workflowRepository;
  @Autowired private WorkflowComposer workflowComposer;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private ExerciseComposer simulationComposer;
  @Autowired private StepComposer stepComposer;
  @Autowired private ConditionComposer conditionComposer;

  private Exercise simulation;

  @BeforeEach
  void beforeEach() {
    simulation =
        simulationComposer.forExercise(ExerciseFixture.createDefaultExercise()).persist().get();
  }

  private Workflow persistSimulationTemplate() {
    Workflow template = WorkflowFixture.getDefaultWorkflowTemplate();
    template.setSimulation(simulation);
    return workflowRepository.save(template);
  }

  @Nested
  @DisplayName("DEPEND_ON remapping")
  class DependOnRemapping {

    @Test
    @DisplayName(
        "Given a step depending on another, should point the copy at the copied prerequisite")
    void given_a_step_depending_on_another_should_point_the_copy_at_the_copied_prerequisite() {
      // -- ARRANGE --
      // A prerequisite step and a dependent step whose DEPEND_ON condition stores the
      // prerequisite's step TEMPLATE id in condition_value (not in a foreign key).
      Step prerequisite = StepFixture.getDefaultStepTemplate();
      Step dependent = StepFixture.getDefaultStepTemplate();
      Workflow scenarioTemplate =
          workflowComposer
              .forWorkflow(WorkflowFixture.getDefaultWorkflowTemplate())
              .withScenario(scenarioComposer.forScenario(ScenarioFixture.getScenario()))
              .withStep(stepComposer.forStep(prerequisite))
              .withStep(stepComposer.forStep(dependent))
              .persist()
              .get();

      conditionComposer
          .forCondition(
              ConditionFixture.getDependOnCondition(prerequisite.getId(), scenarioTemplate.getId()))
          .withStep(stepComposer.forStep(dependent))
          .persist();

      Workflow simulationTemplate = persistSimulationTemplate();

      // -- ACT --
      stepService.copyStepTemplate(scenarioTemplate, simulationTemplate);

      // -- ASSERT --
      List<Step> copiedSteps =
          stepService.findAllStepTemplateByWorkflow(simulationTemplate.getId());
      assertEquals(2, copiedSteps.size());

      Condition copiedDependOn =
          conditionService
              .findAllNonMapperConditionsByWorkflowId(simulationTemplate.getId())
              .stream()
              .filter(condition -> DEPEND_ON.equals(condition.getType()))
              .findFirst()
              .orElseThrow(() -> new AssertionError("DEPEND_ON condition was not copied"));

      // The value must be a step template id OF THE DESTINATION workflow. Keeping the source id
      // would make ConditionService.evaluateDependOnConditions' existsByStepTemplateIdAndWorkflowId
      // lookup impossible to satisfy: the dependent step would never be promoted to READY and the
      // branch would stay silently blocked forever.
      assertNotEquals(prerequisite.getId(), copiedDependOn.getValue());
      assertTrue(
          copiedSteps.stream().anyMatch(step -> step.getId().equals(copiedDependOn.getValue())),
          "DEPEND_ON must reference a step template of the destination workflow");
    }

    @Test
    @DisplayName("Given a launched scenario, should keep the dependency resolvable in the run")
    void given_a_launched_scenario_should_keep_the_dependency_resolvable_in_the_run()
        throws ChainingException {
      // -- ARRANGE --
      Step prerequisite = StepFixture.getDefaultStepTemplate();
      Step dependent = StepFixture.getDefaultStepTemplate();
      Workflow scenarioTemplate =
          workflowComposer
              .forWorkflow(WorkflowFixture.getDefaultWorkflowTemplate())
              .withScenario(scenarioComposer.forScenario(ScenarioFixture.getScenario()))
              .withStep(stepComposer.forStep(prerequisite))
              .withStep(stepComposer.forStep(dependent))
              .persist()
              .get();
      conditionComposer
          .forCondition(
              ConditionFixture.getDependOnCondition(prerequisite.getId(), scenarioTemplate.getId()))
          .withStep(stepComposer.forStep(dependent))
          .persist();

      // -- ACT --
      // Launch copies the scenario TEMPLATE into a simulation TEMPLATE, which is what the RUN
      // workflow's run steps carry as their step_template_id.
      workflowService.startWorkflowByScenarioIdAndSimulation(
          scenarioTemplate.getScenario().getId(), simulation);

      // -- ASSERT --
      Workflow simulationTemplate =
          workflowService.findWorkflowTemplateBySimulationId(simulation.getId()).orElseThrow();
      List<String> simulationStepIds =
          stepService.findAllStepTemplateByWorkflow(simulationTemplate.getId()).stream()
              .map(Step::getId)
              .toList();

      Condition dependOn =
          conditionService
              .findAllNonMapperConditionsByWorkflowId(simulationTemplate.getId())
              .stream()
              .filter(condition -> DEPEND_ON.equals(condition.getType()))
              .findFirst()
              .orElseThrow(() -> new AssertionError("DEPEND_ON condition was not copied"));

      assertTrue(
          simulationStepIds.contains(dependOn.getValue()),
          "The launched simulation's DEPEND_ON must reference one of its own step templates");
    }
  }

  @Nested
  @DisplayName("Standalone conditions")
  class StandaloneConditions {

    @Test
    @DisplayName("Given an event not linked to any step, should copy it with the workflow")
    void given_an_event_not_linked_to_any_step_should_copy_it_with_the_workflow() {
      // -- ARRANGE --
      // An event authored in the Logic UI but not yet attached to an action: authored content that
      // the copy used to drop silently.
      Workflow scenarioTemplate =
          workflowComposer
              .forWorkflow(WorkflowFixture.getDefaultWorkflowTemplate())
              .withScenario(scenarioComposer.forScenario(ScenarioFixture.getScenario()))
              .persist()
              .get();

      Condition standalone =
          ConditionFixture.getDefaultCondition(PrimitiveType.Text, "standalone-value");
      standalone.setWorkflowId(scenarioTemplate.getId());
      standalone.setName("Unattached event");
      conditionComposer.forCondition(standalone).persist();

      Workflow simulationTemplate = persistSimulationTemplate();

      // -- ACT --
      stepService.copyStepTemplate(scenarioTemplate, simulationTemplate);

      // -- ASSERT --
      List<Condition> copied =
          conditionService.findAllNonMapperConditionsByWorkflowId(simulationTemplate.getId());
      assertEquals(1, copied.size());
      assertEquals("Unattached event", copied.getFirst().getName());
      assertNotEquals(standalone.getId(), copied.getFirst().getId());
    }
  }

  @Nested
  @DisplayName("Step data re-pointing")
  class StepDataRepointing {

    @Test
    @DisplayName("Given a scenario step, should re-point its owner and drop execution artefacts")
    void given_a_scenario_step_should_repoint_its_owner_and_drop_execution_artefacts() {
      // -- ARRANGE --
      Step scenarioStep = StepFixture.getDefaultStepTemplate();
      Workflow scenarioTemplate =
          workflowComposer
              .forWorkflow(WorkflowFixture.getDefaultWorkflowTemplate())
              .withScenario(scenarioComposer.forScenario(ScenarioFixture.getScenario()))
              .withStep(stepComposer.forStep(scenarioStep))
              .persist()
              .get();
      scenarioStep.setData(
          "{\"inject_scenario\":\""
              + scenarioTemplate.getScenario().getId()
              + "\",\"inject_id\":\"runtime-inject\",\"inject_injector_contract\":\"contract-1\"}");

      Workflow simulationTemplate = persistSimulationTemplate();

      // -- ACT --
      stepService.copyStepTemplate(scenarioTemplate, simulationTemplate);

      // -- ASSERT --
      String copiedData =
          stepService
              .findAllStepTemplateByWorkflow(simulationTemplate.getId())
              .getFirst()
              .getData();

      // Owner follows the destination workflow, and the source owner is cleared so the copy never
      // claims to belong to both objects.
      assertTrue(copiedData.contains("\"inject_exercise\":\"" + simulation.getId() + "\""));
      assertFalse(copiedData.contains("inject_scenario"));
      // Runtime artefacts are execution bleed and must not survive the copy.
      assertFalse(copiedData.contains("inject_id"));
      // Same-tenant shared references stay valid and are kept as-is.
      assertTrue(copiedData.contains("\"inject_injector_contract\":\"contract-1\""));
    }
  }
}
