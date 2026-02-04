package io.openaev.database.model;

import static org.junit.jupiter.api.Assertions.*;

import io.openaev.IntegrationTest;
import io.openaev.database.repository.WorkflowRepository;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
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
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private WorkflowRepository workflowRepository;

  @Test
  void whenFindAllBySimulationId_thenReturnsWorkflowsLinkedToSimulation() {
    Workflow workflow = WorkflowFixture.getDefaultWorkflowTemplate();
    Workflow savedWorkflow =
        workflowComposer
            .forWorkflow(workflow)
            .withSimulation(exerciseComposer.forExercise(ExerciseFixture.createDefaultExercise()))
            .persist()
            .get();

    String simulationId = savedWorkflow.getSimulation().getId();
    List<Workflow> workflows = workflowRepository.findAllBySimulation_Id(simulationId);
    assertFalse(workflows.isEmpty());
    assertEquals(simulationId, workflows.get(0).getSimulation().getId());
  }

  @Test
  void whenFindBySimulationIdAndStatus_thenReturnsMatchingWorkflow() {
    Workflow workflow = WorkflowFixture.getDefaultWorkflowExecution(WorkflowStatus.RUN);
    Workflow savedWorkflow =
        workflowComposer
            .forWorkflow(workflow)
            .withWorkflowTemplate(
                workflowComposer
                    .forWorkflow(WorkflowFixture.getDefaultWorkflowTemplate())
                    .withSimulation(
                        exerciseComposer.forExercise(ExerciseFixture.createDefaultExercise())))
            .withSimulation(exerciseComposer.forExercise(ExerciseFixture.createDefaultExercise()))
            .persist()
            .get();
    String simulationId = savedWorkflow.getSimulation().getId();

    Workflow found =
        workflowRepository.findBySimulation_IdAndStatus(simulationId, WorkflowStatus.RUN);
    assertNotNull(found);
    assertEquals(WorkflowStatus.RUN, found.getStatus());
    assertEquals(simulationId, found.getSimulation().getId());
  }
}
