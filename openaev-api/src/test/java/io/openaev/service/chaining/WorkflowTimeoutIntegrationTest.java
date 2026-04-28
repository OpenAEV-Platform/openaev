package io.openaev.service.chaining;

import static org.junit.jupiter.api.Assertions.*;

import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.database.repository.StepDelayQueueRepository;
import io.openaev.database.repository.StepRepository;
import io.openaev.database.repository.WorkflowRepository;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.StepComposer;
import io.openaev.utils.fixtures.composers.WorkflowComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WithMockUser(isAdmin = true)
@DisplayName("Workflow Timeout Integration Tests")
class WorkflowTimeoutIntegrationTest extends IntegrationTest {

  @Autowired private WorkflowTimeoutService workflowTimeoutService;
  @Autowired private WorkflowRepository workflowRepository;
  @Autowired private StepRepository stepRepository;
  @Autowired private StepDelayQueueRepository stepDelayQueueRepository;
  @Autowired private WorkflowComposer workflowComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private StepComposer stepComposer;

  @BeforeEach
  void setUp() {
    workflowComposer.reset();
    exerciseComposer.reset();
    stepComposer.reset();
  }

  // ========================================================================
  // forceCompleteWorkflow
  // ========================================================================
  @Nested
  @DisplayName("forceCompleteWorkflow")
  class ForceCompleteWorkflowTests {

    @Test
    @DisplayName("given_runningWorkflow_should_setStatusToEnd")
    void given_runningWorkflow_should_setStatusToEnd() {
      // Arrange
      Workflow workflowRun = createPersistedRunWorkflow();

      // Act
      workflowTimeoutService.forceCompleteWorkflow(workflowRun);

      // Assert
      Workflow result = workflowRepository.findById(workflowRun.getId()).orElseThrow();
      assertEquals(WorkflowStatus.END, result.getStatus());
    }

