package io.openaev.scheduler.jobs;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.quality.Strictness.LENIENT;

import io.openaev.database.model.*;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.InjectDependenciesRepository;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.notification.model.NotificationEvent;
import io.openaev.notification.model.NotificationEventType;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.scheduler.jobs.exception.ErrorMessagesPreExecutionException;
import io.openaev.service.NotificationEventService;
import io.openaev.service.PreviewFeatureService;
import io.openaev.service.SecurityCoverageSendJobService;
import io.openaev.service.chaining.WorkflowService;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.composers.InjectComposer;
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
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
@DisplayName("InjectsExecutionJob Unit Tests")
class InjectsExecutionJobUnitTest {

  @Mock private InjectDependenciesRepository injectDependenciesRepository;

  @Mock private ExerciseRepository exerciseRepository;
  @Mock private PreviewFeatureService previewFeatureService;
  @Mock private WorkflowService workflowService;
  @Mock private SecurityCoverageSendJobService securityCoverageSendJobService;
  @Mock private NotificationEventService notificationEventService;
  @Mock private InjectExpectationRepository injectExpectationRepository;

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
  // Malicious extensions
  // ========================================================================
  @Nested
  @DisplayName("handleMaliciousExpectationsTests")
  // Because we use the inject composer in this test, we need to use the spring context, despite it
  // being super slow
  // Which is why this test is isolated in it's own nested class
  @SpringBootTest
  @Transactional
  class handleMaliciousExpectationsTests {

    @Autowired private InjectComposer injectComposer;

    @BeforeEach
    void initMocks() {
      // As we are using the spring extension, we need to manually enable the mocks from the parent
      // class
      MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName(
        "When auto closing of NON stix-created simulation, DOES NOT trigger stix coverage job")
    public void shouldRaiseExceptionIfExpectationMalicious() {
      Inject inject = injectComposer.forInject(InjectFixture.getDefaultInject()).get();
      inject.setId(UUID.randomUUID().toString());
      InjectDependency injectDependency = new InjectDependency();
      injectDependency
          .getCompositeId()
          .setInjectParent(
              InjectFixture.createInjectWithManualExpectation(
                  InjectorContractFixture.createDefaultInjectorContract(),
                  "parent",
                  "T(java.lang.Runtime).getRuntime().exec('gedit');"));
      injectDependency.getCompositeId().setInjectChildren(InjectFixture.getDefaultInject());
      injectDependency.setInjectDependencyCondition(
          new InjectDependencyConditions.InjectDependencyCondition());
      InjectDependencyConditions.Condition condition = new InjectDependencyConditions.Condition();
      condition.setOperator(InjectDependencyConditions.DependencyOperator.eq);
      condition.setValue(true);
      condition.setKey("T(java.lang.Runtime).getRuntime().exec('gedit');");
      injectDependency.getInjectDependencyCondition().setConditions(List.of(condition));
      when(injectDependenciesRepository.findParents(any())).thenReturn(List.of(injectDependency));
      try {
        injectsExecutionJob.checkErrorMessagesPreExecution(UUID.randomUUID().toString(), inject);
        fail("Should have raised an exception");
      } catch (Exception e) {
        assertThat(e).isInstanceOf(ErrorMessagesPreExecutionException.class);
        assertThat(e.getMessage())
            .isEqualTo("There was an error during the evaluation of the condition of the inject");
      }
    }
  }

  // ========================================================================
  // Auto closing of simulations
  // ========================================================================
  @Nested
  @DisplayName("handleAutoClosingSimulations")
  class HandleAutoClosingSimulationsTests {

    @Captor private ArgumentCaptor<List<Exercise>> simulationCaptor;

    @Captor private ArgumentCaptor<NotificationEvent> notificationEventCaptor;

    private Exercise createMockSimulation(String id, Scenario scenario) {
      Exercise simulation = mock(Exercise.class, withSettings().strictness(LENIENT));
      when(simulation.getId()).thenReturn(id);
      when(simulation.getScenario()).thenReturn(scenario);
      return simulation;
    }

    @Test
    @DisplayName("should finish simuations and update their status")
    void shouldFinishSimulationsAndUpdateStatus() {
      // Prepare
      String simulationId = UUID.randomUUID().toString();
      Exercise simulation = createMockSimulation(simulationId, null);
      List<Exercise> simulations = new ArrayList<>(List.of(simulation));

      when(exerciseRepository.thatMustBeFinished()).thenReturn(simulations);
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.INJECT_CHAINING))
          .thenReturn(false);
      when(exerciseRepository.saveAll(anyList())).thenReturn(simulations);

