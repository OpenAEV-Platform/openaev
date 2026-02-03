package io.openaev.database.model;

import io.openaev.IntegrationTest;
import io.openaev.database.repository.StepRepository;
import io.openaev.utils.fixtures.StepFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.fixtures.composers.StepComposer;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
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
}
