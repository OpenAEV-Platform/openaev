package io.openaev.service.chaining;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
  void pushStepTemplateIntoStepDelayQueue_shouldUpsertEntity() {
    // Arrange
    Step stepTemplate = mock(Step.class);
    Workflow workflowRun = mock(Workflow.class);
    Instant now = Instant.now();
    Instant goal = now.plusMillis(5000);
    when(stepTemplate.getId()).thenReturn("step-id");
    when(workflowRun.getId()).thenReturn("workflow-id");

    // Act
    stepDelayQueueService.pushStepTemplateIntoStepDelayQueue(
        stepTemplate, now, "input", 5000L, workflowRun, goal);

    // Assert
    verify(stepDelayQueueRepository)
        .upsertByWorkflowRunStepTemplateAndInput(
            eq("input"), eq(now), eq(goal), eq(5000L), eq("step-id"), eq("workflow-id"));
  }
}
