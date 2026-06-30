package io.openaev.service.chaining;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.openaev.database.model.Step;
import io.openaev.database.model.Workflow;
import io.openaev.database.repository.StepDelayQueueRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StepDelayQueueServiceTest {

  @Mock private StepDelayQueueRepository stepDelayQueueRepository;

  @InjectMocks private StepDelayQueueService stepDelayQueueService;

  @Test
  void pushStepTemplateIntoStepDelayQueue_shouldSaveEntity() {
    Step stepTemplate = mock(Step.class);
    Workflow workflowRun = mock(Workflow.class);
    Instant now = Instant.now();
    Instant goal = now.plusMillis(5000);

    stepDelayQueueService.pushStepTemplateIntoStepDelayQueue(
        stepTemplate, now, "input", 5000L, workflowRun, goal);

    verify(stepDelayQueueRepository)
        .save(
            argThat(
                entry ->
                    entry.getInput().equals("input")
                        && entry.getDelay().equals(5000L)
                        && entry.getNow().equals(now)
                        && entry.getGoal().equals(goal)
                        && entry.getStepTemplate().equals(stepTemplate)
                        && entry.getWorkflowRun().equals(workflowRun)));
  }
}
