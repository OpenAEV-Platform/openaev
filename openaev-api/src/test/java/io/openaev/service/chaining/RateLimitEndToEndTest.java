package io.openaev.service.chaining;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.openaev.api.chaining.ActionStep;
import io.openaev.database.model.*;
import io.openaev.database.repository.InjectStatusRepository;
import io.openaev.database.repository.StepDelayQueueRepository;
import io.openaev.database.repository.StepRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * End-to-end orchestration test for the rate-limit lifecycle.
 *
 * <p>Wires real {@link StepEventService}, {@link RateLimitGuardService} and {@link
 * StepDelayQueueService} together, mocking only the persistence boundaries (repositories) and the
 * action-step execution layer. This validates the full cycle:
 *
 * <ol>
 *   <li>Rate limit reached → step rescheduled with {@code rateLimitCount} incremented
 *   <li>{@code rateLimitCount} preserved through the delay queue dedicated column
 *   <li>Delay queue consumed → existing READY step re-enqueued with the preserved count
 *   <li>Rate limit now under threshold → step executes; action step receives step with {@code
 *       rateLimitCount}
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Rate Limit — end-to-end lifecycle")
class RateLimitEndToEndTest {

  // -- Boundary mocks --
  @Mock private InjectStatusRepository injectStatusRepository;
  @Mock private StepDelayQueueRepository stepDelayQueueRepository;
  @Mock private StepRepository stepRepository;
  @Mock private StepService stepService;
  @Mock private WorkflowService workflowService;
  @Mock private QueueChainingService queueChainingService;

  // -- Real services wired together --
  private RateLimitGuardService rateLimitGuardService;
  private StepDelayQueueService stepDelayQueueService;
  private StepEventService stepEventService;

  // -- Shared fixtures --
  private Workflow workflowRun;
  private Step stepTemplate;

  @BeforeEach
  void setUp() {
    rateLimitGuardService = new RateLimitGuardService(injectStatusRepository);
    stepDelayQueueService = new StepDelayQueueService(stepDelayQueueRepository);

    // Create a TransactionTemplate mock that simply executes the callback
    TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
    lenient()
        .doAnswer(
            invocation -> {
              Consumer<TransactionStatus> action = invocation.getArgument(0);
              action.accept(null);
              return null;
            })
        .when(transactionTemplate)
        .executeWithoutResult(any());

    stepEventService =
        new StepEventService(
            stepService,
            workflowService,
            stepRepository,
            rateLimitGuardService,
            stepDelayQueueService,
            queueChainingService,
            transactionTemplate);

    // Shared simulation + workflow
    Exercise simulation = new Exercise();
    simulation.setId(UUID.randomUUID().toString());

    workflowRun =
        Workflow.builder()
            .id(UUID.randomUUID().toString())
            .status(WorkflowStatus.RUN)
            .simulation(simulation)
            .rateLimitEnabled(true)
            .maxAttempts(3)
            .maxTemporalRateSeconds(60L)
            .build();

    stepTemplate = new Step();
    stepTemplate.setId(UUID.randomUUID().toString());
    stepTemplate.setStatus(StepStatus.TEMPLATE);
    stepTemplate.setStepAction(StepActionClass.INJECT_EXECUTION);
    stepTemplate.setWorkflow(workflowRun);
  }

