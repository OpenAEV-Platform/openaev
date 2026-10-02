package io.openaev.notification.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.NotificationTriggerEventType;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tenant;
import io.openaev.notification.engine.NotificationEngineService;
import io.openaev.notification.engine.NotificationResourceCatalog;
import io.openaev.notification.model.NotificationEvent;
import io.openaev.notification.model.NotificationEventType;
import io.openaev.rest.exercise.service.ExerciseService;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * The handler detects the degradation inside a tenant-scoped transaction, then calls the engine
 * OUTSIDE of it: {@code NotificationEngineService} opens its own scoped transaction per trigger and
 * {@code TenantScopedTransaction.execute()} refuses to run inside an active one.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Scenario score degradation notification handler")
class ScenarioNotificationEventHandlerTest {

  private static final String SCENARIO_ID = "scenario-1";
  private static final String TENANT_ID = "tenant-1";
  private static final Instant EVENT_AT = Instant.parse("2026-01-02T00:00:00Z");
  private static final Instant LAST_END = Instant.parse("2026-01-01T12:00:00Z");
  private static final Instant PREVIOUS_END = Instant.parse("2025-12-31T12:00:00Z");

  @Mock private EntityManager entityManager;
  @Mock private Session session;
  @Mock private ExerciseService exerciseService;
  @Mock private NotificationEngineService notificationEngineService;
  @Mock private TenantScopedTransaction tenantTx;

  @Captor private ArgumentCaptor<TxCtx> txCtxCaptor;

