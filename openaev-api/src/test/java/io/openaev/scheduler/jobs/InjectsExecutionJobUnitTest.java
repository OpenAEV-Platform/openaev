package io.openaev.scheduler.jobs;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.quality.Strictness.LENIENT;

import io.openaev.database.model.Exercise;
import io.openaev.database.model.ExerciseStatus;
import io.openaev.database.model.NotificationRuleResourceType;
import io.openaev.database.model.Scenario;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.notification.model.NotificationEvent;
import io.openaev.notification.model.NotificationEventType;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.NotificationEventService;
import io.openaev.service.PreviewFeatureService;
import io.openaev.service.SecurityCoverageSendJobService;
import io.openaev.service.chaining.WorkflowService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("InjectsExecutionJob Unit Tests")
class InjectsExecutionJobUnitTest {

  @Mock private ExerciseRepository exerciseRepository;
  @Mock private PreviewFeatureService previewFeatureService;
  @Mock private WorkflowService workflowService;
  @Mock private SecurityCoverageSendJobService securityCoverageSendJobService;
  @Mock private NotificationEventService notificationEventService;

  @InjectMocks private InjectsExecutionJob injectsExecutionJob;

  @BeforeEach
  void setUp() {
    reset(
        exerciseRepository,
        previewFeatureService,
        workflowService,
        securityCoverageSendJobService,
        notificationEventService);
  }

  // ========================================================================
  // getWorkflowById Tests
  // ========================================================================
  @Nested
  @DisplayName("handleAutoClosingExercises")
  class HandleAutoClosingExercisesTests {

    @Captor private ArgumentCaptor<List<Exercise>> exercisesCaptor;

    @Captor private ArgumentCaptor<NotificationEvent> notificationEventCaptor;

    private Exercise createMockExercise(String id, Scenario scenario) {
      Exercise exercise = mock(Exercise.class, withSettings().strictness(LENIENT));
      when(exercise.getId()).thenReturn(id);
      when(exercise.getScenario()).thenReturn(scenario);
      return exercise;
    }

    @Test
    @DisplayName("should finish exercises and update their status")
    void shouldFinishExercisesAndUpdateStatus() {
      // Prepare
      String exerciseId = UUID.randomUUID().toString();
      Exercise exercise = createMockExercise(exerciseId, null);
      List<Exercise> exercises = new ArrayList<>(List.of(exercise));

      when(exerciseRepository.thatMustBeFinished()).thenReturn(exercises);
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.INJECT_CHAINING))
          .thenReturn(false);
      when(exerciseRepository.saveAll(anyList())).thenReturn(exercises);

      // Act
      injectsExecutionJob.handleAutoClosingExercises();

      // Assert
      verify(exercise).setStatus(ExerciseStatus.FINISHED);
      verify(exercise).setEnd(any(Instant.class));
      verify(exercise).setUpdatedAt(any(Instant.class));
      verify(exerciseRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("should filter out chaining exercises when feature is enabled")
    void shouldFilterOutChainingExercisesWhenFeatureEnabled() {
      // Prepare
      String chainingExerciseId = UUID.randomUUID().toString();
      String normalExerciseId = UUID.randomUUID().toString();

      Exercise chainingExercise = createMockExercise(chainingExerciseId, null);
      Exercise normalExercise = createMockExercise(normalExerciseId, null);
      List<Exercise> exercises = new ArrayList<>(List.of(chainingExercise, normalExercise));

      when(exerciseRepository.thatMustBeFinished()).thenReturn(exercises);
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.INJECT_CHAINING)).thenReturn(true);
      when(workflowService.isExerciseChaining(chainingExerciseId)).thenReturn(true);
      when(workflowService.isExerciseChaining(normalExerciseId)).thenReturn(false);
      when(exerciseRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

      // Act
      injectsExecutionJob.handleAutoClosingExercises();

      // Assert
      verify(exerciseRepository).saveAll(exercisesCaptor.capture());
      List<Exercise> savedExercises = exercisesCaptor.getValue();
      assertEquals(1, savedExercises.size());
      assertEquals(normalExerciseId, savedExercises.getFirst().getId());
    }