  @Test
  @DisplayName(
      "Full cycle: rate limit blocks → reschedule with rateLimitCount"
          + " → delay queue preserves count → re-execution proceeds")
  void fullRateLimitCycle() throws Exception {
    // =====================================================================
    // PHASE 1 — First attempt: rate limit reached, step is rescheduled
    // =====================================================================

    // Build a READY step linked to its TEMPLATE parent
    Step stepReady1 = buildReadyStep(stepTemplate, workflowRun, "{}");

    // Guard stubs
    when(workflowService.isWorkflowEnded(workflowRun.getId())).thenReturn(false);
    // 3 terminal injects already executed → limit of 3 reached
    when(injectStatusRepository.countLaunchedInjectsSince(
            eq(workflowRun.getSimulation().getId()), any(Instant.class)))
        .thenReturn(3L);

    // Act — first run attempt
    stepEventService.run(stepReady1);

    // Assert — step was NOT executed
    verify(stepService, never()).factoryAction(any(), any());

    // Assert — rateLimitCount was incremented to 1
    assertEquals(
        1, stepReady1.getRateLimitCount(), "rateLimitCount should be 1 after first reschedule");

    // Assert — delay queue entry was persisted with the TEMPLATE step (not the READY step)
    ArgumentCaptor<StepDelayQueue> delayCaptor = ArgumentCaptor.forClass(StepDelayQueue.class);
    verify(stepDelayQueueRepository).save(delayCaptor.capture());
    StepDelayQueue capturedEntry = delayCaptor.getValue();

    assertSame(
        stepTemplate,
        capturedEntry.getStepTemplate(),
        "Delay queue must reference the TEMPLATE step, not the READY step");
    assertSame(
        stepReady1,
        capturedEntry.getStepReady(),
        "Delay queue must reference the existing READY step for rate-limit rescheduling");
    assertSame(workflowRun, capturedEntry.getWorkflowRun());
    assertEquals(
        60_000L, capturedEntry.getDelay(), "Delay should be maxTemporalRateSeconds * 1000");
    assertEquals(
        1, capturedEntry.getRateLimitCount(), "Delay queue entry must carry rateLimitCount=1");

    // =====================================================================
    // PHASE 2 — Second attempt: still rate limited → count increments to 2
    // =====================================================================

    // Simulate QueueChainingJob consuming the delay queue: it re-enqueues the
    // existing READY step with the rateLimitCount from the delay queue entry.
    stepReady1.setRateLimitCount(capturedEntry.getRateLimitCount());

    reset(stepDelayQueueRepository);
    // Still at the limit
    when(injectStatusRepository.countLaunchedInjectsSince(anyString(), any(Instant.class)))
        .thenReturn(3L);

    // Act — second run attempt
    stepEventService.run(stepReady1);

    // Assert — count incremented to 2
    assertEquals(
        2, stepReady1.getRateLimitCount(), "rateLimitCount should be 2 after second reschedule");

    // Assert — delay queue was saved again with count 2
    verify(stepDelayQueueRepository).save(delayCaptor.capture());
    StepDelayQueue secondEntry = delayCaptor.getValue();
    assertEquals(
        2, secondEntry.getRateLimitCount(), "Second delay queue entry must carry rateLimitCount=2");

    // =====================================================================
    // PHASE 3 — Third attempt: rate limit now allows execution
    // =====================================================================

    // Simulate QueueChainingJob: set count from delay queue onto the step
    stepReady1.setRateLimitCount(secondEntry.getRateLimitCount());

    // Now only 2 terminal injects → under the limit of 3
    when(injectStatusRepository.countLaunchedInjectsSince(anyString(), any(Instant.class)))
        .thenReturn(2L);

    // Mock the action step execution
    ActionStep actionStep = mock(ActionStep.class);
    Step stepRun = new Step();
    stepRun.setId(UUID.randomUUID().toString());
    stepRun.setStatus(StepStatus.RUN);

    when(stepService.factoryAction(eq(StepActionClass.INJECT_EXECUTION), eq(stepReady1.getId())))
        .thenReturn(actionStep);
    when(actionStep.run(stepReady1)).thenReturn(Optional.of(stepRun));
    when(stepService.saveStep(stepRun)).thenReturn(stepRun);

    // Act — third run attempt, this time it should proceed
    stepEventService.run(stepReady1);

    // Assert — action step was executed
    verify(actionStep).run(stepReady1);
    verify(stepService).saveStep(stepRun);
    assertEquals(StepStatus.RUN, stepRun.getStatus());

    // Assert — the step still carries rateLimitCount=2 for INFO trace emission
    assertEquals(
        2,
        stepReady1.getRateLimitCount(),
        "The executed step should still carry rateLimitCount=2 for INFO trace emission");
  }

  @Test
  @DisplayName("Zero rateLimitCount is handled gracefully through the full reschedule cycle")
  void fullCycle_withZeroCount() {
    // READY step with default count (0)
    Step stepReady = buildReadyStep(stepTemplate, workflowRun, null);

    when(workflowService.isWorkflowEnded(workflowRun.getId())).thenReturn(false);
    when(injectStatusRepository.countLaunchedInjectsSince(anyString(), any(Instant.class)))
        .thenReturn(5L);

    // Act
    stepEventService.run(stepReady);

    // Assert — count incremented from 0 to 1
    assertEquals(
        1, stepReady.getRateLimitCount(), "Count should be 1 after first reschedule from zero");

    // Assert — delay queue preserves the count
    ArgumentCaptor<StepDelayQueue> captor = ArgumentCaptor.forClass(StepDelayQueue.class);
    verify(stepDelayQueueRepository).save(captor.capture());
    assertEquals(
        1,
        captor.getValue().getRateLimitCount(),
        "Delay queue entry should carry rateLimitCount=1");
  }

  // ========================================================================
  // Helpers
  // ========================================================================

  /**
   * Builds a READY step linked to its TEMPLATE parent, simulating what {@code
   * StepService.createReadySteps()} produces.
   */
  private Step buildReadyStep(Step template, Workflow workflow, String input) {
    Step step = new Step();
    step.setId(UUID.randomUUID().toString());
    step.setStatus(StepStatus.READY);
    step.setStepAction(StepActionClass.INJECT_EXECUTION);
    step.setWorkflow(workflow);
    step.setStepTemplate(template);
    step.setInput(input);
    step.setData("{}");
    return step;
  }
}
