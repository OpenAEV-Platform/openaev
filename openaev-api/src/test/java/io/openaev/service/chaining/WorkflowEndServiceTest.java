package io.openaev.service.chaining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

import io.openaev.database.model.*;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.WorkflowRepository;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.inject.service.InjectStatusService;
import io.openaev.telemetry.metric_collectors.ResultsMetricCollector;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkflowEndService Tests")
class WorkflowEndServiceTest {

  @Mock private StepService stepService;
  @Mock private StepDelayQueueService stepDelayQueueService;
  @Mock private InjectService injectService;
  @Mock private InjectStatusService injectStatusService;
  @Mock private ResultsMetricCollector resultsMetricCollector;
  @Mock private WorkflowRepository workflowRepository;
  @Mock private ExerciseRepository exerciseRepository;

  @InjectMocks private WorkflowEndService workflowEndService;

  @Nested
  @DisplayName("forceCompleteWorkflowByTimeout")
  class ForceCompleteWorkflowTests {

    @Test
    @DisplayName(
        "should_endSteps_deleteDelayQueue_endWorkflow_completeActiveInjects_finishSimulation_inOrder")
    void
        should_endSteps_deleteDelayQueue_endWorkflow_completeActiveInjects_finishSimulation_inOrder() {
      // Arrange
      Exercise simulation = new Exercise();
      simulation.setId(UUID.randomUUID().toString());
      simulation.setStatus(ExerciseStatus.RUNNING);
      Workflow workflowRun = buildRunWorkflowWithSimulation(simulation);
      when(stepService.endActiveStepsByWorkflowId(workflowRun.getId())).thenReturn(3);

      Inject activeInject = buildInjectWithStatus(ExecutionStatus.PENDING);
      Inject finishedInject = buildInjectWithStatus(ExecutionStatus.EXECUTED);
      when(injectService.findBySimulationId(simulation.getId()))
          .thenReturn(List.of(activeInject, finishedInject));

      // Act
      workflowEndService.forceCompleteWorkflowByTimeout(workflowRun);

      // Assert — verify ordering
      InOrder inOrder =
          inOrder(
              stepService,
              stepDelayQueueService,
              workflowRepository,
              injectService,
              injectStatusService,
              exerciseRepository);
      inOrder.verify(stepService).endActiveStepsByWorkflowId(workflowRun.getId());
      inOrder.verify(stepDelayQueueService).deleteAllByWorkflowRun(workflowRun);
      inOrder.verify(workflowRepository).save(workflowRun);
      inOrder.verify(injectService).findBySimulationId(simulation.getId());
      inOrder.verify(injectStatusService).save(activeInject.getStatus().get());
      inOrder.verify(exerciseRepository).save(simulation);

      // Assert — active inject status was set to SUCCESS with info trace
      assertEquals(ExecutionStatus.ERROR, activeInject.getStatus().get().getName());
      assertNotNull(activeInject.getStatus().get().getTrackingEndDate());
      assertEquals(1, activeInject.getStatus().get().getTraces().size());
      ExecutionTrace trace = activeInject.getStatus().get().getTraces().get(0);
      assertEquals(ExecutionTraceStatus.TIMEOUT, trace.getStatus());
      assertEquals(ExecutionTraceAction.COMPLETE, trace.getAction());
      assertEquals("Inject stopped due to simulation timeout.", trace.getMessage());

      // Assert — already finished inject was NOT touched
      verify(injectStatusService, never()).save(finishedInject.getStatus().get());

      // Assert simulation status is FINISHED
      assertEquals(ExerciseStatus.FINISHED, simulation.getStatus());
    }

    @Test
    @DisplayName("should_completeAllActiveInjectStatuses_queuingExecutingPending")
    void should_completeAllActiveInjectStatuses_queuingExecutingPending() {
      // Arrange
      Exercise simulation = new Exercise();
      simulation.setId(UUID.randomUUID().toString());
      simulation.setStatus(ExerciseStatus.RUNNING);
      Workflow workflowRun = buildRunWorkflowWithSimulation(simulation);
      when(stepService.endActiveStepsByWorkflowId(workflowRun.getId())).thenReturn(0);

      Inject queuingInject = buildInjectWithStatus(ExecutionStatus.QUEUING);
      Inject executingInject = buildInjectWithStatus(ExecutionStatus.EXECUTING);
      Inject pendingInject = buildInjectWithStatus(ExecutionStatus.PENDING);
      when(injectService.findBySimulationId(simulation.getId()))
          .thenReturn(List.of(queuingInject, executingInject, pendingInject));

      // Act
      workflowEndService.forceCompleteWorkflowByTimeout(workflowRun);

      // Assert — all three active injects completed with ERROR
      assertEquals(ExecutionStatus.ERROR, queuingInject.getStatus().get().getName());
      assertEquals(ExecutionStatus.ERROR, executingInject.getStatus().get().getName());
      assertEquals(ExecutionStatus.ERROR, pendingInject.getStatus().get().getName());
      verify(injectStatusService).save(queuingInject.getStatus().get());
      verify(injectStatusService).save(executingInject.getStatus().get());
      verify(injectStatusService).save(pendingInject.getStatus().get());
    }

