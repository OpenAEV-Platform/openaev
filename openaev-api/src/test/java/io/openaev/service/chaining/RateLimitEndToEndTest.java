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
 *   <li>Rate limit reached → step rescheduled with {@code _rateLimitCount} incremented
 *   <li>{@code _rateLimitCount} preserved through the delay queue (TEMPLATE step + input)
 *   <li>Delay queue consumed → new READY step created with the preserved input
 *   <li>Rate limit now under threshold → step executes; action step receives input containing
 *       {@code _rateLimitCount}
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
      "Full cycle: rate limit blocks → reschedule with _rateLimitCount"
          + " → delay queue preserves data → re-execution proceeds with count in input")
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

    // Assert — input was enriched with _rateLimitCount:1
    assertTrue(
        stepReady1.getInput().contains("\"_rateLimitCount\":1"),
        "Input should contain _rateLimitCount:1 after first reschedule, got: "
            + stepReady1.getInput());

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
    assertTrue(
        capturedEntry.getInput().contains("\"_rateLimitCount\":1"),
        "Delay queue input must carry _rateLimitCount:1, got: " + capturedEntry.getInput());

    // =====================================================================
    // PHASE 2 — Second attempt: still rate limited → count increments to 2
    // =====================================================================

    // Simulate QueueChainingJob consuming the delay queue: it calls
    // stepService.createReadySteps(template, workflowRun, input) which creates
    // a new READY step with the input from the delay queue.
    Step stepReady2 = buildReadyStep(stepTemplate, workflowRun, capturedEntry.getInput());

    reset(stepDelayQueueRepository);
    // Still at the limit
    when(injectStatusRepository.countLaunchedInjectsSince(anyString(), any(Instant.class)))
        .thenReturn(3L);

    // Act — second run attempt
    stepEventService.run(stepReady2);

    // Assert — count incremented to 2
    assertTrue(
        stepReady2.getInput().contains("\"_rateLimitCount\":2"),
        "Input should contain _rateLimitCount:2 after second reschedule, got: "
            + stepReady2.getInput());

    // Assert — delay queue was saved again with count 2
    verify(stepDelayQueueRepository).save(delayCaptor.capture());
    StepDelayQueue secondEntry = delayCaptor.getValue();
    assertTrue(
        secondEntry.getInput().contains("\"_rateLimitCount\":2"),
        "Second delay queue entry must carry _rateLimitCount:2, got: " + secondEntry.getInput());

    // =====================================================================
    // PHASE 3 — Third attempt: rate limit now allows execution
    // =====================================================================

    // New READY step created from delay queue, carrying count=2
    Step stepReady3 = buildReadyStep(stepTemplate, workflowRun, secondEntry.getInput());

    // Now only 2 terminal injects → under the limit of 3
    when(injectStatusRepository.countLaunchedInjectsSince(anyString(), any(Instant.class)))
        .thenReturn(2L);

    // Mock the action step execution
    ActionStep actionStep = mock(ActionStep.class);
    Step stepRun = new Step();
    stepRun.setId(UUID.randomUUID().toString());
    stepRun.setStatus(StepStatus.RUN);

    when(stepService.factoryAction(eq(StepActionClass.INJECT_EXECUTION), eq(stepReady3.getId())))
        .thenReturn(actionStep);
    when(actionStep.run(stepReady3)).thenReturn(Optional.of(stepRun));
    when(stepService.saveStep(stepRun)).thenReturn(stepRun);

    // Act — third run attempt, this time it should proceed
    stepEventService.run(stepReady3);

    // Assert — action step was executed
    verify(actionStep).run(stepReady3);
    verify(stepService).saveStep(stepRun);
    assertEquals(StepStatus.RUN, stepRun.getStatus());

    // Assert — the input passed to the action step still contains _rateLimitCount:2
    // (it was NOT cleared, so InjectExecutionStep.run() can read it and emit the INFO trace)
    assertEquals(
        2,
        StepEventService.getRateLimitCount(stepReady3.getInput()),
        "The executed step's input should still carry _rateLimitCount:2 for INFO trace emission");
  }

  @Test
  @DisplayName("Null input is handled gracefully through the full reschedule cycle")
  void fullCycle_withNullInput() {
    // READY step with null input
    Step stepReady = buildReadyStep(stepTemplate, workflowRun, null);

    when(workflowService.isWorkflowEnded(workflowRun.getId())).thenReturn(false);
    when(injectStatusRepository.countLaunchedInjectsSince(anyString(), any(Instant.class)))
        .thenReturn(5L);

    // Act — should not throw NPE
    stepEventService.run(stepReady);

    // Assert — input defaulted to {} and enriched with count
    assertNotNull(stepReady.getInput(), "Input must no longer be null after reschedule");
    assertTrue(
        stepReady.getInput().contains("\"_rateLimitCount\":1"),
        "Count should be 1 even when starting from null input, got: " + stepReady.getInput());

    // Assert — delay queue preserves the non-null enriched input
    ArgumentCaptor<StepDelayQueue> captor = ArgumentCaptor.forClass(StepDelayQueue.class);
    verify(stepDelayQueueRepository).save(captor.capture());
    assertEquals(
        stepReady.getInput(),
        captor.getValue().getInput(),
        "Delay queue input should match the step's enriched input");
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
