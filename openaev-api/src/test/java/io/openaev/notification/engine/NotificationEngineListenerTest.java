package io.openaev.notification.engine;

import static io.openaev.database.audit.ModelBaseListener.DATA_PERSIST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.context.MarkingCtx;
import io.openaev.database.audit.BaseEvent;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.NotificationTriggerEventType;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.UserRepository;
import io.openaev.service.stream.StreamClearanceService;
import io.openaev.service.stream.StreamVisibility;
import io.openaev.service.stream.StreamVisibilityService;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@DisplayName("Notification engine listener")
class NotificationEngineListenerTest {

  private static final String TENANT_A = "tenant-a";
  private static final String ASSET_ID = "asset-id";

  private NotificationEngineService notificationEngineService;
  private StreamVisibilityService streamVisibilityService;
  private StreamClearanceService streamClearanceService;
  private NotificationEngineListener listener;

  @BeforeEach
  void setUp() {
    notificationEngineService = mock(NotificationEngineService.class);
    UserRepository userRepository = mock(UserRepository.class);
    streamVisibilityService = mock(StreamVisibilityService.class);
    streamClearanceService = mock(StreamClearanceService.class);
    listener =
        new NotificationEngineListener(
            notificationEngineService,
            userRepository,
            streamVisibilityService,
            streamClearanceService);
  }

  private BaseEvent assetPersistEvent() {
    Endpoint endpoint = new Endpoint();
    endpoint.setId(ASSET_ID);
    endpoint.setTenant(new Tenant(TENANT_A));
    BaseEvent event = mock(BaseEvent.class);
    when(event.getType()).thenReturn(DATA_PERSIST);
    when(event.getInstance()).thenReturn(endpoint);
    when(event.getSchema()).thenReturn("assets");
    return event;
  }

  @SuppressWarnings("unchecked")
  private Supplier<StreamVisibility> capturedVisibilitySupplier() {
    ArgumentCaptor<Supplier<StreamVisibility>> captor = ArgumentCaptor.forClass(Supplier.class);
    verify(notificationEngineService)
        .handleEvent(
            eq(NotificationResourceCatalog.ASSET),
            eq(ASSET_ID),
            eq(TENANT_A),
            eq(NotificationTriggerEventType.CREATE),
            anyString(),
            captor.capture());
    return captor.getValue();
  }

  @Test
  @DisplayName("A recognized entity event is forwarded to the engine with a visibility supplier")
  void forwardsEventWithVisibilitySupplier() {
    listener.onEntityEvent(assetPersistEvent());

    assertThat(capturedVisibilitySupplier()).isNotNull();
  }

  @Nested
  @DisplayName("The visibility supplier")
  class VisibilitySupplierBehavior {

    @Test
    @DisplayName("resolves to UNFILTERED without reading markings when not filtering")
    void unfilteredWhenNotFiltering() {
      when(streamClearanceService.isFiltering()).thenReturn(false);
      listener.onEntityEvent(assetPersistEvent());

      StreamVisibility visibility = capturedVisibilitySupplier().get();

      assertThat(visibility).isEqualTo(StreamVisibility.UNFILTERED);
      verify(streamVisibilityService, never()).visibilityOf(any(), anyString());
    }

    @Test
    @DisplayName("reads the event's markings when filtering")
    void readsMarkingsWhenFiltering() {
      when(streamClearanceService.isFiltering()).thenReturn(true);
      BaseEvent event = assetPersistEvent();
      StreamVisibility resolved = mock(StreamVisibility.class);
      when(streamVisibilityService.visibilityOf(event, TENANT_A)).thenReturn(resolved);

      listener.onEntityEvent(event);

      assertThat(capturedVisibilitySupplier().get()).isEqualTo(resolved);
    }

    @Test
    @DisplayName("fails closed to unresolved when reading the event's markings throws")
    void unresolvedWhenReadingMarkingsFails() {
      when(streamClearanceService.isFiltering()).thenReturn(true);
      BaseEvent event = assetPersistEvent();
      when(streamVisibilityService.visibilityOf(event, TENANT_A))
          .thenThrow(new RuntimeException("boom"));

      listener.onEntityEvent(event);

      // StreamVisibility has no equals(): assert the unresolved() shape (fail-closed) instead of
      // identity - visible only to a reader cleared for everything.
      StreamVisibility visibility = capturedVisibilitySupplier().get();
      assertThat(visibility.isUnfiltered()).isFalse();
      assertThat(visibility.visibleUnder(MarkingCtx.none())).isFalse();
      assertThat(visibility.visibleUnder(MarkingCtx.all())).isTrue();
    }
  }
}
