package io.openaev.service.chaining;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import io.openaev.database.model.Exercise;
import io.openaev.database.model.Step;
import io.openaev.database.model.StepStatus;
import io.openaev.database.model.Workflow;
import io.openaev.database.repository.InjectStatusRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SimulationRateLimitService Tests")
class SimulationRateLimitServiceTest {

  @Mock private InjectStatusRepository injectStatusRepository;
  @Mock private StepDelayQueueService stepDelayQueueService;

  @InjectMocks private SimulationRateLimitService simulationRateLimitService;

  @Nested
  @DisplayName("isExecutionAllowed")
  class IsExecutionAllowedTests {

    @Test
    @DisplayName("should return true when rate limit is disabled")
    void should_returnTrue_when_rateLimitDisabled() {
      Workflow workflow = buildWorkflow(false, null, null);

      boolean result = simulationRateLimitService.isExecutionAllowed(workflow);

      assertTrue(result);
      verifyNoInteractions(injectStatusRepository);
    }

    @Test
    @DisplayName("should return true (fail-open) when maxAttempts is null")
    void should_returnTrue_when_maxAttemptsNull() {
      Workflow workflow = buildWorkflow(true, null, 60L);

      boolean result = simulationRateLimitService.isExecutionAllowed(workflow);

      assertTrue(result);
      verifyNoInteractions(injectStatusRepository);
    }

    @Test
    @DisplayName("should return true (fail-open) when maxTemporalRateSeconds is null")
    void should_returnTrue_when_maxTemporalRateSecondsNull() {
      Workflow workflow = buildWorkflow(true, 5, null);

      boolean result = simulationRateLimitService.isExecutionAllowed(workflow);

      assertTrue(result);
      verifyNoInteractions(injectStatusRepository);
    }

    @Test
    @DisplayName("should return true when count is below maxAttempts")
    void should_returnTrue_when_countBelowMax() {
      Workflow workflow = buildWorkflow(true, 5, 60L);
      when(injectStatusRepository.countLaunchedInjectsSince(anyString(), any(Instant.class)))
          .thenReturn(4L);

      boolean result = simulationRateLimitService.isExecutionAllowed(workflow);

      assertTrue(result);
    }

    @Test
    @DisplayName("should return false when count equals maxAttempts")
    void should_returnFalse_when_countEqualsMax() {
      Workflow workflow = buildWorkflow(true, 5, 60L);
      when(injectStatusRepository.countLaunchedInjectsSince(anyString(), any(Instant.class)))
          .thenReturn(5L);

      boolean result = simulationRateLimitService.isExecutionAllowed(workflow);

      assertFalse(result);
    }

    @Test
    @DisplayName("should return false when count exceeds maxAttempts")
    void should_returnFalse_when_countExceedsMax() {
      Workflow workflow = buildWorkflow(true, 3, 120L);
      when(injectStatusRepository.countLaunchedInjectsSince(anyString(), any(Instant.class)))
          .thenReturn(10L);

      boolean result = simulationRateLimitService.isExecutionAllowed(workflow);

      assertFalse(result);
    }
  }

  @Nested
  @DisplayName("requeueIfRateLimitReached")
  class RequeueIfRateLimitReachedTests {

    @Test
    @DisplayName("should return false and not reschedule when rate limit is not reached")
    void should_returnFalse_when_rateLimitNotReached() {
      Workflow workflow = buildWorkflow(true, 5, 60L);
      Step stepReady = buildStep(workflow);

      when(injectStatusRepository.countLaunchedInjectsSince(anyString(), any(Instant.class)))
          .thenReturn(2L);

      boolean result = simulationRateLimitService.requeueIfRateLimitReached(stepReady, workflow);

      assertFalse(result);
      assertEquals(0, stepReady.getRateLimitCount());
      verify(stepDelayQueueService, never()).reschedule(any(), anyLong());
    }

    @Test
    @DisplayName("should return true and reschedule when rate limit is reached")
    void should_returnTrue_when_rateLimitReached() {
      Workflow workflow = buildWorkflow(true, 3, 120L);
      Step stepReady = buildStep(workflow);

      when(injectStatusRepository.countLaunchedInjectsSince(anyString(), any(Instant.class)))
          .thenReturn(3L);

      boolean result = simulationRateLimitService.requeueIfRateLimitReached(stepReady, workflow);

      assertTrue(result);
      assertEquals(1, stepReady.getRateLimitCount());
      verify(stepDelayQueueService).reschedule(stepReady, 120L);
    }

    @Test
    @DisplayName("should increment rateLimitCount when already rate-limited before")
    void should_incrementCount_when_alreadyRateLimited() {
      Workflow workflow = buildWorkflow(true, 3, 60L);
      Step stepReady = buildStep(workflow);
      stepReady.setRateLimitCount(2);

      when(injectStatusRepository.countLaunchedInjectsSince(anyString(), any(Instant.class)))
          .thenReturn(5L);

      boolean result = simulationRateLimitService.requeueIfRateLimitReached(stepReady, workflow);

      assertTrue(result);
      assertEquals(3, stepReady.getRateLimitCount());
      verify(stepDelayQueueService).reschedule(stepReady, 60L);
    }

    @Test
    @DisplayName("should use 60s fallback delay when maxTemporalRateSeconds is null")
    void should_useFallbackDelay_when_maxTemporalRateSecondsIsNull() {
      Workflow workflow = buildWorkflow(true, 3, null);
      Step stepReady = buildStep(workflow);

      // Rate limit is enabled but maxTemporalRateSeconds is null → isExecutionAllowed fails open.
      // So requeueIfRateLimitReached should return false (no requeue).
      boolean result = simulationRateLimitService.requeueIfRateLimitReached(stepReady, workflow);

      assertFalse(result);
      verify(stepDelayQueueService, never()).reschedule(any(), anyLong());
    }

    @Test
    @DisplayName("should return false when workflow is null")
    void should_returnFalse_when_workflowIsNull() {
      Step stepReady = buildStep(null);

      boolean result = simulationRateLimitService.requeueIfRateLimitReached(stepReady, null);

      assertFalse(result);
      verify(stepDelayQueueService, never()).reschedule(any(), anyLong());
    }

    @Test
    @DisplayName("should return false when rate limit is disabled")
    void should_returnFalse_when_rateLimitDisabled() {
      Workflow workflow = buildWorkflow(false, null, null);
      Step stepReady = buildStep(workflow);

      boolean result = simulationRateLimitService.requeueIfRateLimitReached(stepReady, workflow);

      assertFalse(result);
      assertEquals(0, stepReady.getRateLimitCount());
      verify(stepDelayQueueService, never()).reschedule(any(), anyLong());
    }
  }

  private Step buildStep(Workflow workflow) {
    Step step = new Step();
    step.setId(UUID.randomUUID().toString());
    step.setStatus(StepStatus.READY);
    step.setWorkflow(workflow);
    return step;
  }

  private Workflow buildWorkflow(
      boolean rateLimitEnabled, Integer maxAttempts, Long maxTemporalRateSeconds) {
    Exercise simulation = new Exercise();
    simulation.setId(UUID.randomUUID().toString());

    return Workflow.builder()
        .id(UUID.randomUUID().toString())
        .rateLimitEnabled(rateLimitEnabled)
        .maxAttempts(maxAttempts)
        .maxTemporalRateSeconds(maxTemporalRateSeconds)
        .simulation(simulation)
        .build();
  }
}
