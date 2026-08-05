package io.openaev.service.chaining;

import static org.junit.jupiter.api.Assertions.*;

import io.openaev.database.model.*;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.database.repository.StepRepository;
import io.openaev.database.repository.WorkflowRepository;
import io.openaev.rest.exception.ChainingException;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.exception.WorkflowNotEditableException;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.StepFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.fixtures.composers.StepComposer;
import io.openaev.utils.fixtures.composers.WorkflowComposer;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class StepServiceScenarioIntegrationTest {

  @Autowired private WorkflowRepository workflowRepository;
  @Autowired private ScenarioRepository scenarioRepository;
  @Autowired private StepRepository stepRepository;
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private WorkflowComposer workflowComposer;
  @Autowired private ExerciseComposer simulationComposer;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private StepComposer stepComposer;

  private Exercise savedSimulation;
  @Autowired private WorkflowService workflowService;
  @Autowired private StepService stepService;

  @BeforeEach
  void beforeEach() {
    savedSimulation =
        simulationComposer.forExercise(ExerciseFixture.createDefaultExercise()).persist().get();
  }

  // -------------------------------------------------------------------------
  // Workflow starts correctly from a scenario
  // -------------------------------------------------------------------------
  @Test
  void should_start_workflow_from_scenario_successfully() throws ChainingException {
    // PREPARE
    Workflow workflowTemplate =
        workflowComposer
            .forWorkflow(WorkflowFixture.getDefaultWorkflowTemplate())
            .withScenario(scenarioComposer.forScenario(ScenarioFixture.getScenario()))
            .persist()
            .get();

    long workflowCountBefore = workflowRepository.count();

    // ACT & ASSERT
    workflowService.startWorkflowByScenarioIdAndSimulation(
        workflowTemplate.getScenario().getId(), savedSimulation);

    long workflowCountAfter = workflowRepository.count();

    assertEquals(workflowCountBefore + 2, workflowCountAfter);
    List<Workflow> workflows = workflowRepository.findAll();
    Workflow newWorkflowTemplate = findNewWorkflowTemplate(workflows, workflowTemplate.getId());
    Workflow workflowRun = findEndedWorkflowRun(workflows, newWorkflowTemplate.getId());

    assertEquals(savedSimulation.getId(), workflowRun.getSimulation().getId());
    assertEquals(WorkflowStatus.END, workflowRun.getStatus());
  }

  // -------------------------------------------------------------------------
  // Template not found → ElementNotFoundException
  // -------------------------------------------------------------------------
  @Test
  void should_throw_when_workflow_template_not_found_for_scenario() {
    // PREPARE
    String unknownScenarioId = "scenario-id-inexistant";
    // ACT & ASSERT
    ElementNotFoundException exception =
        assertThrows(
            ElementNotFoundException.class,
            () ->
                workflowService.startWorkflowByScenarioIdAndSimulation(
                    unknownScenarioId, savedSimulation));

    assertTrue(exception.getMessage().contains(unknownScenarioId));
  }

  // -------------------------------------------------------------------------
  // No step template → the RUN workflow immediately transitions to END
  // -------------------------------------------------------------------------
  @Test
  void should_set_workflow_run_to_end_when_no_step_template() throws ChainingException {
    // PREPARE
    Workflow workflowTemplate =
        workflowComposer
            .forWorkflow(WorkflowFixture.getDefaultWorkflowTemplate())
            .withScenario(scenarioComposer.forScenario(ScenarioFixture.getScenario()))
            .persist()
            .get();

    // ACT & ASSERT
    workflowService.startWorkflowByScenarioIdAndSimulation(
        workflowTemplate.getScenario().getId(), savedSimulation);

    List<Workflow> workflows = workflowRepository.findAll();
    Workflow newWorkflowTemplate = findNewWorkflowTemplate(workflows, workflowTemplate.getId());
    Workflow workflowRun = findEndedWorkflowRun(workflows, newWorkflowTemplate.getId());

    assertEquals(WorkflowStatus.END, workflowRun.getStatus());
  }

  // -------------------------------------------------------------------------
  // Workflow template not find on Scenario
  // -------------------------------------------------------------------------
  @Test
  void should_throw_when_findWorkflowTemplateByScenarioId_returns_empty() {
    // PREPARE
    Scenario scenarioWithoutWorkflow = ScenarioFixture.getScenario();
    scenarioWithoutWorkflow = scenarioRepository.save(scenarioWithoutWorkflow);
    String scenarioId = scenarioWithoutWorkflow.getId();

    // ACT & ASSERT
    ElementNotFoundException exception =
        assertThrows(
            ElementNotFoundException.class,
            () ->
                workflowService.startWorkflowByScenarioIdAndSimulation(
                    scenarioId, savedSimulation));

    assertTrue(exception.getMessage().contains("Workflow (TEMPLATE) not found"));
    assertTrue(exception.getMessage().contains(scenarioId));
  }

  // -------------------------------------------------------------------------
  // Logic-map freeze / copy-on-launch isolation (ADR-005)
  // -------------------------------------------------------------------------

  /**
   * Editing a scenario step after launch must not affect the simulation template that was populated
   * by the launch-time copy. This also proves the copy path itself is never blocked by the freeze
   * guard (it populates a fresh, still-SCHEDULED simulation template).
   */
  @Test
  void should_isolateSimulationTemplate_fromLaterScenarioStepEdits() {
    // PREPARE — a scenario workflow template owning a single step template
    Step scenarioStep = StepFixture.getDefaultStepTemplate();
    scenarioStep.setData("{\"v\":\"original\"}");
    Workflow scenarioTemplate =
        workflowComposer
            .forWorkflow(WorkflowFixture.getDefaultWorkflowTemplate())
            .withScenario(scenarioComposer.forScenario(ScenarioFixture.getScenario()))
            .withStep(stepComposer.forStep(scenarioStep))
            .persist()
            .get();

    Workflow simulationTemplateFixture = WorkflowFixture.getDefaultWorkflowTemplate();
    simulationTemplateFixture.setSimulation(savedSimulation);
    Workflow simulationTemplate = workflowRepository.save(simulationTemplateFixture);

    // ACT — launch-time copy of the scenario logic map into the simulation template
    stepService.copyStepTemplate(scenarioTemplate, simulationTemplate);

    List<Step> copiedSteps = stepService.findAllStepTemplateByWorkflow(simulationTemplate.getId());
    assertEquals(1, copiedSteps.size());
    Step copiedStep = copiedSteps.getFirst();
    assertNotEquals(scenarioStep.getId(), copiedStep.getId());
    assertEquals("{\"v\":\"original\"}", copiedStep.getData());

    // ACT — edit the scenario step AFTER the copy
    scenarioStep.setData("{\"v\":\"edited\"}");
    stepRepository.save(scenarioStep);

    // ASSERT — the copied simulation step is untouched (isolation)
    Step reloadedCopiedStep = stepRepository.findById(copiedStep.getId()).orElseThrow();
    assertEquals("{\"v\":\"original\"}", reloadedCopiedStep.getData());
  }

  /**
   * Once the owning simulation is launched (RUNNING), its copied logic map is frozen: deleting a
   * simulation step template is rejected with {@link WorkflowNotEditableException}.
   */
  @Test
  void should_rejectDeletingSimulationStep_onceSimulationLaunched() {
    // PREPARE — copy a scenario step into the simulation template
    Step scenarioStep = StepFixture.getDefaultStepTemplate();
    Workflow scenarioTemplate =
        workflowComposer
            .forWorkflow(WorkflowFixture.getDefaultWorkflowTemplate())
            .withScenario(scenarioComposer.forScenario(ScenarioFixture.getScenario()))
            .withStep(stepComposer.forStep(scenarioStep))
            .persist()
            .get();

    Workflow simulationTemplateFixture = WorkflowFixture.getDefaultWorkflowTemplate();
    simulationTemplateFixture.setSimulation(savedSimulation);
    Workflow simulationTemplate = workflowRepository.save(simulationTemplateFixture);

    stepService.copyStepTemplate(scenarioTemplate, simulationTemplate);
    String copiedStepId =
        stepService.findAllStepTemplateByWorkflow(simulationTemplate.getId()).getFirst().getId();

    // ACT — launch the simulation
    savedSimulation.setStatus(ExerciseStatus.RUNNING);
    exerciseRepository.save(savedSimulation);

    // ASSERT — the frozen logic map rejects the deletion, and the step is preserved
    assertThrows(
        WorkflowNotEditableException.class, () -> stepService.deleteStepTemplate(copiedStepId));
    assertTrue(stepRepository.findById(copiedStepId).isPresent());
  }

  private Workflow findNewWorkflowTemplate(List<Workflow> workflows, String sourceTemplateId) {
    return workflows.stream()
        .filter(workflow -> WorkflowStatus.TEMPLATE.equals(workflow.getStatus()))
        .filter(workflow -> !workflow.getId().equals(sourceTemplateId))
        .findFirst()
        .orElseThrow(() -> new AssertionError("New Workflow TEMPLATE not found"));
  }

  private Workflow findEndedWorkflowRun(List<Workflow> workflows, String templateId) {
    return workflows.stream()
        .filter(workflow -> WorkflowStatus.END.equals(workflow.getStatus()))
        .filter(
            workflow ->
                templateId.equals(
                    workflow.getWorkflowTemplate() != null
                        ? workflow.getWorkflowTemplate().getId()
                        : null))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Workflow END not found"));
  }
}
