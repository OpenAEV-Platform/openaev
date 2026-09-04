package io.openaev.scheduler.jobs;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.quality.Strictness.LENIENT;

import io.openaev.database.model.*;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.helper.InjectHelper;
import io.openaev.notification.model.NotificationEvent;
import io.openaev.notification.model.NotificationEventType;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.inject.service.InjectStatusService;
import io.openaev.scheduler.TenantScopedJobRunner;
import io.openaev.service.NotificationEventService;
import io.openaev.service.SecurityCoverageSendJobService;
import io.openaev.service.chaining.WorkflowService;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("InjectsFinalizationJob Unit Tests")
class InjectsFinalizationJobUnitTest {

  @Mock private InjectHelper injectHelper;
  @Mock private InjectService injectService;
  @Mock private InjectStatusService injectStatusService;
  @Mock private ExerciseRepository exerciseRepository;
  @Mock private WorkflowService workflowService;
  @Mock private SecurityCoverageSendJobService securityCoverageSendJobService;
  @Mock private NotificationEventService notificationEventService;
  @Mock private EntityManager entityManager;
  @Mock private TenantScopedJobRunner tenantScopedJobRunner;

  @InjectMocks private InjectsFinalizationJob finalizationJob;

  @BeforeEach
  void setUp() {
    reset(
        exerciseRepository,
        workflowService,
        securityCoverageSendJobService,
        notificationEventService);
  }

  @Nested
  @DisplayName("handleAutoClosingSimulations")
  class HandleAutoClosingSimulationsTests {

    @Captor private ArgumentCaptor<List<Exercise>> simulationCaptor;

    @Captor private ArgumentCaptor<NotificationEvent> notificationEventCaptor;

    private Exercise createMockSimulation(String id, Scenario scenario) {
      Exercise simulation = mock(Exercise.class, withSettings().strictness(LENIENT));
      Tenant tenant = mock(Tenant.class, withSettings().strictness(LENIENT));
      when(tenant.getId()).thenReturn("tenant-test");
      when(simulation.getId()).thenReturn(id);
      when(simulation.getScenario()).thenReturn(scenario);
      when(simulation.getTenant()).thenReturn(tenant);
      return simulation;
    }

    private void mockFindAllByIdFrom(List<Exercise> simulations) {
      when(exerciseRepository.findAllById(anyIterable()))
          .thenAnswer(
              invocation -> {
                Iterable<String> ids = invocation.getArgument(0);
                List<String> idList = new ArrayList<>();
                ids.forEach(idList::add);
                return simulations.stream().filter(s -> idList.contains(s.getId())).toList();
              });
    }

    private void mockTenantRunnerInline() {
      doAnswer(
              invocation -> {
                Runnable work = invocation.getArgument(1);
                work.run();
                return null;
              })
          .when(tenantScopedJobRunner)
          .runInTenant(any(), any(Runnable.class));
    }

