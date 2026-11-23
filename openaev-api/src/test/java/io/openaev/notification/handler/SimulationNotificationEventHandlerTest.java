package io.openaev.notification.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.NotificationTriggerEventType;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.Tenant;
import io.openaev.notification.engine.NotificationEngineService;
import io.openaev.notification.engine.NotificationResourceCatalog;
import io.openaev.notification.model.NotificationEvent;
import io.openaev.notification.model.NotificationEventType;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.service.attackpath.AttackPathGraphService;
import io.openaev.service.attackpath.dto.AttackPathDTO;
import io.openaev.service.attackpath.dto.AttackPathNodeDTO;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SimulationNotificationEventHandlerTest {

  @Mock private ExerciseService exerciseService;
  @Mock private AttackPathGraphService attackPathGraphService;
  @Mock private NotificationEngineService notificationEngineService;
  @Mock private TenantScopedTransaction tenantTx;
  @InjectMocks private SimulationNotificationEventHandler handler;

  private Exercise simulation;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    when(tenantTx.execute(any(), any(Supplier.class)))
        .thenAnswer(invocation -> ((Supplier<Object>) invocation.getArgument(1)).get());
    Tenant tenant = new Tenant();
    tenant.setId("tenant-1");
    simulation = new Exercise();
    simulation.setId("sim-1");
    simulation.setName("Sim");
    simulation.setTenant(tenant);
    when(exerciseService.findByIdInScope(any(), eq("sim-1"))).thenReturn(Optional.of(simulation));
  }

  private void listening(boolean listening) {
    when(notificationEngineService.hasLiveTrigger(
            NotificationResourceCatalog.SIMULATION,
            "tenant-1",
            NotificationTriggerEventType.CRITICAL_ASSET_REACHED))
        .thenReturn(listening);
  }

  private static AttackPathNodeDTO asset(String criticality) {
    AttackPathNodeDTO node = new AttackPathNodeDTO();
    node.setType("ASSET");
    node.setCriticality(criticality);
    return node;
  }

  private void graph(AttackPathNodeDTO... nodes) {
    AttackPathDTO graph =
        new AttackPathDTO(List.of(), List.of(), List.of(nodes), List.of(), null, "full", 0);
    when(attackPathGraphService.buildGraph("sim-1")).thenReturn(graph);
  }

  @Test
  @DisplayName("default behaviour: with no trigger listening, nothing is read and nothing fires")
  void noTriggerReadsNothing() {
    listening(false);

    handler.handle(SimulationNotificationEventHandler.completedEvent(simulation));

    verifyNoInteractions(exerciseService, attackPathGraphService, tenantTx);
    verify(notificationEngineService, never())
        .handleEventWithMessage(any(), anyString(), anyString(), any(), anyString());
  }

  @Test
  @DisplayName("fires one event when the attack path reached a high or very high asset")
  void firesOnceOnCriticalAsset() {
    listening(true);
    graph(asset("VERY_HIGH"), asset("HIGH"), asset("MEDIUM"));

    handler.handle(SimulationNotificationEventHandler.completedEvent(simulation));

    verify(notificationEngineService, times(1))
        .handleEventWithMessage(
            eq(NotificationResourceCatalog.SIMULATION),
            eq("sim-1"),
            eq("tenant-1"),
            eq(NotificationTriggerEventType.CRITICAL_ASSET_REACHED),
            contains("/api/attack-path/simulations/sim-1/graph"));
  }

  @Test
  @DisplayName("the message names no asset and no count")
  void messageCarriesNoAssetNorCount() {
    String message =
        SimulationNotificationEventHandler.message(
            new SimulationNotificationEventHandler.Reached("sim-1", "tenant-1", "Sim"));

    assertThat(message)
        .isEqualTo(
            "[simulation] Sim: attack path reached a critical asset"
                + " (/api/attack-path/simulations/sim-1/graph)");
  }

  @Test
  @DisplayName("fires nothing when no critical asset was reached or the run has no attack path")
  void silentWithoutCriticalAsset() {
    listening(true);
    graph(asset("MEDIUM"), asset(null));
    handler.handle(SimulationNotificationEventHandler.completedEvent(simulation));

    when(attackPathGraphService.buildGraph("sim-1")).thenThrow(new IllegalStateException("none"));
    handler.handle(SimulationNotificationEventHandler.completedEvent(simulation));

    verify(notificationEngineService, never())
        .handleEventWithMessage(any(), anyString(), anyString(), any(), anyString());
  }

  @Test
  @DisplayName("ignores events without a tenant")
  void ignoresEventsWithoutTenant() {
    listening(true);
    handler.handle(
        NotificationEvent.builder()
            .eventType(NotificationEventType.SIMULATION_COMPLETED)
            .resourceType(ResourceType.SIMULATION)
            .resourceId("sim-1")
            .build());

    verifyNoInteractions(attackPathGraphService);
  }

  @Test
  @DisplayName("the completed event carries the simulation and its tenant")
  void completedEventCarriesTenant() {
    NotificationEvent event = SimulationNotificationEventHandler.completedEvent(simulation);

    assertThat(event.getEventType()).isEqualTo(NotificationEventType.SIMULATION_COMPLETED);
    assertThat(event.getResourceType()).isEqualTo(ResourceType.SIMULATION);
    assertThat(event.getResourceId()).isEqualTo("sim-1");
    assertThat(event.getTenantId()).isEqualTo("tenant-1");
  }
}
