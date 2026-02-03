package io.openaev.database.model;

import static org.junit.jupiter.api.Assertions.*;

import io.openaev.IntegrationTest;
import io.openaev.database.repository.WorkflowRepository;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.fixtures.composers.WorkflowComposer;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class WorkflowRepositoryTest extends IntegrationTest {

  @Autowired private WorkflowComposer workflowComposer;
  @Autowired private WorkflowRepository workflowRepository;

  @Test
  void testFindAllBySimulation_Id() {
    Workflow workflow = WorkflowFixture.getDefaultWorkflowTemplate();
    Exercise simulation = ExerciseFixture.createDefaultExercise();
    Workflow savedWorkflow =
        workflowComposer.forWorkflow(workflow).withSimulation(simulation).persist().get();

    String simulationId = savedWorkflow.getSimulation().getId();
    List<Workflow> workflows = workflowRepository.findAllBySimulation_Id(simulationId);
    assertFalse(workflows.isEmpty());
    assertEquals(simulationId, workflows.get(0).getSimulation().getId());
  }

  @Test
  void testFindBySimulation_IdAndStatus() {
    Workflow workflowTemplate = WorkflowFixture.getDefaultWorkflowTemplate();
    Workflow workflow = WorkflowFixture.getDefaultWorkflowExecution(WORKFLOW_STATUS.RUN);
    Exercise simulation = ExerciseFixture.createDefaultExercise();
    Workflow savedWorkflow =
        workflowComposer
            .forWorkflow(workflow)
            .withWorkflowTemplate(workflowTemplate)
            .withSimulation(simulation)
            .persist()
            .get();
    String simulationId = savedWorkflow.getSimulation().getId();

    Workflow found =
        workflowRepository.findBySimulation_IdAndStatus(simulationId, WORKFLOW_STATUS.RUN);
    assertNotNull(found);
    assertEquals(WORKFLOW_STATUS.RUN, found.getStatus());
    assertEquals(simulationId, found.getSimulation().getId());
  }
}