    @Test
    @DisplayName("should finish simuations and update their status")
    void shouldFinishSimulationsAndUpdateStatus() {
      // Prepare
      mockTenantRunnerInline();
      String simulationId = UUID.randomUUID().toString();
      Exercise simulation = createMockSimulation(simulationId, null);
      List<Exercise> simulations = new ArrayList<>(List.of(simulation));

      when(exerciseRepository.thatMustBeFinished()).thenReturn(simulations);
      mockFindAllByIdFrom(simulations);
      when(exerciseRepository.saveAll(anyList())).thenReturn(simulations);

      // Act
      finalizationJob.handleAutoClosingSimulations();

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
      mockTenantRunnerInline();
      String chainingSimulationId = UUID.randomUUID().toString();
      String normalSimulationId = UUID.randomUUID().toString();

      Exercise chainingSimulation = createMockSimulation(chainingSimulationId, null);
      Exercise normalSimulation = createMockSimulation(normalSimulationId, null);
      List<Exercise> simulations = new ArrayList<>(List.of(chainingSimulation, normalSimulation));

      when(exerciseRepository.thatMustBeFinished()).thenReturn(simulations);
      when(workflowService.existsBySimulationId(chainingSimulationId)).thenReturn(true);
      when(workflowService.existsBySimulationId(normalSimulationId)).thenReturn(false);
      mockFindAllByIdFrom(simulations);
      when(exerciseRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

      // Act
      finalizationJob.handleAutoClosingSimulations();

      // Assert
      verify(exerciseRepository).saveAll(simulationCaptor.capture());
      List<Exercise> savedSimulations = simulationCaptor.getValue();
      assertEquals(1, savedSimulations.size());
      assertEquals(normalSimulationId, savedSimulations.getFirst().getId());
    }

    @Test
    @DisplayName("should keep non-chaining simulations")
    void shouldKeepNonChainingSimulations() {
      // Prepare
      mockTenantRunnerInline();
      String simulationId1 = UUID.randomUUID().toString();
      String simulationId2 = UUID.randomUUID().toString();

      Exercise simulation1 = createMockSimulation(simulationId1, null);
      Exercise simulation2 = createMockSimulation(simulationId2, null);
      List<Exercise> simulations = new ArrayList<>(List.of(simulation1, simulation2));

      when(exerciseRepository.thatMustBeFinished()).thenReturn(simulations);
      mockFindAllByIdFrom(simulations);
      when(exerciseRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

      // Act
      finalizationJob.handleAutoClosingSimulations();

      // Assert
      verify(workflowService, times(2)).existsBySimulationId(anyString());
      verify(exerciseRepository).saveAll(simulationCaptor.capture());
      assertEquals(2, simulationCaptor.getValue().size());
    }

    @Test
    @DisplayName("should trigger coverage send job for finished simulations")
    void shouldTriggerCoverageSendJob() {
      // Prepare
      mockTenantRunnerInline();
      Exercise simulation = createMockSimulation(UUID.randomUUID().toString(), null);
      List<Exercise> simulations = new ArrayList<>(List.of(simulation));

      when(exerciseRepository.thatMustBeFinished()).thenReturn(simulations);
      mockFindAllByIdFrom(simulations);
      when(exerciseRepository.saveAll(anyList())).thenReturn(simulations);

      // Act
      finalizationJob.handleAutoClosingSimulations();

      // Assert
      verify(securityCoverageSendJobService)
          .createOrUpdateCoverageSendJobForSimulationsIfReady(simulations);
    }

    @Test
    @DisplayName("should send notification for simulations with scenario")
    void shouldSendNotificationForSimulationsWithScenario() {
      // Prepare
      mockTenantRunnerInline();
      String scenarioId = UUID.randomUUID().toString();
      Scenario scenario = mock(Scenario.class);
      when(scenario.getId()).thenReturn(scenarioId);

      Exercise simulation = createMockSimulation(UUID.randomUUID().toString(), scenario);
      List<Exercise> simulations = new ArrayList<>(List.of(simulation));

      when(exerciseRepository.thatMustBeFinished()).thenReturn(simulations);
      mockFindAllByIdFrom(simulations);
      when(exerciseRepository.saveAll(anyList())).thenReturn(simulations);

      // Act
      finalizationJob.handleAutoClosingSimulations();

      // Assert
      verify(notificationEventService)
          .sendNotificationEventWithDelay(notificationEventCaptor.capture(), any(Long.class));

      NotificationEvent event = notificationEventCaptor.getValue();
      assertEquals(NotificationEventType.SIMULATION_COMPLETED, event.getEventType());
      assertEquals(ResourceType.SCENARIO, event.getResourceType());
      assertEquals(scenarioId, event.getResourceId());
      assertNotNull(event.getTimestamp());
    }

    @Test
    @DisplayName("should not send notification for simulations without scenario")
    void shouldNotSendNotificationForSimulationsWithoutScenario() {
      // Prepare
      mockTenantRunnerInline();
      Exercise simulation = createMockSimulation(UUID.randomUUID().toString(), null);
      List<Exercise> simulations = new ArrayList<>(List.of(simulation));

      when(exerciseRepository.thatMustBeFinished()).thenReturn(simulations);
      mockFindAllByIdFrom(simulations);
      when(exerciseRepository.saveAll(anyList())).thenReturn(simulations);

      // Act
      finalizationJob.handleAutoClosingSimulations();

      // Assert
      verify(notificationEventService, never()).sendNotificationEventWithDelay(any(), anyLong());
    }

    @Test
    @DisplayName("should send notifications only for simulations with scenario in mixed list")
    void shouldSendNotificationsOnlyForSimulationsWithScenarioInMixedList() {
      // Prepare
      mockTenantRunnerInline();
      String scenarioId = UUID.randomUUID().toString();
      Scenario scenario = mock(Scenario.class);
      when(scenario.getId()).thenReturn(scenarioId);

      Exercise simulationWithScenario =
          createMockSimulation(UUID.randomUUID().toString(), scenario);
      Exercise simulationWithoutScenario = createMockSimulation(UUID.randomUUID().toString(), null);
      List<Exercise> simulations =
          new ArrayList<>(List.of(simulationWithScenario, simulationWithoutScenario));

      when(exerciseRepository.thatMustBeFinished()).thenReturn(simulations);
      mockFindAllByIdFrom(simulations);
      when(exerciseRepository.saveAll(anyList())).thenReturn(simulations);

      // Act
      finalizationJob.handleAutoClosingSimulations();

      // Assert
      verify(notificationEventService, times(1)).sendNotificationEventWithDelay(any(), anyLong());
    }

    @Test
    @DisplayName("should handle empty list of simulations")
    void shouldHandleEmptyListOfSimulations() {
      // Prepare
      when(exerciseRepository.thatMustBeFinished()).thenReturn(Collections.emptyList());

      // Act
      finalizationJob.handleAutoClosingSimulations();

      // Assert
      verify(exerciseRepository, never()).saveAll(anyList());
      verify(securityCoverageSendJobService, never())
          .createOrUpdateCoverageSendJobForSimulationsIfReady(anyList());
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
      when(workflowService.existsBySimulationId(simulationId1)).thenReturn(true);
      when(workflowService.existsBySimulationId(simulationId2)).thenReturn(true);

      // Act
      finalizationJob.handleAutoClosingSimulations();

      // Assert
      verify(exerciseRepository, never()).saveAll(anyList());
      verify(securityCoverageSendJobService, never())
          .createOrUpdateCoverageSendJobForSimulationsIfReady(anyList());
    }

    @Test
    @DisplayName("should send multiple notifications for multiple simulations with scenarios")
    void shouldSendMultipleNotificationsForMultipleSimulationsWithScenarios() {
      // Prepare
      mockTenantRunnerInline();
      Scenario scenario1 = mock(Scenario.class);
      when(scenario1.getId()).thenReturn(UUID.randomUUID().toString());

      Scenario scenario2 = mock(Scenario.class);
      when(scenario2.getId()).thenReturn(UUID.randomUUID().toString());

      Exercise simulation1 = createMockSimulation(UUID.randomUUID().toString(), scenario1);
      Exercise simulation2 = createMockSimulation(UUID.randomUUID().toString(), scenario2);
      List<Exercise> simulations = new ArrayList<>(List.of(simulation1, simulation2));

      when(exerciseRepository.thatMustBeFinished()).thenReturn(simulations);
      mockFindAllByIdFrom(simulations);
      when(exerciseRepository.saveAll(anyList())).thenReturn(simulations);

      // Act
      finalizationJob.handleAutoClosingSimulations();

      // Assert
      verify(notificationEventService, times(2)).sendNotificationEventWithDelay(any(), anyLong());
    }
  }

  // ========================================================================
  // Inject expectation collect status
  // ========================================================================
  @Nested
  @DisplayName("handleInjectExpectationCollectStatus")
  class HandleInjectExpectationCollectStatusTests {

    @Captor private ArgumentCaptor<List<Inject>> injectsCaptor;

    private Inject inject;

    @BeforeEach
    void setUpCollectStatus() {
      Session session = mock(Session.class, withSettings().strictness(LENIENT));
      when(entityManager.unwrap(Session.class)).thenReturn(session);
      inject = new Inject();
      when(injectService.getExecutedAndNotFinished()).thenReturn(List.of(inject));
    }

    private BaseInjectExpectation buildExpectation(
        Instant createdAt, long expirationSeconds, String... resultTexts) {
      BaseInjectExpectation expectation = new BaseInjectExpectation();
      expectation.setCreatedAt(createdAt);
      expectation.setExpirationTime(expirationSeconds);
      expectation.setResults(
          Arrays.stream(resultTexts)
              .map(text -> InjectExpectationResult.builder().result(text).build())
              .collect(Collectors.toCollection(ArrayList::new)));
      return expectation;
    }

    private List<Inject> actAndCaptureSaved() {
      finalizationJob.handleInjectExpectationCollectStatus();
      verify(injectService).saveAll(injectsCaptor.capture());
      return injectsCaptor.getValue();
    }

    @Test
    @DisplayName("should complete collect status when the inject has no expectations")
    void shouldCompleteWhenNoExpectations() {
      List<Inject> saved = actAndCaptureSaved();

      assertEquals(1, saved.size());
      assertEquals(CollectExecutionStatus.COMPLETED, saved.get(0).getCollectExecutionStatus());
    }

    @Test
    @DisplayName("should complete collect status when every expectation result is filled")
    void shouldCompleteWhenAllResultsFilled() {
      inject
          .getExpectations()
          .add(buildExpectation(Instant.now(), 3600, "Prevented", "Not Prevented"));

      List<Inject> saved = actAndCaptureSaved();

      assertEquals(1, saved.size());
    }

    @Test
    @DisplayName("should keep collecting while an unexpired expectation has an unfilled result")
    void shouldKeepCollectingWhileUnexpiredResultUnfilled() {
      // One collector reported, the other placeholder is still empty and the window is open
      inject.getExpectations().add(buildExpectation(Instant.now(), 3600, "Prevented", ""));

      List<Inject> saved = actAndCaptureSaved();

      assertTrue(saved.isEmpty());
      assertEquals(CollectExecutionStatus.COLLECTING, inject.getCollectExecutionStatus());
    }

    @Test
    @DisplayName(
        "should complete collect status when an expectation with unfilled results has expired "
            + "(prevents simulations from staying on-going forever)")
    void shouldCompleteWhenExpectationExpiredDespiteUnfilledResults() {
      // Partially filled expectation: score was set by the reporting collector, so the
      // expiration manager never back-fills the empty placeholder. Once the collection
      // window is over the inject must stop blocking the simulation auto-close.
      inject
          .getExpectations()
          .add(buildExpectation(Instant.now().minusSeconds(7200), 3600, "Prevented", ""));

      List<Inject> saved = actAndCaptureSaved();

      assertEquals(1, saved.size());
      assertEquals(CollectExecutionStatus.COMPLETED, saved.get(0).getCollectExecutionStatus());
    }
  }
}
