package io.openaev.service.chaining;

import static org.junit.jupiter.api.Assertions.*;

import io.openaev.database.model.*;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.database.repository.WorkflowRepository;
import io.openaev.rest.exception.ChainingException;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
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
  @Autowired private WorkflowComposer workflowComposer;
  @Autowired private ExerciseComposer simulationComposer;
  @Autowired private ScenarioComposer scenarioComposer;

  private Exercise savedSimulation;
  @Autowired private WorkflowService workflowService;

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

  private Workflow findNewWorkflowTemplate(List<Workflow> workflows, String sourceTemplateId) {
    return workflows.stream()
        .filter(workflow -> WorkflowStatus.TEMPLATE.equals(workflow.getStatus()))
        .filter(workflow -> !workflow.getId().equals(sourceTemplateId))
        .findFirst()
        .orElseThrow(() -> new AssertionError("New Workflow TEMPLATE not find"));
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
        .orElseThrow(() -> new AssertionError("Workflow END not find"));
  }
}
