package io.openaev.service.chaining;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.database.model.Step;
import io.openaev.database.model.StepDelayQueue;
import io.openaev.database.model.Workflow;
import io.openaev.database.repository.StepDelayQueueRepository;
import java.time.Instant;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
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

  @Nested
  class Reschedule {

    @Captor private ArgumentCaptor<StepDelayQueue> delayQueueCaptor;

    @Test
    void shouldPushTemplateStepIntoDelayQueueWithCorrectParameters() {
      // -------- Prepare --------
      Workflow workflowRun = mock(Workflow.class);
      when(workflowRun.getId()).thenReturn("wf-1");

      Step template = mock(Step.class);
      when(template.getId()).thenReturn("template-1");

      Step step = mock(Step.class);
      when(step.getId()).thenReturn("step-1");
      when(step.getInput()).thenReturn("{\"key\":\"value\"}");
      when(step.getWorkflow()).thenReturn(workflowRun);
      when(step.getStepTemplate()).thenReturn(template);

      Instant before = Instant.now().minusSeconds(5);

      // -------- Act --------
      stepDelayQueueService.reschedule(step, 120L);

      // -------- Assert --------
      Instant after = Instant.now().plusSeconds(125);

      verify(stepDelayQueueRepository).save(delayQueueCaptor.capture());
      StepDelayQueue captured = delayQueueCaptor.getValue();

      assertEquals("{\"key\":\"value\"}", captured.getInput());
      assertEquals(120_000L, captured.getDelay());
      assertSame(template, captured.getStepTemplate());
      assertSame(workflowRun, captured.getWorkflowRun());
      assertTrue(captured.getGoal().isAfter(before));
      assertTrue(captured.getGoal().isBefore(after));
    }

    @Test
    void shouldNotEnqueueWhenNoParentTemplate() {
      // -------- Prepare --------
      Step step = mock(Step.class);
      when(step.getId()).thenReturn("step-1");
      when(step.getStepTemplate()).thenReturn(null);

      // -------- Act --------
      stepDelayQueueService.reschedule(step, 120L);

      // -------- Assert --------
      verify(stepDelayQueueRepository, org.mockito.Mockito.never())
          .save(org.mockito.ArgumentMatchers.any());
    }
  }
}