    @Test
    @DisplayName("given_noActiveInjects_should_stillFinishSimulation")
    void given_noActiveInjects_should_stillFinishSimulation() {
      // Arrange
      Exercise simulation = new Exercise();
      simulation.setId(UUID.randomUUID().toString());
      simulation.setStatus(ExerciseStatus.RUNNING);
      Workflow workflowRun = buildRunWorkflowWithSimulation(simulation);
      when(stepService.endActiveStepsByWorkflowId(workflowRun.getId())).thenReturn(0);

      Inject finishedInject = buildInjectWithStatus(ExecutionStatus.EXECUTED);
      when(injectService.findBySimulationId(simulation.getId()))
          .thenReturn(List.of(finishedInject));

      // Act
      workflowEndService.forceCompleteWorkflowByTimeout(workflowRun);

      // Assert — simulation still finished, no inject status changed
      verify(exerciseRepository).save(simulation);
      verifyNoInteractions(injectStatusService);
      assertEquals(ExerciseStatus.FINISHED, simulation.getStatus());
    }

    @Test
    @DisplayName("given_noSimulation_should_stillEndWorkflowWithoutError")
    void given_noSimulation_should_stillEndWorkflowWithoutError() {
      // Arrange
      Workflow workflowRun = buildRunWorkflow();
      when(stepService.endActiveStepsByWorkflowId(workflowRun.getId())).thenReturn(0);

      // Act
      workflowEndService.forceCompleteWorkflowByTimeout(workflowRun);

      // Assert — workflow ended, no simulation interaction
      verify(stepService).endActiveStepsByWorkflowId(workflowRun.getId());
      verify(stepDelayQueueService).deleteAllByWorkflowRun(workflowRun);
      verify(workflowRepository).save(workflowRun);
      verifyNoInteractions(exerciseRepository);
      verifyNoInteractions(injectService);
      verifyNoInteractions(injectStatusService);
    }
  }

  @Nested
  @DisplayName("endWorkflow / stopSimulationByEndWorkflow")
  class EndWorkflowSimulationSyncTests {

    @Test
    @DisplayName("given NO_MORE_PROGRESS should end workflow and finish its simulation")
    void given_noMoreProgress_should_finishAssociatedSimulation() {
      // Arrange
      Exercise simulation = new Exercise();
      simulation.setId(UUID.randomUUID().toString());
      simulation.setStatus(ExerciseStatus.RUNNING);
      Workflow workflowRun = buildRunWorkflowWithSimulation(simulation);
      Inject finishedInject = buildInjectWithStatus(ExecutionStatus.EXECUTED);
      when(injectService.findBySimulationId(simulation.getId())).thenReturn(List.of(finishedInject));

      // Act
      workflowEndService.endWorkflow(
          workflowRun, WorkflowEndService.WORKFLOW_END_CAUSE.NO_MORE_PROGRESS);

      // Assert
      assertEquals(WorkflowStatus.END, workflowRun.getStatus());
      assertEquals(ExerciseStatus.FINISHED, simulation.getStatus());
      verify(exerciseRepository).save(simulation);
      verify(workflowRepository).save(workflowRun);
    }

    @Test
    @DisplayName("given workflow already END should finish simulation via stopSimulationByEndWorkflow")
    void given_workflowAlreadyEnd_should_finishAssociatedSimulation() {
      // Arrange
      Exercise simulation = new Exercise();
      simulation.setId(UUID.randomUUID().toString());
      simulation.setStatus(ExerciseStatus.RUNNING);
      Workflow workflowRun = buildRunWorkflowWithSimulation(simulation);
      workflowRun.setStatus(WorkflowStatus.END);
      Inject finishedInject = buildInjectWithStatus(ExecutionStatus.EXECUTED);
      when(injectService.findBySimulationId(simulation.getId())).thenReturn(List.of(finishedInject));

      // Act
      workflowEndService.stopSimulationByEndWorkflow(workflowRun);

      // Assert
      assertEquals(ExerciseStatus.FINISHED, simulation.getStatus());
      verify(exerciseRepository).save(simulation);
    }
  }

  @Nested
  @DisplayName("findAllExpiredRunWorkflows")
  class FindAllExpiredRunWorkflowsTests {

    @Test
    @DisplayName("should_delegateToWorkflowService")
    void should_delegateToWorkflowService() {
      // Arrange
      List<Workflow> expected = List.of(buildRunWorkflow());
      when(workflowRepository.findAllExpiredRunWorkflowIds())
          .thenReturn(expected.stream().map(Workflow::getId).toList());
      when(workflowRepository.findAllByIdWithScopeRules(expected.stream().map(Workflow::getId).toList()))
          .thenReturn(expected);

      // Act
      List<Workflow> result = workflowEndService.findAllExpiredRunWorkflows();

      // Assert
      assertEquals(expected, result);
      verify(workflowRepository).findAllExpiredRunWorkflowIds();
      verify(workflowRepository).findAllByIdWithScopeRules(expected.stream().map(Workflow::getId).toList());
    }
  }

  private static Workflow buildRunWorkflow() {
    Workflow workflow = new Workflow();
    workflow.setId(UUID.randomUUID().toString());
    workflow.setStatus(WorkflowStatus.RUN);
    return workflow;
  }

  private static Workflow buildRunWorkflowWithSimulation(Exercise simulation) {
    Workflow workflow = buildRunWorkflow();
    workflow.setSimulation(simulation);
    return workflow;
  }

  private static Inject buildInjectWithStatus(ExecutionStatus status) {
    Inject inject = new Inject();
    inject.setId(UUID.randomUUID().toString());
    InjectStatus injectStatus = new InjectStatus();
    injectStatus.setId(UUID.randomUUID().toString());
    injectStatus.setName(status);
    inject.setStatus(injectStatus);
    return inject;
  }
}
