package io.openaev.notification.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
import io.openaev.service.exposure.ExposureResult;
import io.openaev.service.exposure.ExposureResult.ReachedAsset;
import io.openaev.service.exposure.ExposureResult.Summary;
import io.openaev.service.exposure.ExposureResultService;
import java.time.Instant;
import java.util.List;
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
  @Mock private ExposureResultService exposureResultService;
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
    when(exerciseService.exercise("sim-1")).thenReturn(simulation);
  }

  private static ExposureResult result(List<ReachedAsset> reached) {
    int critical = (int) reached.stream().filter(ReachedAsset::critical).count();
    return new ExposureResult(
        ExposureResult.SCHEMA_VERSION,
        "sim-1",
        "Sim",
        null,
        Instant.now(),
        new Summary(3, 1, 0, 0, 2, 0, critical),
        List.of(),
        reached);
  }

  @Test
  @DisplayName("fires one ready event and one event per critical asset reached")
  void firesReadyAndCriticalEvents() {
    when(exposureResultService.compute(simulation))
        .thenReturn(
            result(
                List.of(
                    new ReachedAsset("db", "tenant-db", "VERY_HIGH", true),
                    new ReachedAsset("web", "web", "HIGH", true),
                    new ReachedAsset("dev", "dev", "MEDIUM", false))));

    handler.handle(SimulationNotificationEventHandler.finishedEvent(simulation));

    verify(notificationEngineService)
        .handleEventWithMessage(
            eq(NotificationResourceCatalog.SIMULATION),
            eq("sim-1"),
            eq("tenant-1"),
            eq(NotificationTriggerEventType.EXPOSURE_RESULT_READY),
            contains("/api/exercises/sim-1/exposure-result"));
    verify(notificationEngineService, times(2))
        .handleEventWithMessage(
            eq(NotificationResourceCatalog.SIMULATION),
            eq("sim-1"),
            eq("tenant-1"),
            eq(NotificationTriggerEventType.CRITICAL_ASSET_REACHED),
            anyString());
  }

  @Test
  @DisplayName("ignores other event types and events without a tenant")
  void ignoresOtherEvents() {
    handler.handle(
        NotificationEvent.builder()
            .eventType(NotificationEventType.SIMULATION_COMPLETED)
            .resourceType(ResourceType.SIMULATION)
            .resourceId("sim-1")
            .tenantId("tenant-1")
            .build());
    handler.handle(
        NotificationEvent.builder()
            .eventType(NotificationEventType.SIMULATION_FINISHED)
            .resourceType(ResourceType.SIMULATION)
            .resourceId("sim-1")
            .build());

    verify(notificationEngineService, never())
        .handleEventWithMessage(any(), anyString(), anyString(), any(), anyString());
  }

  @Test
  @DisplayName("the finished event carries the simulation and its tenant")
  void finishedEventCarriesTenant() {
    NotificationEvent event = SimulationNotificationEventHandler.finishedEvent(simulation);

    assertThat(event.getEventType()).isEqualTo(NotificationEventType.SIMULATION_FINISHED);
    assertThat(event.getResourceType()).isEqualTo(ResourceType.SIMULATION);
    assertThat(event.getResourceId()).isEqualTo("sim-1");
    assertThat(event.getTenantId()).isEqualTo("tenant-1");
  }
}
