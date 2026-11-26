package io.openaev.notification.engine;

import static io.openaev.database.audit.ModelBaseListener.DATA_PERSIST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.database.audit.BaseEvent;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.NotificationTriggerEventType;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.UserRepository;
import io.openaev.service.stream.StreamVisibility;
import io.openaev.service.stream.StreamVisibilityService;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@DisplayName("Notification engine listener")
class NotificationEngineListenerTest {

  private static final String TENANT_A = "tenant-a";
  private static final String ASSET_ID = "asset-id";

  private NotificationEngineService notificationEngineService;
  private StreamVisibilityService streamVisibilityService;
  private NotificationEngineListener listener;

  @BeforeEach
  void setUp() {
    notificationEngineService = mock(NotificationEngineService.class);
    UserRepository userRepository = mock(UserRepository.class);
    streamVisibilityService = mock(StreamVisibilityService.class);
    listener =
        new NotificationEngineListener(
            notificationEngineService, userRepository, streamVisibilityService);
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

  @Test
  @DisplayName(
      "The visibility supplier delegates to StreamVisibilityService.visibilityOrUnresolved - the"
          + " gating and fail-closed fallback are tested there (StreamVisibilityServiceTest)")
  void visibilitySupplierDelegatesToStreamVisibilityService() {
    BaseEvent event = assetPersistEvent();
    StreamVisibility resolved = mock(StreamVisibility.class);
    when(streamVisibilityService.visibilityOrUnresolved(event, TENANT_A)).thenReturn(resolved);

    listener.onEntityEvent(event);

    assertThat(capturedVisibilitySupplier().get()).isEqualTo(resolved);
  }
}
