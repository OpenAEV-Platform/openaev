package io.openaev.service.chaining;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;

import io.openaev.database.model.*;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.database.repository.WorkflowRepository;
import io.openaev.rest.document.DocumentService;
import io.openaev.rest.exception.ChainingException;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.tag.TagService;
import io.openaev.service.AssetService;
import io.openaev.service.TeamService;
import io.openaev.service.UserService;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.fixtures.composers.WorkflowComposer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class StepServiceScenarioIntegrationTest {

  @Autowired private StepService stepService;
  @Autowired private WorkflowRepository workflowRepository;
  @Autowired private ScenarioRepository scenarioRepository;
  @Autowired private WorkflowComposer workflowComposer;
  @Autowired private ExerciseComposer simulationComposer;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private InjectRepository injectRepository;

  @MockBean private UserService userService;
  @MockBean private TeamService teamService;
  @MockBean private AssetService assetService;
  @MockBean private TagService tagService;
  @MockBean private DocumentService documentService;
  @MockBean private InjectService injectService;
  @MockBean private io.openaev.executors.Executor executor;

  private Exercise savedSimulation;
  @Autowired private WorkflowService workflowService;

  @BeforeEach
  void beforeEach() throws Exception {
    // PREPARE
    savedSimulation =
        simulationComposer.forExercise(ExerciseFixture.createDefaultExercise()).persist().get();

    // MOCKS
    doReturn(new User()).when(userService).currentUser();
    doReturn(new ArrayList<>()).when(teamService).getTeamsByIds(any());
    doReturn(new ArrayList<>()).when(assetService).assets(any());
    doReturn(new HashSet<>()).when(tagService).tagSet(any());
    doReturn(null).when(documentService).document(any());
    doReturn(false).when(injectService).canApplyTargetType(any(), any());
    doReturn(new InjectStatus()).when(executor).directExecute(any());

    doAnswer(
            invocation -> {
              Inject inject = invocation.getArgument(0);
              return injectRepository.save(inject);
            })
        .when(injectService)
        .createInject(any(Inject.class));
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
    Workflow newWorkflowTemplate =
        workflows.stream()
            .filter(
                workflow ->
                    workflow.getStatus().equals(WorkflowStatus.TEMPLATE)
                        && !workflow.getId().equals(workflowTemplate.getId()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("New Workflow TEMPLATE not find"));

    Workflow workflowRun =
        workflows.stream()
            .filter(w -> WorkflowStatus.END.equals(w.getStatus()))
            .filter(
                w ->
                    newWorkflowTemplate
                        .getId()
                        .equals(
                            w.getWorkflowTemplate() != null
                                ? w.getWorkflowTemplate().getId()
                                : null))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Workflow END not find"));

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

    Workflow newWorkflowTemplate =
        workflows.stream()
            .filter(
                workflow ->
                    workflow.getStatus().equals(WorkflowStatus.TEMPLATE)
                        && !workflow.getId().equals(workflowTemplate.getId()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("New Workflow TEMPLATE not find"));

    Workflow workflowRun =
        workflowRepository.findAll().stream()
            .filter(w -> WorkflowStatus.END.equals(w.getStatus()))
            .filter(
                w ->
                    newWorkflowTemplate
                        .getId()
                        .equals(
                            w.getWorkflowTemplate() != null
                                ? w.getWorkflowTemplate().getId()
                                : null))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Workflow END not find"));

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
}
