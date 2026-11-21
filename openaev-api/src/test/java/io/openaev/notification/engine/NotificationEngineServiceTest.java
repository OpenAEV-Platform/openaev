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

import io.openaev.config.cache.MarkingClearanceCacheManager;
import io.openaev.context.MarkingCtx;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.NotificationEventRecord;
import io.openaev.database.model.NotificationTriggerEventType;
import io.openaev.database.model.NotificationTriggerType;
import io.openaev.database.model.ResourceType;
import io.openaev.database.repository.NotificationEventRecordRepository;
import io.openaev.service.stream.StreamVisibility;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
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
  private NotificationEventRecordRepository eventRecordRepository;
  private TenantScopedTransaction tenantTx;
  private MarkingClearanceCacheManager markingClearanceCacheManager;
  private NotificationEngineService engineService;

  private static final String TENANT_A = "tenant-a";
  private static final String TENANT_B = "tenant-b";

  /** A supplier that fails the test if resolved: for events that must never reach it. */
  private static final java.util.function.Supplier<StreamVisibility> NEVER_RESOLVED =
      () -> {
        throw new AssertionError("visibility must not be resolved for this event");
      };

  @BeforeEach
  void setUp() {
    cacheService = mock(NotificationTriggerCacheService.class);
    matchingService = mock(NotificationMatchingService.class);
    dispatchService = mock(NotificationDispatchService.class);
    eventRecordRepository = mock(NotificationEventRecordRepository.class);
    tenantTx = mock(TenantScopedTransaction.class);
    doAnswer(
            invocation -> {
              invocation.<Runnable>getArgument(1).run();
              return null;
            })
        .when(tenantTx)
        .execute(any(TxCtx.class), ArgumentMatchers.<Runnable>any());
    markingClearanceCacheManager = mock(MarkingClearanceCacheManager.class);
    engineService =
        new NotificationEngineService(
            cacheService,
            matchingService,
            dispatchService,
            eventRecordRepository,
            tenantTx,
            markingClearanceCacheManager);
  }

  private ResolvedNotificationTrigger liveTrigger(
      String id,
      Set<NotificationTriggerEventType> eventTypes,
      String tenantId,
      List<String> userIds) {
    return new ResolvedNotificationTrigger(
        id,
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

  private ResolvedNotificationTrigger liveTrigger(
      Set<NotificationTriggerEventType> eventTypes, String tenantId, List<String> userIds) {
    return liveTrigger("trigger-id", eventTypes, tenantId, userIds);
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
        "My scenario",
        () -> StreamVisibility.UNFILTERED);

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
  @DisplayName("No live trigger for the resource type never resolves the event's visibility")
  void noLiveTriggerNeverResolvesVisibility() {
    when(cacheService.getLiveTriggers(ResourceType.SCENARIO)).thenReturn(List.of());

    engineService.handleEvent(
        NotificationResourceCatalog.SCENARIO,
        "scenario-id",
        TENANT_A,
        NotificationTriggerEventType.CREATE,
        "My scenario",
        NEVER_RESOLVED);

    verify(dispatchService, never()).dispatch(any(), any(), anyList(), anyList());
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
        "My scenario",
        NEVER_RESOLVED);

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
        "My scenario",
        NEVER_RESOLVED);

    verify(matchingService, never()).matches(any(), any(), anyString());
    verify(eventRecordRepository, never()).saveAll(anyCollection());
    verify(dispatchService, never()).dispatch(any(), any(), anyList(), anyList());
  }

  @Test
  @DisplayName("Events of an unsubscribed type are skipped without resolving visibility")
  void unsubscribedEventTypesAreSkipped() {
    ResolvedNotificationTrigger trigger =
        liveTrigger(Set.of(NotificationTriggerEventType.CREATE), TENANT_A, List.of("user-1"));
    when(cacheService.getLiveTriggers(ResourceType.SCENARIO)).thenReturn(List.of(trigger));

    engineService.handleEvent(
        NotificationResourceCatalog.SCENARIO,
        "scenario-id",
        TENANT_A,
        NotificationTriggerEventType.DELETE,
        "My scenario",
        NEVER_RESOLVED);

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
        "My scenario",
        () -> StreamVisibility.UNFILTERED);

    verify(eventRecordRepository, never()).saveAll(anyCollection());
    verify(dispatchService, never()).dispatch(any(), any(), anyList(), anyList());
  }

  @Test
  @DisplayName("Live dispatch is narrowed to recipients the event's visibility shows them")
  void dispatchIsNarrowedByMarkingClearance() {
    ResolvedNotificationTrigger trigger =
        liveTrigger(
            Set.of(NotificationTriggerEventType.CREATE), TENANT_A, List.of("user-1", "user-2"));
    when(cacheService.getLiveTriggers(ResourceType.ASSET)).thenReturn(List.of(trigger));
    when(matchingService.matches(eq(trigger), any(), anyString())).thenReturn(true);
    MarkingCtx clearance1 = MarkingCtx.forMarkings(List.of("tlp-red"));
    MarkingCtx clearance2 = MarkingCtx.none();
    when(markingClearanceCacheManager.findClearance("user-1", TENANT_A)).thenReturn(clearance1);
    when(markingClearanceCacheManager.findClearance("user-2", TENANT_A)).thenReturn(clearance2);
    StreamVisibility visibility = mock(StreamVisibility.class);
    when(visibility.isUnfiltered()).thenReturn(false);
    when(visibility.visibleUnder(clearance1)).thenReturn(true);
    when(visibility.visibleUnder(clearance2)).thenReturn(false);

    engineService.handleEvent(
        NotificationResourceCatalog.ASSET,
        "asset-id",
        TENANT_A,
        NotificationTriggerEventType.CREATE,
        "My asset",
        () -> visibility);

    verify(dispatchService)
        .dispatch(eq(trigger), eq(NotificationTriggerType.LIVE), eq(List.of("user-1")), anyList());
  }

  @Test
  @DisplayName("The outbox only records the recipients cleared for the event, not the full list")
  void outboxOnlyRecordsClearedRecipients() {
    ResolvedNotificationTrigger trigger =
        liveTrigger(
            Set.of(NotificationTriggerEventType.CREATE), TENANT_A, List.of("user-1", "user-2"));
    when(cacheService.getLiveTriggers(ResourceType.ASSET)).thenReturn(List.of(trigger));
    when(matchingService.matches(eq(trigger), any(), anyString())).thenReturn(true);
    MarkingCtx clearance1 = MarkingCtx.forMarkings(List.of("tlp-red"));
    MarkingCtx clearance2 = MarkingCtx.none();
    when(markingClearanceCacheManager.findClearance("user-1", TENANT_A)).thenReturn(clearance1);
    when(markingClearanceCacheManager.findClearance("user-2", TENANT_A)).thenReturn(clearance2);
    StreamVisibility visibility = mock(StreamVisibility.class);
    when(visibility.isUnfiltered()).thenReturn(false);
    when(visibility.visibleUnder(clearance1)).thenReturn(true);
    when(visibility.visibleUnder(clearance2)).thenReturn(false);

    engineService.handleEvent(
        NotificationResourceCatalog.ASSET,
        "asset-id",
        TENANT_A,
        NotificationTriggerEventType.CREATE,
        "My asset",
        () -> visibility);

    ArgumentCaptor<List<NotificationEventRecord>> recordsCaptor = ArgumentCaptor.captor();
    verify(eventRecordRepository).saveAll(recordsCaptor.capture());
    assertEquals(1, recordsCaptor.getValue().size());
    assertEquals("user-1", recordsCaptor.getValue().getFirst().getUser().getId());
  }

  @Test
  @DisplayName("Dispatch stays unfiltered when the event's visibility is unfiltered")
  void dispatchIsUnfilteredWhenVisibilityIsUnfiltered() {
    ResolvedNotificationTrigger trigger =
        liveTrigger(
            Set.of(NotificationTriggerEventType.CREATE), TENANT_A, List.of("user-1", "user-2"));
    when(cacheService.getLiveTriggers(ResourceType.ASSET)).thenReturn(List.of(trigger));
    when(matchingService.matches(eq(trigger), any(), anyString())).thenReturn(true);

    engineService.handleEvent(
        NotificationResourceCatalog.ASSET,
        "asset-id",
        TENANT_A,
        NotificationTriggerEventType.CREATE,
        "My asset",
        () -> StreamVisibility.UNFILTERED);

    verify(dispatchService)
        .dispatch(
            eq(trigger),
            eq(NotificationTriggerType.LIVE),
            eq(List.of("user-1", "user-2")),
            anyList());
    verify(markingClearanceCacheManager, never()).findClearance(anyString(), anyString());
  }

  @Test
  @DisplayName("Visibility is resolved once per event, not once per matching trigger")
  void visibilityIsResolvedOnceAcrossMultipleTriggers() {
    ResolvedNotificationTrigger triggerOne =
        liveTrigger(
            "trigger-1", Set.of(NotificationTriggerEventType.CREATE), TENANT_A, List.of("user-1"));
    ResolvedNotificationTrigger triggerTwo =
        liveTrigger(
            "trigger-2", Set.of(NotificationTriggerEventType.CREATE), TENANT_A, List.of("user-2"));
    when(cacheService.getLiveTriggers(ResourceType.ASSET))
        .thenReturn(List.of(triggerOne, triggerTwo));
    when(matchingService.matches(any(), any(), anyString())).thenReturn(true);
    AtomicInteger resolutions = new AtomicInteger();
    java.util.function.Supplier<StreamVisibility> countingSupplier =
        () -> {
          resolutions.incrementAndGet();
          return StreamVisibility.UNFILTERED;
        };

    engineService.handleEvent(
        NotificationResourceCatalog.ASSET,
        "asset-id",
        TENANT_A,
        NotificationTriggerEventType.CREATE,
        "My asset",
        countingSupplier);

    assertEquals(1, resolutions.get());
    verify(dispatchService)
        .dispatch(
            eq(triggerOne), eq(NotificationTriggerType.LIVE), eq(List.of("user-1")), anyList());
    verify(dispatchService)
        .dispatch(
            eq(triggerTwo), eq(NotificationTriggerType.LIVE), eq(List.of("user-2")), anyList());
  }
}