    @Test
    @DisplayName("should not filter exercises when chaining feature is disabled")
    void shouldNotFilterExercisesWhenChainingFeatureDisabled() {
      // Prepare
      String exerciseId1 = UUID.randomUUID().toString();
      String exerciseId2 = UUID.randomUUID().toString();

      Exercise exercise1 = createMockExercise(exerciseId1, null);
      Exercise exercise2 = createMockExercise(exerciseId2, null);
      List<Exercise> exercises = new ArrayList<>(List.of(exercise1, exercise2));

      when(exerciseRepository.thatMustBeFinished()).thenReturn(exercises);
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.INJECT_CHAINING))
          .thenReturn(false);
      when(exerciseRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

      // Act
      injectsExecutionJob.handleAutoClosingExercises();

      // Assert
      verify(workflowService, never()).isExerciseChaining(anyString());
      verify(exerciseRepository).saveAll(exercisesCaptor.capture());
      assertEquals(2, exercisesCaptor.getValue().size());
    }

    @Test
    @DisplayName("should trigger coverage send job for finished exercises")
    void shouldTriggerCoverageSendJob() {
      // Prepare
      Exercise exercise = createMockExercise(UUID.randomUUID().toString(), null);
      List<Exercise> exercises = new ArrayList<>(List.of(exercise));

      when(exerciseRepository.thatMustBeFinished()).thenReturn(exercises);
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.INJECT_CHAINING))
          .thenReturn(false);
      when(exerciseRepository.saveAll(anyList())).thenReturn(exercises);

      // Act
      injectsExecutionJob.handleAutoClosingExercises();

      // Assert
      verify(securityCoverageSendJobService)
          .createOrUpdateCoverageSendJobForSimulationsIfReady(exercises);
    }

    @Test
    @DisplayName("should send notification for exercises with scenario")
    void shouldSendNotificationForExercisesWithScenario() {
      // Prepare
      String scenarioId = UUID.randomUUID().toString();
      Scenario scenario = mock(Scenario.class);
      when(scenario.getId()).thenReturn(scenarioId);

      Exercise exercise = createMockExercise(UUID.randomUUID().toString(), scenario);
      List<Exercise> exercises = new ArrayList<>(List.of(exercise));

      when(exerciseRepository.thatMustBeFinished()).thenReturn(exercises);
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.INJECT_CHAINING))
          .thenReturn(false);
      when(exerciseRepository.saveAll(anyList())).thenReturn(exercises);

      // Act
      injectsExecutionJob.handleAutoClosingExercises();

      // Assert
      verify(notificationEventService)
          .sendNotificationEventWithDelay(notificationEventCaptor.capture(), any(Long.class));

      NotificationEvent event = notificationEventCaptor.getValue();
      assertEquals(NotificationEventType.SIMULATION_COMPLETED, event.getEventType());
      assertEquals(NotificationRuleResourceType.SCENARIO, event.getResourceType());
      assertEquals(scenarioId, event.getResourceId());
      assertNotNull(event.getTimestamp());
    }

    @Test
    @DisplayName("should not send notification for exercises without scenario")
    void shouldNotSendNotificationForExercisesWithoutScenario() {
      // Prepare
      Exercise exercise = createMockExercise(UUID.randomUUID().toString(), null);
      List<Exercise> exercises = new ArrayList<>(List.of(exercise));

      when(exerciseRepository.thatMustBeFinished()).thenReturn(exercises);
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.INJECT_CHAINING))
          .thenReturn(false);
      when(exerciseRepository.saveAll(anyList())).thenReturn(exercises);

      // Act
      injectsExecutionJob.handleAutoClosingExercises();

      // Assert
      verify(notificationEventService, never()).sendNotificationEventWithDelay(any(), anyLong());
    }

    @Test
    @DisplayName("should send notifications only for exercises with scenario in mixed list")
    void shouldSendNotificationsOnlyForExercisesWithScenarioInMixedList() {
      // Prepare
      String scenarioId = UUID.randomUUID().toString();
      Scenario scenario = mock(Scenario.class);
      when(scenario.getId()).thenReturn(scenarioId);

      Exercise exerciseWithScenario = createMockExercise(UUID.randomUUID().toString(), scenario);
      Exercise exerciseWithoutScenario = createMockExercise(UUID.randomUUID().toString(), null);
      List<Exercise> exercises =
          new ArrayList<>(List.of(exerciseWithScenario, exerciseWithoutScenario));

      when(exerciseRepository.thatMustBeFinished()).thenReturn(exercises);
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.INJECT_CHAINING))
          .thenReturn(false);
      when(exerciseRepository.saveAll(anyList())).thenReturn(exercises);

      // Act
      injectsExecutionJob.handleAutoClosingExercises();

      // Assert
      verify(notificationEventService, times(1)).sendNotificationEventWithDelay(any(), anyLong());
    }

    @Test
    @DisplayName("should handle empty list of exercises")
    void shouldHandleEmptyListOfExercises() {
      // Prepare
      when(exerciseRepository.thatMustBeFinished()).thenReturn(Collections.emptyList());
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.INJECT_CHAINING))
          .thenReturn(false);
      when(exerciseRepository.saveAll(anyList())).thenReturn(Collections.emptyList());

      // Act
      injectsExecutionJob.handleAutoClosingExercises();

      // Assert
      verify(exerciseRepository).saveAll(exercisesCaptor.capture());
      assertTrue(exercisesCaptor.getValue().isEmpty());
      verify(securityCoverageSendJobService)
          .createOrUpdateCoverageSendJobForSimulationsIfReady(Collections.emptyList());
      verify(notificationEventService, never()).sendNotificationEventWithDelay(any(), anyLong());
    }

    @Test
    @DisplayName("should filter all exercises when all are chaining exercises")
    void shouldFilterAllExercisesWhenAllAreChaining() {
      // Prepare
      String exerciseId1 = UUID.randomUUID().toString();
      String exerciseId2 = UUID.randomUUID().toString();

      Exercise exercise1 = createMockExercise(exerciseId1, null);
      Exercise exercise2 = createMockExercise(exerciseId2, null);
      List<Exercise> exercises = new ArrayList<>(List.of(exercise1, exercise2));

      when(exerciseRepository.thatMustBeFinished()).thenReturn(exercises);
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.INJECT_CHAINING)).thenReturn(true);
      when(workflowService.isExerciseChaining(exerciseId1)).thenReturn(true);
      when(workflowService.isExerciseChaining(exerciseId2)).thenReturn(true);
      when(exerciseRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

      // Act
      injectsExecutionJob.handleAutoClosingExercises();

      // Assert
      verify(exerciseRepository).saveAll(exercisesCaptor.capture());
      assertTrue(exercisesCaptor.getValue().isEmpty());
    }

    @Test
    @DisplayName("should send multiple notifications for multiple exercises with scenarios")
    void shouldSendMultipleNotificationsForMultipleExercisesWithScenarios() {
      // Prepare
      Scenario scenario1 = mock(Scenario.class);
      when(scenario1.getId()).thenReturn(UUID.randomUUID().toString());

      Scenario scenario2 = mock(Scenario.class);
      when(scenario2.getId()).thenReturn(UUID.randomUUID().toString());

      Exercise exercise1 = createMockExercise(UUID.randomUUID().toString(), scenario1);
      Exercise exercise2 = createMockExercise(UUID.randomUUID().toString(), scenario2);
      List<Exercise> exercises = new ArrayList<>(List.of(exercise1, exercise2));

      when(exerciseRepository.thatMustBeFinished()).thenReturn(exercises);
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.INJECT_CHAINING))
          .thenReturn(false);
      when(exerciseRepository.saveAll(anyList())).thenReturn(exercises);

      // Act
      injectsExecutionJob.handleAutoClosingExercises();

      // Assert
      verify(notificationEventService, times(2)).sendNotificationEventWithDelay(any(), anyLong());
    }
  }
}
