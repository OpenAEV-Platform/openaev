package io.openaev.database.model;

import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import io.openaev.IntegrationTest;
import io.openaev.database.repository.StepRepository;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.StepFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.StepComposer;
import io.openaev.utils.fixtures.composers.WorkflowComposer;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@TestInstance(PER_CLASS)
@Transactional
class StepRepositoryTest extends IntegrationTest {

  @Autowired private StepRepository stepRepository;
  @Autowired private StepComposer stepComposer;
  @Autowired private WorkflowComposer workflowComposer;
  @Autowired private ExerciseComposer simulationComposer;

  @Test
  void whenFindAllByStatus_thenReturnsStepsWithGivenStatus() {
    workflowComposer
        .forWorkflow(WorkflowFixture.getDefaultWorkflowTemplate())
        .withSimulation(simulationComposer.forExercise(ExerciseFixture.createDefaultExercise()))
        .withStep(stepComposer.forStep(StepFixture.getDefaultStepTemplate()))
        .persist()
        .get();

    List<Step> steps = stepRepository.findAllByStatus(StepStatus.TEMPLATE);
    Assertions.assertFalse(steps.isEmpty());
    Assertions.assertEquals(StepStatus.TEMPLATE, steps.get(0).getStatus());
  }

  @Test
  void whenFindAllByStepTemplateIdIsNullAndWorkflowId_thenReturnsStepsTemplateForWorkflow() {
    // GIVEN
    Workflow workflow =
        workflowComposer
            .forWorkflow(WorkflowFixture.getDefaultWorkflowTemplate())
            .withSimulation(simulationComposer.forExercise(ExerciseFixture.createDefaultExercise()))
            .withStep(stepComposer.forStep(StepFixture.getDefaultStepExecution(StepStatus.RUN)))
            .persist()
            .get();

    // WHEN
    List<Step> steps = stepRepository.findAllByStepTemplateIdIsNullAndWorkflowId(workflow.getId());

    // THEN
    Assertions.assertFalse(steps.isEmpty(), "Step list should not be empty");
    Assertions.assertNull(steps.get(0).getStepTemplate(), "Step template should be null");
    Assertions.assertEquals(workflow.getId(), steps.get(0).getWorkflow().getId());
  }

  @Test
  void whenFindStepIdByInjectId_thenReturnsCorrectStepId() {
    // GIVEN: a step with JSON data containing an inject_id
    String injectId = "inject-123";
    Step step =
        Step.builder()
            .stepAction(StepActionClass.INJECT_EXECUTION)
            .status(StepStatus.TEMPLATE)
            .data("{\"inject_id\": \"" + injectId + "\"}")
            .build();

    workflowComposer
        .forWorkflow(WorkflowFixture.getDefaultWorkflowTemplate())
        .withSimulation(simulationComposer.forExercise(ExerciseFixture.createDefaultExercise()))
        .withStep(stepComposer.forStep(step))
        .persist();

    // WHEN
    var optionalStepId = stepRepository.findStepIdByInjectId(injectId);

    // THEN
    Assertions.assertTrue(optionalStepId.isPresent(), "Step ID should be found");
    Assertions.assertEquals(step.getId(), optionalStepId.get());
  }
}
