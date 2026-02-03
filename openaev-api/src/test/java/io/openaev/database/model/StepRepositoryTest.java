package io.openaev.database.model;

import io.openaev.IntegrationTest;
import io.openaev.database.repository.StepRepository;
import io.openaev.utils.fixtures.StepFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.fixtures.composers.StepComposer;
import io.openaev.utils.fixtures.composers.WorkflowComposer;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class StepRepositoryTest extends IntegrationTest {

  @Autowired private StepRepository stepRepository;
  @Autowired private StepComposer stepComposer;
  @Autowired private WorkflowComposer workflowComposer;

  @Test
  void testFindAllByStatus() {
    stepComposer
        .forStep(StepFixture.getDefaultStepTemplate())
        .withWorkflow(workflowComposer.forWorkflow(WorkflowFixture.getDefaultWorkflowTemplate()))
        .persist()
        .get();

    List<Step> steps = stepRepository.findAllByStatus(STEP_STATUS.TEMPLATE);
    Assertions.assertFalse(steps.isEmpty());
    Assertions.assertEquals(STEP_STATUS.TEMPLATE, steps.get(0).getStatus());
  }

  @Test
  void testFindAllByStepTemplateIdIsNullAndWorkflowId() {
    // GIVEN
    Step step =
        stepComposer
            .forStep(StepFixture.getDefaultStepTemplate())
            .withWorkflow(
                workflowComposer.forWorkflow(WorkflowFixture.getDefaultWorkflowTemplate()))
            .persist()
            .get();

    // WHEN
    List<Step> steps =
        stepRepository.findAllByStepTemplateIdIsNullAndWorkflowId(step.getWorkflow().getId());

    // THEN
    Assertions.assertFalse(steps.isEmpty(), "Step list should not be empty");
    Assertions.assertNull(steps.get(0).getStepTemplate(), "Step template should be null");
    Assertions.assertEquals(step.getWorkflow().getId(), steps.get(0).getWorkflow().getId());
  }

  @Test
  void testFindStepIdByInjectId() {
    // GIVEN: a step with JSON data containing an inject_id
    String injectId = "inject-123";
    Step step =
        Step.builder()
            .stepAction(STEP_ACTION_CLASS.INJECT_EXECUTION)
            .status(STEP_STATUS.TEMPLATE)
            .data("{\"inject_id\": \"" + injectId + "\"}")
            .build();

    stepComposer
        .forStep(step)
        .withWorkflow(workflowComposer.forWorkflow(WorkflowFixture.getDefaultWorkflowTemplate()))
        .persist();

    // WHEN
    var optionalStepId = stepRepository.findStepIdByInjectId(injectId);

    // THEN
    Assertions.assertTrue(optionalStepId.isPresent(), "Step ID should be found");
    Assertions.assertEquals(step.getId(), optionalStepId.get());
  }
}
