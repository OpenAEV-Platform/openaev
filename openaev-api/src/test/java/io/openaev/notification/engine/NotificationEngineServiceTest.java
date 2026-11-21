package io.openaev.notification.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.NotificationEventRecord;
import io.openaev.database.model.NotificationTriggerEventType;
import io.openaev.database.model.NotificationTriggerType;
import io.openaev.database.model.ResourceType;
import io.openaev.database.repository.NotificationEventRecordRepository;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

@DisplayName("Notification engine live stage")
class NotificationEngineServiceTest {

  private NotificationTriggerCacheService cacheService;
  private NotificationMatchingService matchingService;
  private NotificationDispatchService dispatchService;
  private NotificationRecipientFilter recipientFilter;
  private NotificationEventRecordRepository eventRecordRepository;
  private TenantScopedTransaction tenantTx;
  private NotificationEngineService engineService;

  private static final String TENANT_A = "tenant-a";
  private static final String TENANT_B = "tenant-b";

  @BeforeEach
  void setUp() {
    cacheService = mock(NotificationTriggerCacheService.class);
    matchingService = mock(NotificationMatchingService.class);
    dispatchService = mock(NotificationDispatchService.class);
    recipientFilter = mock(NotificationRecipientFilter.class);
    // Default: every recipient reads the object; the segregation tests below narrow it.
    when(recipientFilter.readersOf(any(), any(), anyString()))
        .thenAnswer(
            invocation ->
                invocation.<ResolvedNotificationTrigger>getArgument(0).recipientUserIds());
    eventRecordRepository = mock(NotificationEventRecordRepository.class);
    tenantTx = mock(TenantScopedTransaction.class);
    doAnswer(
            invocation -> {
              invocation.<Runnable>getArgument(1).run();
              return null;
            })
        .when(tenantTx)
        .execute(any(TxCtx.class), ArgumentMatchers.<Runnable>any());
    engineService =
        new NotificationEngineService(
            cacheService,
            matchingService,
            dispatchService,
            recipientFilter,
            eventRecordRepository,
            tenantTx);
  }

