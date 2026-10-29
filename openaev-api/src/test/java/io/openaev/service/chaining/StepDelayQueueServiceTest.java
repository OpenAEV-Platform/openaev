package io.openaev.service.chaining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.database.model.Step;
import io.openaev.database.model.StepDelayQueue;
import io.openaev.database.model.Workflow;
import io.openaev.database.repository.StepDelayQueueRepository;
import java.time.Instant;
import java.util.List;
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

  // Two pause/resume cycles on one entry: each pause must add up. The entry object plays the
  // persisted row, carried from one cycle to the next, and its goal is nulled on pause as
  // nullifyGoalsByWorkflowRun does.

  @Test
  void recalculateGoalsOnResume_shouldAddUpSuccessivePausesForAnEntryEnqueuedBeforeThem() {
    // Arrange - enqueued at t0 with a 100s delay
    Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
    Workflow workflowRun = new Workflow();
    StepDelayQueue entry =
        StepDelayQueue.builder().now(t0).delay(100_000L).goal(t0.plusSeconds(100)).build();
    when(stepDelayQueueRepository.findAllByWorkflowRun(workflowRun)).thenReturn(List.of(entry));

    // Act - first cycle: paused 10s in (90s left), resumed 50s later
    entry.setGoal(null);
    stepDelayQueueService.recalculateGoalsOnResume(
        workflowRun, t0.plusSeconds(60), t0.plusSeconds(10));

    // Assert
    assertEquals(t0.plusSeconds(150), entry.getGoal());

    // Act - second cycle: paused at t0+70 (80s left), resumed 130s later
    entry.setGoal(null);
    stepDelayQueueService.recalculateGoalsOnResume(
        workflowRun, t0.plusSeconds(200), t0.plusSeconds(70));

    // Assert - both pauses (50s + 130s) push the goal, not only the last one (t0+230)
    assertEquals(t0.plusSeconds(280), entry.getGoal());
  }

  @Test
  void recalculateGoalsOnResume_shouldStartTheDelayAtResumeForAnEntryEnqueuedDuringThePause() {
    // Arrange - paused at t0, entry pushed at t0+20 with a 100s delay (goal null while paused)
    Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
    Workflow workflowRun = new Workflow();
    StepDelayQueue entry =
        StepDelayQueue.builder().now(t0.plusSeconds(20)).delay(100_000L).goal(null).build();
    when(stepDelayQueueRepository.findAllByWorkflowRun(workflowRun)).thenReturn(List.of(entry));

    // Act - first resume at t0+100: the whole delay starts now
    stepDelayQueueService.recalculateGoalsOnResume(workflowRun, t0.plusSeconds(100), t0);

    // Assert
    assertEquals(t0.plusSeconds(200), entry.getGoal());
    assertEquals(t0.plusSeconds(100), entry.getNow());

    // Act - second cycle: paused at t0+150 (50s left), resumed at t0+300
    entry.setGoal(null);
    stepDelayQueueService.recalculateGoalsOnResume(
        workflowRun, t0.plusSeconds(300), t0.plusSeconds(150));

    // Assert - the end of the first pause is not counted as waiting time (would fire at t0+300)
    assertEquals(t0.plusSeconds(350), entry.getGoal());
  }
}
