package io.openaev.service.chaining;

import static org.junit.jupiter.api.Assertions.*;

import io.openaev.database.model.*;
import io.openaev.database.repository.StepRepository;
import io.openaev.database.repository.WorkflowRepository;
import io.openaev.database.repository.WorkflowScopeRuleRepository;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.StepFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.StepComposer;
import io.openaev.utils.fixtures.composers.WorkflowComposer;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Functional contract of duplicating a chained simulation (ADR-007 §6): <b>copy what was authored,
 * drop what was run</b>. The source is deliberately a simulation that has already executed, because
 * the safety guarantee is not the source status but the fact that the cloner reads only from the
 * TEMPLATE workflow.
 */
@SpringBootTest
@Transactional
@DisplayName("Chained simulation duplication")
class ChainedSimulationDuplicationIntegrationTest {

  @Autowired private WorkflowService workflowService;
  @Autowired private StepService stepService;
  @Autowired private ExerciseService exerciseService;
  @Autowired private WorkflowRepository workflowRepository;
  @Autowired private StepRepository stepRepository;
  @Autowired private WorkflowScopeRuleRepository workflowScopeRuleRepository;
  @Autowired private WorkflowComposer workflowComposer;
  @Autowired private ExerciseComposer simulationComposer;
  @Autowired private StepComposer stepComposer;

  /**
   * A simulation that has already run: a TEMPLATE workflow with one authored step, plus a RUN
   * workflow carrying a runtime step.
   */
  private Exercise launchedChainedSimulation() {
    Exercise simulation =
        simulationComposer.forExercise(ExerciseFixture.createDefaultExercise()).persist().get();

    Step authoredStep = StepFixture.getDefaultStepTemplate();
    Workflow template =
        workflowComposer
            .forWorkflow(WorkflowFixture.getDefaultWorkflowTemplate())
            .withSimulation(simulationComposer.forExercise(simulation))
            .withStep(stepComposer.forStep(authoredStep))
            .persist()
            .get();
    template.setKeepAlive(true);
    template.setTimeoutEnabled(false);
    template.setEdited(true);
    template.setVersion(7);
    workflowRepository.save(template);

    Workflow run = WorkflowFixture.getDefaultWorkflowExecution(WorkflowStatus.RUN);
    run.setSimulation(simulation);
    run.setWorkflowTemplate(template);
    run = workflowRepository.save(run);

    Step runtimeStep = StepFixture.getDefaultStepExecution(StepStatus.END);
    runtimeStep.setWorkflow(run);
    runtimeStep.setStepTemplate(authoredStep);
    stepRepository.save(runtimeStep);

    simulation.setStatus(ExerciseStatus.FINISHED);
    return simulation;
  }

  @Nested
  @DisplayName("Given a simulation that has already run")
  class GivenASimulationThatHasAlreadyRun {

    @Test
    @DisplayName("Should copy the authored logic map and no execution artefact")
    void should_copy_the_authored_logic_map_and_no_execution_artefact() {
      // -- ARRANGE --
      Exercise source = launchedChainedSimulation();
      Exercise duplicate = exerciseService.getDuplicateExercise(source.getId());

      // -- ACT --
      Workflow copy = workflowService.duplicateSimulationWorkflow(source.getId(), duplicate);

      // -- ASSERT --
      assertNotNull(copy);
      // Exactly one workflow on the duplicate, and it is a TEMPLATE: no RUN is ever cloned.
      List<Workflow> duplicateWorkflows =
          workflowRepository.findAllBySimulation_Id(duplicate.getId());
      assertEquals(1, duplicateWorkflows.size());
      assertEquals(WorkflowStatus.TEMPLATE, copy.getStatus());

      // The authored step is copied; the runtime step is not.
      List<Step> copiedSteps = stepService.findAllStepTemplateByWorkflow(copy.getId());
      assertEquals(1, copiedSteps.size());
      assertEquals(StepStatus.TEMPLATE, copiedSteps.getFirst().getStatus());
      assertNull(copiedSteps.getFirst().getStepTemplate());
    }