  private ScenarioNotificationEventHandler handler;

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    when(entityManager.unwrap(Session.class)).thenReturn(session);
    handler =
        new ScenarioNotificationEventHandler(
            entityManager, exerciseService, notificationEngineService, tenantTx);
    // Run the detection phase inline so its outcome can be asserted.
    when(tenantTx.execute(any(TxCtx.class), any(Supplier.class)))
        .thenAnswer(invocation -> invocation.getArgument(1, Supplier.class).get());
  }

  private NotificationEvent simulationCompleted() {
    return NotificationEvent.builder()
        .eventType(NotificationEventType.SIMULATION_COMPLETED)
        .resourceId(SCENARIO_ID)
        .timestamp(EVENT_AT)
        .build();
  }

  private Exercise simulation(String id, Instant end, Scenario scenario) {
    Exercise exercise = mock(Exercise.class);
    when(exercise.getId()).thenReturn(id);
    when(exercise.getEnd()).thenReturn(Optional.ofNullable(end));
    when(exercise.getScenario()).thenReturn(scenario);
    return exercise;
  }

  private Scenario scenarioWithTenant(String tenantId) {
    Scenario scenario = mock(Scenario.class);
    when(scenario.getId()).thenReturn(SCENARIO_ID);
    when(scenario.getName()).thenReturn("Scenario under test");
    when(scenario.getTenant()).thenReturn(tenantId == null ? null : new Tenant(tenantId));
    return scenario;
  }

  private void givenTwoFinishedSimulations(Scenario scenario) {
    // Mocks must be fully built BEFORE being handed to thenReturn(): creating a mock inside a
    // stubbing expression is what Mockito reports as "unfinished stubbing".
    Exercise last = simulation("sim-last", LAST_END, scenario);
    Exercise previous = simulation("sim-previous", PREVIOUS_END, scenario);
    when(exerciseService.previousFinishedSimulation(eq(SCENARIO_ID), eq(EVENT_AT)))
        .thenReturn(last);
    when(exerciseService.previousFinishedSimulation(eq(SCENARIO_ID), eq(LAST_END)))
        .thenReturn(previous);
    when(exerciseService.getGlobalResults(anyString())).thenReturn(List.of());
  }

  @Nested
  @DisplayName("When a score degradation is detected")
  class WhenDegradationDetected {

    @Test
    @DisplayName("given a degradation, should detect in a scope then dispatch outside it")
    void given_degradation_should_detectInScopeThenDispatchOutside() {
      // -- ARRANGE --
      givenTwoFinishedSimulations(scenarioWithTenant(TENANT_ID));
      when(exerciseService.isThereAScoreDegradation(anyMap(), anyMap())).thenReturn(true);

      // -- ACT --
      handler.handle(simulationCompleted());

      // -- ASSERT --
      // Detection is cross-tenant: the scenario's tenant is only known once it has been read.
      verify(tenantTx).execute(txCtxCaptor.capture(), any(Supplier.class));
      assertEquals(TxCtx.allTenants(), txCtxCaptor.getValue());
      verify(notificationEngineService)
          .handleEventWithMessage(
              eq(NotificationResourceCatalog.SCENARIO),
              eq(SCENARIO_ID),
              eq(TENANT_ID),
              eq(NotificationTriggerEventType.SCORE_DEGRADATION),
              anyString());
    }

    @Test
    @DisplayName("given a degradation, should never nest the engine call in a transaction")
    void given_degradation_should_neverNestEngineCallInTransaction() {
      // -- ARRANGE --
      // Regression guard: wrapping the engine call in executeNew() made it fail with
      // "TenantScopedTransaction.execute() refuses to open inside an active transaction".
      givenTwoFinishedSimulations(scenarioWithTenant(TENANT_ID));
      when(exerciseService.isThereAScoreDegradation(anyMap(), anyMap())).thenReturn(true);

      // -- ACT --
      handler.handle(simulationCompleted());

      // -- ASSERT --
      verify(tenantTx, never()).executeNew(any(TxCtx.class), any(Runnable.class));
      verify(tenantTx, never()).executeNew(any(TxCtx.class), any(Supplier.class));
    }

    @Test
    @DisplayName("given a scenario without tenant, should not dispatch at all")
    void given_scenarioWithoutTenant_should_notDispatch() {
      // -- ARRANGE --
      // The engine drops tenant-less events fail-closed anyway.
      givenTwoFinishedSimulations(scenarioWithTenant(null));
      when(exerciseService.isThereAScoreDegradation(anyMap(), anyMap())).thenReturn(true);

      // -- ACT --
      handler.handle(simulationCompleted());

      // -- ASSERT --
      verify(notificationEngineService, never())
          .handleEventWithMessage(any(), anyString(), anyString(), any(), anyString());
    }
  }

  @Nested
  @DisplayName("When no score degradation occurred")
  class WhenNoDegradation {

    @Test
    @DisplayName("given no degradation, should not reach the engine")
    void given_noDegradation_should_notReachEngine() {
      // -- ARRANGE --
      givenTwoFinishedSimulations(scenarioWithTenant(TENANT_ID));
      when(exerciseService.isThereAScoreDegradation(anyMap(), anyMap())).thenReturn(false);

      // -- ACT --
      handler.handle(simulationCompleted());

      // -- ASSERT --
      verify(notificationEngineService, never())
          .handleEventWithMessage(any(), anyString(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("given a single finished simulation, should return before comparing")
    void given_singleFinishedSimulation_should_returnEarly() {
      // -- ARRANGE --
      Scenario scenario = scenarioWithTenant(TENANT_ID);
      Exercise last = simulation("sim-last", LAST_END, scenario);
      when(exerciseService.previousFinishedSimulation(eq(SCENARIO_ID), eq(EVENT_AT)))
          .thenReturn(last);
      when(exerciseService.previousFinishedSimulation(eq(SCENARIO_ID), eq(LAST_END)))
          .thenReturn(null);

      // -- ACT --
      handler.handle(simulationCompleted());

      // -- ASSERT --
      verify(exerciseService, never()).isThereAScoreDegradation(anyMap(), anyMap());
      verify(notificationEngineService, never())
          .handleEventWithMessage(any(), anyString(), anyString(), any(), anyString());
    }
  }
}
