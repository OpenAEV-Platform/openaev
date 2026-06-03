package io.openaev.service.chaining;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.openaev.database.model.Exercise;
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
@DisplayName("RateLimitGuardService Tests")
class RateLimitGuardServiceTest {

  @Mock private InjectStatusRepository injectStatusRepository;

  @InjectMocks private RateLimitGuardService rateLimitGuardService;

  @Nested
  @DisplayName("isExecutionAllowed")
  class IsExecutionAllowedTests {

    @Test
    @DisplayName("should return true when rate limit is disabled")
    void should_returnTrue_when_rateLimitDisabled() {
      Workflow workflow = buildWorkflow(false, null, null);

      boolean result = rateLimitGuardService.isExecutionAllowed(workflow);

      assertTrue(result);
      verifyNoInteractions(injectStatusRepository);
    }

    @Test
    @DisplayName("should return true (fail-open) when maxAttempts is null")
    void should_returnTrue_when_maxAttemptsNull() {
      Workflow workflow = buildWorkflow(true, null, 60L);

      boolean result = rateLimitGuardService.isExecutionAllowed(workflow);

      assertTrue(result);
      verifyNoInteractions(injectStatusRepository);
    }

    @Test
    @DisplayName("should return true (fail-open) when maxTemporalRateSeconds is null")
    void should_returnTrue_when_maxTemporalRateSecondsNull() {
      Workflow workflow = buildWorkflow(true, 5, null);

      boolean result = rateLimitGuardService.isExecutionAllowed(workflow);

      assertTrue(result);
      verifyNoInteractions(injectStatusRepository);
    }

    @Test
    @DisplayName("should return true when count is below maxAttempts")
    void should_returnTrue_when_countBelowMax() {
      Workflow workflow = buildWorkflow(true, 5, 60L);
      when(injectStatusRepository.countTerminalInjectsSince(anyString(), any(Instant.class)))
          .thenReturn(4L);

      boolean result = rateLimitGuardService.isExecutionAllowed(workflow);

      assertTrue(result);
    }

    @Test
    @DisplayName("should return false when count equals maxAttempts")
    void should_returnFalse_when_countEqualsMax() {
      Workflow workflow = buildWorkflow(true, 5, 60L);
      when(injectStatusRepository.countTerminalInjectsSince(anyString(), any(Instant.class)))
          .thenReturn(5L);

      boolean result = rateLimitGuardService.isExecutionAllowed(workflow);

      assertFalse(result);
    }

    @Test
    @DisplayName("should return false when count exceeds maxAttempts")
    void should_returnFalse_when_countExceedsMax() {
      Workflow workflow = buildWorkflow(true, 3, 120L);
      when(injectStatusRepository.countTerminalInjectsSince(anyString(), any(Instant.class)))
          .thenReturn(10L);

      boolean result = rateLimitGuardService.isExecutionAllowed(workflow);

      assertFalse(result);
    }
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

