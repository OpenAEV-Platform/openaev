package io.openaev.service.chaining;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.openaev.context.TxCtx;
import io.openaev.database.model.Workflow;
import io.openaev.database.model.WorkflowStatus;
import io.openaev.database.repository.WorkflowRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("resumeWorkflowRunIsolated")
class WorkflowResumeServiceTest {

  private static final String TENANT = "tenant-1";

  @Mock private WorkflowRepository workflowRepository;
  @Mock private StepDelayQueueService stepDelayQueueService;

  private WorkflowResumeService workflowResumeService;

  @BeforeEach
  void setUp() {
    workflowResumeService = new WorkflowResumeService(workflowRepository, stepDelayQueueService);
  }

  @Test
  @DisplayName("puts a STOP run back to RUN, accumulates the pause and shifts delay goals")
  void given_stoppedRun_should_resumeAndAccumulatePause() {
    // Arrange
    Instant resumeAt = Instant.now();
    Instant pausedAt = resumeAt.minusSeconds(120);
    Workflow stoppedRun = new Workflow();
    stoppedRun.setId("run-1");
    stoppedRun.setStatus(WorkflowStatus.STOP);
    stoppedRun.setPauseAt(pausedAt);
    stoppedRun.setPauseSecond(10L);
    when(workflowRepository.findForUpdateByIdAndStatus("run-1", WorkflowStatus.STOP))
        .thenReturn(Optional.of(stoppedRun));

    // Act
    boolean resumed =
        workflowResumeService.resumeWorkflowRunIsolated(TxCtx.forTenant(TENANT), "run-1", resumeAt);

    // Assert
    assertTrue(resumed);
    assertEquals(WorkflowStatus.RUN, stoppedRun.getStatus());
    assertNull(stoppedRun.getPauseAt());
    assertEquals(130L, stoppedRun.getPauseSecond());
    verify(stepDelayQueueService).recalculateGoalsOnResume(stoppedRun, resumeAt, pausedAt);
    verify(workflowRepository).save(stoppedRun);
  }

  @Test
  @DisplayName("resumes nothing when the run is no longer STOP")
  void given_runNotStopped_should_notResume() {
    // Arrange
    when(workflowRepository.findForUpdateByIdAndStatus("run-1", WorkflowStatus.STOP))
        .thenReturn(Optional.empty());

    // Act
    boolean resumed =
        workflowResumeService.resumeWorkflowRunIsolated(
            TxCtx.forTenant(TENANT), "run-1", Instant.now());

    // Assert
    assertFalse(resumed);
    verifyNoInteractions(stepDelayQueueService);
    verify(workflowRepository, never()).save(any());
  }
}
