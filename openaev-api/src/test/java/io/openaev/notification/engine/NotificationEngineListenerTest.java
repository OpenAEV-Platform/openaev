package io.openaev.notification.engine;

import static io.openaev.database.audit.ModelBaseListener.DATA_DELETE;
import static io.openaev.database.audit.ModelBaseListener.DATA_PERSIST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.database.audit.BaseEvent;
import io.openaev.database.model.Base;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.NotificationTriggerEventType;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tag;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.UserRepository;
import io.openaev.service.stream.StreamVisibility;
import io.openaev.service.stream.StreamVisibilityService;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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

  private static BaseEvent event(String type, Base instance) {
    BaseEvent event = mock(BaseEvent.class);
    when(event.getType()).thenReturn(type);
    when(event.getInstance()).thenReturn(instance);
    return event;
  }

  private static BaseEvent scenarioRootDeletionEvent() {
    Scenario scenario = new Scenario();
    scenario.setId("scenario-id");
    BaseEvent event = event(DATA_DELETE, scenario);
    when(event.getPermissionResourceId()).thenReturn("scenario-id");
    when(event.getPermissionResourceType()).thenReturn(ResourceType.SCENARIO);
    return event;
  }

  static Stream<Arguments> eventsThatCannotNotify() {
    return Stream.of(
        Arguments.of("an event type with no trigger", event("other", new Endpoint())),
        Arguments.of("an event with no entity", event(DATA_PERSIST, null)),
        Arguments.of("a grant-managed root deletion", scenarioRootDeletionEvent()),
        Arguments.of("an entity no trigger watches", event(DATA_PERSIST, new Tag())));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("eventsThatCannotNotify")
  @DisplayName("An event that cannot notify is kept out of the queue")
  void given_eventThatCannotNotify_should_notBeNotifiable(String label, BaseEvent event) {
    // Act & Assert
    assertThat(NotificationEngineListener.notifiable(event)).isFalse();
  }

  @Test
  @DisplayName("A change on a watched entity goes into the queue")
  void given_watchedEntityChange_should_beNotifiable() {
    // Act & Assert
    assertThat(NotificationEngineListener.notifiable(assetPersistEvent())).isTrue();
  }
}
