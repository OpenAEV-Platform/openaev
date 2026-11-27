package io.openaev.notification.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Grant;
import io.openaev.database.model.NotificationTriggerEventType;
import io.openaev.database.model.NotificationTriggerType;
import io.openaev.database.model.NotifierType;
import io.openaev.database.model.ResourceType;
import io.openaev.service.notification.GroupAccessRevokedEvent;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

@DisplayName("Notification readers captured before a change")
class NotificationReadersCaptureListenerTest {

  private static final String TENANT = "tenant-a";

  private NotificationTriggerCacheService cacheService;
  private NotificationMatchingService matchingService;
  private NotificationRecipientFilter recipientFilter;
  private ApplicationEventPublisher eventPublisher;
  private NotificationReadersCaptureListener listener;

  @BeforeEach
  void setUp() {
    cacheService = mock(NotificationTriggerCacheService.class);
    matchingService = mock(NotificationMatchingService.class);
    recipientFilter = mock(NotificationRecipientFilter.class);
    eventPublisher = mock(ApplicationEventPublisher.class);
    TenantScopedTransaction tenantTx = mock(TenantScopedTransaction.class);
    doAnswer(invocation -> invocation.<Supplier<?>>getArgument(1).get())
        .when(tenantTx)
        .execute(any(TxCtx.class), any(Supplier.class));
    listener =
        new NotificationReadersCaptureListener(
            cacheService, matchingService, recipientFilter, tenantTx, eventPublisher);
  }

  private static ResolvedNotificationTrigger trigger(
      Set<NotificationTriggerEventType> eventTypes, List<String> recipients) {
    return new ResolvedNotificationTrigger(
        "trigger-id",
        "My trigger",
        NotificationTriggerType.LIVE,
        ResourceType.SCENARIO,
        eventTypes,
        null,
        null,
        null,
        null,
        List.of(),
        TENANT,
        recipients,
        List.of(new ResolvedNotifier("notifier-id", "UI", NotifierType.UI, Map.of())));
  }

  private static GroupAccessRevokedEvent revoked(Set<String> userIds) {
    return new GroupAccessRevokedEvent(
        TENANT,
        List.of(
            new GroupAccessRevokedEvent.Target(Grant.GRANT_RESOURCE_TYPE.SCENARIO, "scenario-id")),
        userIds);
  }

  @Test
  @DisplayName("A removed grant captures, among its members, those who read the scenario now")
  void capturesTheMembersWhoReadBeforeTheChange() {
    ResolvedNotificationTrigger trigger =
        trigger(Set.of(NotificationTriggerEventType.DELETE), List.of("user-1", "user-2"));
    when(cacheService.getLiveTriggers(ResourceType.SCENARIO)).thenReturn(List.of(trigger));
    when(matchingService.matches(eq(trigger), any(), anyString())).thenReturn(true);
    when(recipientFilter.readersOf(
            eq(trigger),
            eq(NotificationResourceCatalog.SCENARIO),
            eq("scenario-id"),
            anyCollection()))
        .thenReturn(List.of("user-1"));

    listener.onGroupAccessRevoked(revoked(Set.of("user-1")));

    ArgumentCaptor<NotificationReadersSnapshot> published =
        ArgumentCaptor.forClass(NotificationReadersSnapshot.class);
    verify(eventPublisher).publishEvent(published.capture());
    NotificationReadersSnapshot snapshot = published.getValue();
    assertEquals(NotificationReadersSnapshot.Kind.ACCESS_LOSS, snapshot.kind());
    assertEquals("scenario-id", snapshot.entityId());
    assertEquals(List.of("user-1"), snapshot.triggers().getFirst().readers());
  }

  @Test
  @DisplayName("Nobody who reads the scenario before the change: nothing is published")
  void nothingCapturedPublishesNothing() {
    ResolvedNotificationTrigger trigger =
        trigger(Set.of(NotificationTriggerEventType.DELETE), List.of("user-1"));
    when(cacheService.getLiveTriggers(ResourceType.SCENARIO)).thenReturn(List.of(trigger));
    when(matchingService.matches(eq(trigger), any(), anyString())).thenReturn(true);
    when(recipientFilter.readersOf(any(), any(), anyString(), anyCollection()))
        .thenReturn(List.of());

    listener.onGroupAccessRevoked(revoked(Set.of("user-1")));

    verify(eventPublisher, never()).publishEvent(any(Object.class));
  }

  @Test
  @DisplayName("A trigger that does not watch deletions captures nothing")
  void triggerWithoutDeleteIsIgnored() {
    ResolvedNotificationTrigger trigger =
        trigger(Set.of(NotificationTriggerEventType.UPDATE), List.of("user-1"));
    when(cacheService.getLiveTriggers(ResourceType.SCENARIO)).thenReturn(List.of(trigger));

    listener.onGroupAccessRevoked(revoked(Set.of("user-1")));

    verify(recipientFilter, never()).readersOf(any(), any(), anyString(), anyCollection());
    verify(eventPublisher, never()).publishEvent(any(Object.class));
  }

  @Test
  @DisplayName("Only scenarios and simulations are notification resources among grant targets")
  void catalogOfGrantTargets() {
    assertEquals(
        NotificationResourceCatalog.SCENARIO,
        NotificationReadersCaptureListener.catalogOf(Grant.GRANT_RESOURCE_TYPE.SCENARIO).get());
    assertEquals(
        NotificationResourceCatalog.SIMULATION,
        NotificationReadersCaptureListener.catalogOf(Grant.GRANT_RESOURCE_TYPE.SIMULATION).get());
    assertEquals(
        true,
        NotificationReadersCaptureListener.catalogOf(Grant.GRANT_RESOURCE_TYPE.ATOMIC_TESTING)
            .isEmpty());
  }
}
