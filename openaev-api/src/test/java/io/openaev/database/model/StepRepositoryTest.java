package io.openaev.database.model;

import io.openaev.IntegrationTest;
import io.openaev.database.repository.StepRepository;
import io.openaev.utils.fixtures.StepFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.fixtures.composers.StepComposer;
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

  @Test
  void testFindAllByStatus() {
    Workflow workflow = WorkflowFixture.getDefaultWorkflowTemplate();
    stepComposer
        .forStep(StepFixture.getDefaultStepTemplate())
        .withWorkflow(workflow)
        .persist()
        .get();

    List<Step> steps = stepRepository.findAllByStatus(STEP_STATUS.TEMPLATE);
    Assertions.assertFalse(steps.isEmpty());
    Assertions.assertEquals(STEP_STATUS.TEMPLATE, steps.get(0).getStatus());
  }

  @Test
  void testFindAllByStepTemplateIdIsNullAndWorkflowId() {
    // GIVEN
    Workflow workflow = WorkflowFixture.getDefaultWorkflowTemplate();
    Step step = StepFixture.getDefaultStepTemplate();

    stepComposer.forStep(step).withWorkflow(workflow).persist();

    // WHEN
    List<Step> steps = stepRepository.findAllByStepTemplateIdIsNullAndWorkflowId(workflow.getId());

    // THEN
    Assertions.assertFalse(steps.isEmpty(), "Step list should not be empty");
    Assertions.assertNull(steps.get(0).getStepTemplate(), "Step template should be null");
    Assertions.assertEquals(workflow.getId(), steps.get(0).getWorkflow().getId());
  }

  @Test
  void testFindStepIdByInjectId() {
    // GIVEN: a step with JSON data containing an inject_id
    String injectId = "inject-123";
    Workflow workflow = WorkflowFixture.getDefaultWorkflowTemplate();
    Step step =
        Step.builder()
            .stepAction(STEP_ACTION_CLASS.INJECT_EXECUTION)
            .status(STEP_STATUS.TEMPLATE)
            .data("{\"inject_id\": \"" + injectId + "\"}")
            .build();

    stepComposer.forStep(step).withWorkflow(workflow).persist();

    // WHEN
    var optionalStepId = stepRepository.findStepIdByInjectId(injectId);

    // THEN
    Assertions.assertTrue(optionalStepId.isPresent(), "Step ID should be found");
    Assertions.assertEquals(step.getId(), optionalStepId.get());
  }
}