    @Test
    @DisplayName("given_workflowWithActiveSteps_should_endAllActiveSteps")
    void given_workflowWithActiveSteps_should_endAllActiveSteps() {
      // Arrange
      Workflow workflowRun = createPersistedRunWorkflow();

      Step stepReady = createPersistedStep(workflowRun, StepStatus.READY);
      Step stepRun = createPersistedStep(workflowRun, StepStatus.RUN);
      Step stepRun2 = createPersistedStep(workflowRun, StepStatus.RUN);

      // Act
      workflowTimeoutService.forceCompleteWorkflow(workflowRun);

      // Assert
      assertEquals(
          StepStatus.END, stepRepository.findById(stepReady.getId()).orElseThrow().getStatus());
      assertEquals(
          StepStatus.END, stepRepository.findById(stepRun.getId()).orElseThrow().getStatus());
      assertEquals(
          StepStatus.END, stepRepository.findById(stepRun2.getId()).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("given_workflowWithAlreadyEndedSteps_should_notModifyThem")
    void given_workflowWithAlreadyEndedSteps_should_notModifyThem() {
      // Arrange
      Workflow workflowRun = createPersistedRunWorkflow();

      Step stepAlreadyEnded = createPersistedStep(workflowRun, StepStatus.END);
      Step stepActive = createPersistedStep(workflowRun, StepStatus.RUN);

      // Act
      workflowTimeoutService.forceCompleteWorkflow(workflowRun);

      // Assert
      assertEquals(
          StepStatus.END,
          stepRepository.findById(stepAlreadyEnded.getId()).orElseThrow().getStatus());
      assertEquals(
          StepStatus.END, stepRepository.findById(stepActive.getId()).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("given_workflowWithDelayQueueEntries_should_purgeDelayQueue")
    void given_workflowWithDelayQueueEntries_should_purgeDelayQueue() {
      // Arrange
      Workflow workflowRun = createPersistedRunWorkflow();

      Step stepTemplate = createPersistedStep(workflowRun, StepStatus.TEMPLATE);

      StepDelayQueue delayEntry =
          StepDelayQueue.builder()
              .workflowRun(workflowRun)
              .stepTemplate(stepTemplate)
              .input("{}")
              .now(Instant.now())
              .goal(Instant.now().plus(1, ChronoUnit.HOURS))
              .delay(3600000L)
              .build();
      stepDelayQueueRepository.save(delayEntry);

      assertFalse(stepDelayQueueRepository.findAllByWorkflowRun(workflowRun).isEmpty());

      // Act
      workflowTimeoutService.forceCompleteWorkflow(workflowRun);

      // Assert
      assertTrue(stepDelayQueueRepository.findAllByWorkflowRun(workflowRun).isEmpty());
    }

    @Test
    @DisplayName("given_workflowWithMixedStepsAndDelayQueue_should_endAllAndPurge")
    void given_workflowWithMixedStepsAndDelayQueue_should_endAllAndPurge() {
      // Arrange
      Workflow workflowRun = createPersistedRunWorkflow();

      Step stepReady = createPersistedStep(workflowRun, StepStatus.READY);
      Step stepRun = createPersistedStep(workflowRun, StepStatus.RUN);
      Step stepEnd = createPersistedStep(workflowRun, StepStatus.END);
      Step stepTemplate = createPersistedStep(workflowRun, StepStatus.TEMPLATE);

      StepDelayQueue delayEntry =
          StepDelayQueue.builder()
              .workflowRun(workflowRun)
              .stepTemplate(stepTemplate)
              .input("{}")
              .now(Instant.now())
              .goal(Instant.now().plus(1, ChronoUnit.HOURS))
              .delay(3600000L)
              .build();
      stepDelayQueueRepository.save(delayEntry);

      // Act
      workflowTimeoutService.forceCompleteWorkflow(workflowRun);

      // Assert
      Workflow result = workflowRepository.findById(workflowRun.getId()).orElseThrow();
      assertEquals(WorkflowStatus.END, result.getStatus());

      assertEquals(
          StepStatus.END, stepRepository.findById(stepReady.getId()).orElseThrow().getStatus());
      assertEquals(
          StepStatus.END, stepRepository.findById(stepRun.getId()).orElseThrow().getStatus());
      assertEquals(
          StepStatus.END, stepRepository.findById(stepEnd.getId()).orElseThrow().getStatus());

      assertTrue(stepDelayQueueRepository.findAllByWorkflowRun(workflowRun).isEmpty());
    }

    @Test
    @DisplayName("given_alreadyEndedWorkflow_should_beIdempotent")
    void given_alreadyEndedWorkflow_should_beIdempotent() {
      // Arrange
      Workflow workflowRun = createPersistedRunWorkflow();
      Step stepRun = createPersistedStep(workflowRun, StepStatus.RUN);

      // First call — normal force-complete
      workflowTimeoutService.forceCompleteWorkflow(workflowRun);
      assertEquals(
          WorkflowStatus.END,
          workflowRepository.findById(workflowRun.getId()).orElseThrow().getStatus());
      assertEquals(
          StepStatus.END, stepRepository.findById(stepRun.getId()).orElseThrow().getStatus());

      // Act — second call on already-ended workflow (simulates concurrent pod execution)
      assertDoesNotThrow(() -> workflowTimeoutService.forceCompleteWorkflow(workflowRun));

      // Assert — still END, no exception
      assertEquals(
          WorkflowStatus.END,
          workflowRepository.findById(workflowRun.getId()).orElseThrow().getStatus());
    }
  }

  // ========================================================================
  // findAllExpiredRunWorkflows (repository query)
  // ========================================================================
  @Nested
  @DisplayName("findAllExpiredRunWorkflows")
  class FindAllExpiredRunWorkflowsTests {

    @Test
    @DisplayName("given_noExpiredWorkflows_should_returnEmptyList")
    void given_noExpiredWorkflows_should_returnEmptyList() {
      // Arrange
      Workflow notExpired = createPersistedRunWorkflowWithTimeout(true, 3600L, Instant.now());

      // Act
      List<Workflow> result = workflowTimeoutService.findAllExpiredRunWorkflows();

      // Assert
      assertTrue(result.stream().noneMatch(w -> w.getId().equals(notExpired.getId())));
    }

    @Test
    @DisplayName("given_expiredRunWorkflow_should_returnIt")
    void given_expiredRunWorkflow_should_returnIt() {
      // Arrange — created 2 hours ago with 1 hour timeout → expired
      Workflow expired =
          createPersistedRunWorkflowWithTimeout(
              true, 3600L, Instant.now().minus(2, ChronoUnit.HOURS));

      // Act
      List<Workflow> result = workflowTimeoutService.findAllExpiredRunWorkflows();

      // Assert
      assertTrue(result.stream().anyMatch(w -> w.getId().equals(expired.getId())));
    }

    @Test
    @DisplayName("given_workflowWithTimeoutDisabled_should_notReturnIt")
    void given_workflowWithTimeoutDisabled_should_notReturnIt() {
      // Arrange — very old but timeout disabled
      Workflow noTimeout =
          createPersistedRunWorkflowWithTimeout(
              false, 60L, Instant.now().minus(24, ChronoUnit.HOURS));

      // Act
      List<Workflow> result = workflowTimeoutService.findAllExpiredRunWorkflows();

      // Assert
      assertTrue(result.stream().noneMatch(w -> w.getId().equals(noTimeout.getId())));
    }

    @Test
    @DisplayName("given_templateWorkflowWithTimeout_should_notReturnIt")
    void given_templateWorkflowWithTimeout_should_notReturnIt() {
      // Arrange — TEMPLATE status, not RUN
      Workflow template = WorkflowFixture.getDefaultWorkflowTemplate();
      template.setTimeoutEnabled(true);
      template.setTimeoutSeconds(60L);
      template.setWorkflowCreatedAt(Instant.now().minus(2, ChronoUnit.HOURS));
      ExerciseComposer.Composer simComposer =
          exerciseComposer.forExercise(ExerciseFixture.createDefaultExercise());
      workflowComposer.forWorkflow(template).withSimulation(simComposer).persist();

      // Act
      List<Workflow> result = workflowTimeoutService.findAllExpiredRunWorkflows();

      // Assert
      assertTrue(result.stream().noneMatch(w -> w.getId().equals(template.getId())));
    }

    @Test
    @DisplayName("given_mixedWorkflows_should_returnOnlyExpiredRunOnes")
    void given_mixedWorkflows_should_returnOnlyExpiredRunOnes() {
      // Arrange
      Workflow expired1 =
          createPersistedRunWorkflowWithTimeout(
              true, 60L, Instant.now().minus(2, ChronoUnit.HOURS));
      Workflow expired2 =
          createPersistedRunWorkflowWithTimeout(
              true, 120L, Instant.now().minus(1, ChronoUnit.HOURS));
      Workflow notExpired = createPersistedRunWorkflowWithTimeout(true, 7200L, Instant.now());
      Workflow disabledTimeout =
          createPersistedRunWorkflowWithTimeout(
              false, 60L, Instant.now().minus(2, ChronoUnit.HOURS));

      // Act
      List<Workflow> result = workflowTimeoutService.findAllExpiredRunWorkflows();

      // Assert
      List<String> resultIds = result.stream().map(Workflow::getId).toList();
      assertTrue(resultIds.contains(expired1.getId()));
      assertTrue(resultIds.contains(expired2.getId()));
      assertFalse(resultIds.contains(notExpired.getId()));
      assertFalse(resultIds.contains(disabledTimeout.getId()));
    }

    @Test
    @DisplayName("given_workflowJustExpired_should_returnIt")
    void given_workflowJustExpired_should_returnIt() {
      // Arrange — created exactly timeoutSeconds ago (edge case <=)
      Workflow justExpired =
          createPersistedRunWorkflowWithTimeout(
              true, 60L, Instant.now().minus(60, ChronoUnit.SECONDS));

      // Act
      List<Workflow> result = workflowTimeoutService.findAllExpiredRunWorkflows();

      // Assert
      assertTrue(result.stream().anyMatch(w -> w.getId().equals(justExpired.getId())));
    }
  }

  // ========================================================================
  // Helpers
  // ========================================================================

  private Workflow createPersistedRunWorkflow() {
    Workflow workflowRun = WorkflowFixture.getDefaultWorkflowExecution(WorkflowStatus.RUN);
    workflowRun.setTimeoutEnabled(true);
    workflowRun.setTimeoutSeconds(3600L);

    ExerciseComposer.Composer simComposer =
        exerciseComposer.forExercise(ExerciseFixture.createDefaultExercise());

    return workflowComposer.forWorkflow(workflowRun).withSimulation(simComposer).persist().get();
  }

  private Workflow createPersistedRunWorkflowWithTimeout(
      boolean timeoutEnabled, Long timeoutSeconds, Instant createdAt) {
    Workflow workflowRun = WorkflowFixture.getDefaultWorkflowExecution(WorkflowStatus.RUN);
    workflowRun.setTimeoutEnabled(timeoutEnabled);
    workflowRun.setTimeoutSeconds(timeoutSeconds);

    ExerciseComposer.Composer simComposer =
        exerciseComposer.forExercise(ExerciseFixture.createDefaultExercise());

    Workflow persisted =
        workflowComposer.forWorkflow(workflowRun).withSimulation(simComposer).persist().get();

    // Force the createdAt after initial persist — @CreationTimestamp only acts on INSERT,
    // so this UPDATE will keep our custom value.
    persisted.setWorkflowCreatedAt(createdAt);
    return workflowRepository.save(persisted);
  }

  private Step createPersistedStep(Workflow workflow, StepStatus status) {
    Step step =
        Step.builder()
            .stepAction(StepActionClass.INJECT_EXECUTION)
            .status(status)
            .workflow(workflow)
            .limitExecution(1)
            .build();
    return stepRepository.save(step);
  }
}