  private ResolvedNotificationTrigger liveTrigger(
      Set<NotificationTriggerEventType> eventTypes, String tenantId, List<String> userIds) {
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
        tenantId,
        userIds,
        List.of(
            new ResolvedNotifier(
                "notifier-id",
                "UI",
                io.openaev.database.model.NotifierType.UI,
                java.util.Map.of())));
  }

  @Test
  @DisplayName("A matching event records one outbox row per recipient and dispatches")
  void matchingEventRecordsAndDispatches() {
    ResolvedNotificationTrigger trigger =
        liveTrigger(
            Set.of(NotificationTriggerEventType.CREATE), TENANT_A, List.of("user-1", "user-2"));
    when(cacheService.getLiveTriggers(ResourceType.SCENARIO)).thenReturn(List.of(trigger));
    when(matchingService.matches(eq(trigger), any(), anyString())).thenReturn(true);

    engineService.handleEvent(
        NotificationResourceCatalog.SCENARIO,
        "scenario-id",
        TENANT_A,
        NotificationTriggerEventType.CREATE,
        "My scenario");

    ArgumentCaptor<List<NotificationEventRecord>> recordsCaptor = ArgumentCaptor.captor();
    verify(eventRecordRepository, times(1)).saveAll(recordsCaptor.capture());
    assertEquals(2, recordsCaptor.getValue().size());
    verify(tenantTx).execute(any(TxCtx.class), ArgumentMatchers.<Runnable>any());
    verify(dispatchService)
        .dispatch(
            eq(trigger),
            eq(NotificationTriggerType.LIVE),
            eq(List.of("user-1", "user-2")),
            anyList());
  }

  @Test
  @DisplayName("Events of another tenant never match a trigger (tenant isolation)")
  void crossTenantEventsAreSkipped() {
    ResolvedNotificationTrigger trigger =
        liveTrigger(Set.of(NotificationTriggerEventType.CREATE), TENANT_A, List.of("user-1"));
    when(cacheService.getLiveTriggers(ResourceType.SCENARIO)).thenReturn(List.of(trigger));

    engineService.handleEvent(
        NotificationResourceCatalog.SCENARIO,
        "scenario-id",
        TENANT_B,
        NotificationTriggerEventType.CREATE,
        "My scenario");

    verify(matchingService, never()).matches(any(), any(), anyString());
    verify(eventRecordRepository, never()).saveAll(anyCollection());
    verify(dispatchService, never()).dispatch(any(), any(), anyList(), anyList());
  }

  @Test
  @DisplayName("Events without a resolved tenant are dropped (fail-closed tenant isolation)")
  void nullTenantEventsAreSkipped() {
    ResolvedNotificationTrigger trigger =
        liveTrigger(Set.of(NotificationTriggerEventType.CREATE), TENANT_A, List.of("user-1"));
    when(cacheService.getLiveTriggers(ResourceType.SCENARIO)).thenReturn(List.of(trigger));

    engineService.handleEvent(
        NotificationResourceCatalog.SCENARIO,
        "scenario-id",
        null,
        NotificationTriggerEventType.CREATE,
        "My scenario");

    verify(matchingService, never()).matches(any(), any(), anyString());
    verify(eventRecordRepository, never()).saveAll(anyCollection());
    verify(dispatchService, never()).dispatch(any(), any(), anyList(), anyList());
  }

  @Test
  @DisplayName("Events of an unsubscribed type are skipped")
  void unsubscribedEventTypesAreSkipped() {
    ResolvedNotificationTrigger trigger =
        liveTrigger(Set.of(NotificationTriggerEventType.CREATE), TENANT_A, List.of("user-1"));
    when(cacheService.getLiveTriggers(ResourceType.SCENARIO)).thenReturn(List.of(trigger));

    engineService.handleEvent(
        NotificationResourceCatalog.SCENARIO,
        "scenario-id",
        TENANT_A,
        NotificationTriggerEventType.DELETE,
        "My scenario");

    verify(dispatchService, never()).dispatch(any(), any(), anyList(), anyList());
  }

  @Test
  @DisplayName("A recipient who cannot read the object is neither recorded nor notified")
  void recipientWithoutReadIsDropped() {
    ResolvedNotificationTrigger trigger =
        liveTrigger(
            Set.of(NotificationTriggerEventType.CREATE), TENANT_A, List.of("reader", "restricted"));
    when(cacheService.getLiveTriggers(ResourceType.SCENARIO)).thenReturn(List.of(trigger));
    when(matchingService.matches(eq(trigger), any(), anyString())).thenReturn(true);
    when(recipientFilter.readersOf(
            eq(trigger), eq(NotificationResourceCatalog.SCENARIO), eq("scenario-id")))
        .thenReturn(List.of("reader"));

    engineService.handleEvent(
        NotificationResourceCatalog.SCENARIO,
        "scenario-id",
        TENANT_A,
        NotificationTriggerEventType.CREATE,
        "Secret scenario");

    ArgumentCaptor<List<NotificationEventRecord>> recordsCaptor = ArgumentCaptor.captor();
    verify(eventRecordRepository).saveAll(recordsCaptor.capture());
    assertEquals(1, recordsCaptor.getValue().size());
    assertEquals("reader", recordsCaptor.getValue().getFirst().getUser().getId());
    verify(dispatchService)
        .dispatch(eq(trigger), eq(NotificationTriggerType.LIVE), eq(List.of("reader")), anyList());
  }

  @Test
  @DisplayName("No recipient can read the object: nothing is recorded, nothing is sent")
  void noReaderSendsNothing() {
    ResolvedNotificationTrigger trigger =
        liveTrigger(Set.of(NotificationTriggerEventType.CREATE), TENANT_A, List.of("restricted"));
    when(cacheService.getLiveTriggers(ResourceType.SCENARIO)).thenReturn(List.of(trigger));
    when(matchingService.matches(eq(trigger), any(), anyString())).thenReturn(true);
    when(recipientFilter.readersOf(any(), any(), anyString())).thenReturn(List.of());

    engineService.handleEvent(
        NotificationResourceCatalog.SCENARIO,
        "scenario-id",
        TENANT_A,
        NotificationTriggerEventType.CREATE,
        "Secret scenario");

    verify(eventRecordRepository, never()).saveAll(anyCollection());
    verify(dispatchService, never()).dispatch(any(), any(), anyList(), anyList());
  }

  @Test
  @DisplayName("Non-matching filters do not dispatch")
  void nonMatchingFiltersAreSkipped() {
    ResolvedNotificationTrigger trigger =
        liveTrigger(Set.of(NotificationTriggerEventType.CREATE), TENANT_A, List.of("user-1"));
    when(cacheService.getLiveTriggers(ResourceType.SCENARIO)).thenReturn(List.of(trigger));
    when(matchingService.matches(eq(trigger), any(), anyString())).thenReturn(false);

    engineService.handleEvent(
        NotificationResourceCatalog.SCENARIO,
        "scenario-id",
        TENANT_A,
        NotificationTriggerEventType.CREATE,
        "My scenario");

    verify(eventRecordRepository, never()).saveAll(anyCollection());
    verify(dispatchService, never()).dispatch(any(), any(), anyList(), anyList());
  }
}