      // Act
      injectsExecutionJob.handleAutoClosingSimulations();

      // Assert
      verify(simulation).setStatus(ExerciseStatus.FINISHED);
      verify(simulation).setEnd(any(Instant.class));
      verify(simulation).setUpdatedAt(any(Instant.class));
      verify(exerciseRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("should filter out chaining simulations when feature is enabled")
    void shouldFilterOutChainingSimulationsWhenFeatureEnabled() {
      // Prepare
      String chainingSimulationId = UUID.randomUUID().toString();
      String normalSimulationId = UUID.randomUUID().toString();

      Exercise chainingSimulation = createMockSimulation(chainingSimulationId, null);
      Exercise normalSimulation = createMockSimulation(normalSimulationId, null);
      List<Exercise> simulations = new ArrayList<>(List.of(chainingSimulation, normalSimulation));

      when(exerciseRepository.thatMustBeFinished()).thenReturn(simulations);
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.INJECT_CHAINING)).thenReturn(true);
      when(workflowService.isSimulationChaining(chainingSimulationId)).thenReturn(true);
      when(workflowService.isSimulationChaining(normalSimulationId)).thenReturn(false);
      when(exerciseRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

      // Act
      injectsExecutionJob.handleAutoClosingSimulations();

      // Assert
      verify(exerciseRepository).saveAll(simulationCaptor.capture());
      List<Exercise> savedSimulations = simulationCaptor.getValue();
      assertEquals(1, savedSimulations.size());
      assertEquals(normalSimulationId, savedSimulations.getFirst().getId());
    }

    @Test
    @DisplayName("should not filter simulations when chaining feature is disabled")
    void shouldNotFilterSimulationsWhenChainingFeatureDisabled() {
      // Prepare
      String simulationId1 = UUID.randomUUID().toString();
      String simulationId2 = UUID.randomUUID().toString();

      Exercise simulation1 = createMockSimulation(simulationId1, null);
      Exercise simulation2 = createMockSimulation(simulationId2, null);
      List<Exercise> simulations = new ArrayList<>(List.of(simulation1, simulation2));

      when(exerciseRepository.thatMustBeFinished()).thenReturn(simulations);
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.INJECT_CHAINING))
          .thenReturn(false);
      when(exerciseRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

      // Act
      injectsExecutionJob.handleAutoClosingSimulations();

      // Assert
      verify(workflowService, never()).isSimulationChaining(anyString());
      verify(exerciseRepository).saveAll(simulationCaptor.capture());
      assertEquals(2, simulationCaptor.getValue().size());
    }