    @Test
    @DisplayName("Should reset the copy to a brand-new, never-run workflow")
    void should_reset_the_copy_to_a_brand_new_never_run_workflow() {
      // -- ARRANGE --
      Exercise source = launchedChainedSimulation();
      Exercise duplicate = exerciseService.getDuplicateExercise(source.getId());

      // -- ACT --
      Workflow copy = workflowService.duplicateSimulationWorkflow(source.getId(), duplicate);

      // -- ASSERT --
      assertEquals(0, copy.getVersion(), "version is reset, not inherited");
      assertFalse(copy.isEdited());
      assertNull(copy.getWorkflowTemplate());
      assertTrue(copy.getWorkflowsExecuted().isEmpty());
      // A duplicate must never inherit the autonomous "park forever" contract.
      assertFalse(copy.isKeepAlive());
      assertTrue(copy.isTimeoutEnabled());
      assertEquals(ExerciseStatus.SCHEDULED, duplicate.getStatus());
      assertTrue(duplicate.getStart().isEmpty());
      assertTrue(duplicate.getEnd().isEmpty());
    }

    @Test
    @DisplayName("Should leave the source object strictly untouched")
    void should_leave_the_source_object_strictly_untouched() {
      // -- ARRANGE --
      Exercise source = launchedChainedSimulation();
      long workflowsBefore = workflowRepository.findAllBySimulation_Id(source.getId()).size();
      Exercise duplicate = exerciseService.getDuplicateExercise(source.getId());

      // -- ACT --
      workflowService.duplicateSimulationWorkflow(source.getId(), duplicate);

      // -- ASSERT --
      assertEquals(
          workflowsBefore, workflowRepository.findAllBySimulation_Id(source.getId()).size());
      Workflow sourceTemplate =
          workflowService.findWorkflowTemplateBySimulationId(source.getId()).orElseThrow();
      assertEquals(1, stepService.findAllStepTemplateByWorkflow(sourceTemplate.getId()).size());
      assertTrue(sourceTemplate.isKeepAlive(), "the source keeps its own configuration");
    }

    @Test
    @DisplayName("Should not clone the runtime-generated injects")
    void should_not_clone_the_runtime_generated_injects() {
      // -- ARRANGE --
      Exercise source = launchedChainedSimulation();

      // -- ACT --
      Exercise duplicate = exerciseService.getDuplicateExercise(source.getId());

      // -- ASSERT --
      // A chained simulation's injects are created at runtime by the chaining engine, so they are
      // execution artefacts and never authored content.
      assertTrue(duplicate.getInjects().isEmpty());
    }

    @Test
    @DisplayName("Should copy the scope rules, which stay valid in the same tenant")
    void should_copy_the_scope_rules_which_stay_valid_in_the_same_tenant() {
      // -- ARRANGE --
      Exercise source = launchedChainedSimulation();
      Workflow sourceTemplate =
          workflowRepository.findAllBySimulation_Id(source.getId()).stream()
              .filter(workflow -> WorkflowStatus.TEMPLATE.equals(workflow.getStatus()))
              .findFirst()
              .orElseThrow();
      WorkflowScopeRule rule = new WorkflowScopeRule();
      rule.setWorkflow(sourceTemplate);
      rule.setRuleValue("10.10.10.10");
      rule.setRuleSource(ScopeRuleSource.MANUAL);
      rule.setSelectedMode(ScopeRuleSelectedMode.ALLOWLIST);
      rule.setValueType(ScopeRuleValueType.IP);
      workflowScopeRuleRepository.save(rule);

      Exercise duplicate = exerciseService.getDuplicateExercise(source.getId());

      // -- ACT --
      Workflow copy = workflowService.duplicateSimulationWorkflow(source.getId(), duplicate);

      // -- ASSERT --
      List<WorkflowScopeRule> copiedRules =
          workflowScopeRuleRepository.findAllByWorkflowId(copy.getId());
      assertEquals(1, copiedRules.size());
      assertEquals("10.10.10.10", copiedRules.getFirst().getRuleValue());
      assertNotEquals(rule.getId(), copiedRules.getFirst().getId());
    }
  }
}