    @Test
    @DisplayName("should trigger coverage send job for finished simulations")
    void shouldTriggerCoverageSendJob() {
      // Prepare
      Exercise simulation = createMockSimulation(UUID.randomUUID().toString(), null);
      List<Exercise> simulations = new ArrayList<>(List.of(simulation));

      when(exerciseRepository.thatMustBeFinished()).thenReturn(simulations);
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.INJECT_CHAINING))
          .thenReturn(false);
      when(exerciseRepository.saveAll(anyList())).thenReturn(simulations);

      // Act
      injectsExecutionJob.handleAutoClosingSimulations();

      // Assert
      verify(securityCoverageSendJobService)
          .createOrUpdateCoverageSendJobForSimulationsIfReady(simulations);
    }

    @Test
    @DisplayName("should send notification for simulations with scenario")
    void shouldSendNotificationForSimulationsWithScenario() {
      // Prepare
      String scenarioId = UUID.randomUUID().toString();
      Scenario scenario = mock(Scenario.class);
      when(scenario.getId()).thenReturn(scenarioId);

      Exercise simulation = createMockSimulation(UUID.randomUUID().toString(), scenario);
      List<Exercise> simulations = new ArrayList<>(List.of(simulation));

      when(exerciseRepository.thatMustBeFinished()).thenReturn(simulations);
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.INJECT_CHAINING))
          .thenReturn(false);
      when(exerciseRepository.saveAll(anyList())).thenReturn(simulations);

      // Act
      injectsExecutionJob.handleAutoClosingSimulations();

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
    @DisplayName("should not send notification for simulations without scenario")
    void shouldNotSendNotificationForSimulationsWithoutScenario() {
      // Prepare
      Exercise simulation = createMockSimulation(UUID.randomUUID().toString(), null);
      List<Exercise> simulations = new ArrayList<>(List.of(simulation));

      when(exerciseRepository.thatMustBeFinished()).thenReturn(simulations);
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.INJECT_CHAINING))
          .thenReturn(false);
      when(exerciseRepository.saveAll(anyList())).thenReturn(simulations);

      // Act
      injectsExecutionJob.handleAutoClosingSimulations();

      // Assert
      verify(notificationEventService, never()).sendNotificationEventWithDelay(any(), anyLong());
    }

    @Test
    @DisplayName("should send notifications only for simulations with scenario in mixed list")
    void shouldSendNotificationsOnlyForSimulationsWithScenarioInMixedList() {
      // Prepare
      String scenarioId = UUID.randomUUID().toString();
      Scenario scenario = mock(Scenario.class);
      when(scenario.getId()).thenReturn(scenarioId);

      Exercise simulationWithScenario =
          createMockSimulation(UUID.randomUUID().toString(), scenario);
      Exercise simulationWithoutScenario = createMockSimulation(UUID.randomUUID().toString(), null);
      List<Exercise> simulations =
          new ArrayList<>(List.of(simulationWithScenario, simulationWithoutScenario));

      when(exerciseRepository.thatMustBeFinished()).thenReturn(simulations);
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.INJECT_CHAINING))
          .thenReturn(false);
      when(exerciseRepository.saveAll(anyList())).thenReturn(simulations);

      // Act
      injectsExecutionJob.handleAutoClosingSimulations();

      // Assert
      verify(notificationEventService, times(1)).sendNotificationEventWithDelay(any(), anyLong());
    }

    @Test
    @DisplayName("should handle empty list of simulations")
    void shouldHandleEmptyListOfSimulations() {
      // Prepare
      when(exerciseRepository.thatMustBeFinished()).thenReturn(Collections.emptyList());
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.INJECT_CHAINING))
          .thenReturn(false);
      when(exerciseRepository.saveAll(anyList())).thenReturn(Collections.emptyList());

      // Act
      injectsExecutionJob.handleAutoClosingSimulations();

      // Assert
      verify(exerciseRepository).saveAll(simulationCaptor.capture());
      assertTrue(simulationCaptor.getValue().isEmpty());
      verify(securityCoverageSendJobService)
          .createOrUpdateCoverageSendJobForSimulationsIfReady(Collections.emptyList());
      verify(notificationEventService, never()).sendNotificationEventWithDelay(any(), anyLong());
    }

    @Test
    @DisplayName("should filter all simulations when all are chaining simulations")
    void shouldFilterAllSimulationsWhenAllAreChaining() {
      // Prepare
      String simulationId1 = UUID.randomUUID().toString();
      String simulationId2 = UUID.randomUUID().toString();

      Exercise simulation1 = createMockSimulation(simulationId1, null);
      Exercise simulation2 = createMockSimulation(simulationId2, null);
      List<Exercise> simulations = new ArrayList<>(List.of(simulation1, simulation2));

      when(exerciseRepository.thatMustBeFinished()).thenReturn(simulations);
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.INJECT_CHAINING)).thenReturn(true);
      when(workflowService.isSimulationChaining(simulationId1)).thenReturn(true);
      when(workflowService.isSimulationChaining(simulationId2)).thenReturn(true);
      when(exerciseRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

      // Act
      injectsExecutionJob.handleAutoClosingSimulations();

      // Assert
      verify(exerciseRepository).saveAll(simulationCaptor.capture());
      assertTrue(simulationCaptor.getValue().isEmpty());
    }

    @Test
    @DisplayName("should send multiple notifications for multiple simulations with scenarios")
    void shouldSendMultipleNotificationsForMultipleSimulationsWithScenarios() {
      // Prepare
      Scenario scenario1 = mock(Scenario.class);
      when(scenario1.getId()).thenReturn(UUID.randomUUID().toString());

      Scenario scenario2 = mock(Scenario.class);
      when(scenario2.getId()).thenReturn(UUID.randomUUID().toString());

      Exercise simulation1 = createMockSimulation(UUID.randomUUID().toString(), scenario1);
      Exercise simulation2 = createMockSimulation(UUID.randomUUID().toString(), scenario2);
      List<Exercise> simulations = new ArrayList<>(List.of(simulation1, simulation2));

      when(exerciseRepository.thatMustBeFinished()).thenReturn(simulations);
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.INJECT_CHAINING))
          .thenReturn(false);
      when(exerciseRepository.saveAll(anyList())).thenReturn(simulations);

      // Act
      injectsExecutionJob.handleAutoClosingSimulations();

      // Assert
      verify(notificationEventService, times(2)).sendNotificationEventWithDelay(any(), anyLong());
    }
  }
}
